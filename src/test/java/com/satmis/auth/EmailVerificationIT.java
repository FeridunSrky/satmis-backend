package com.satmis.auth;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Mails really go over SMTP to Mailpit and are read back through its HTTP API, like a user opening the inbox.
@SpringBootTest(properties = "app.mail.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class EmailVerificationIT {

    private static final int SMTP_PORT = 1025;
    private static final int MAILPIT_API_PORT = 8025;
    private static final Pattern TOKEN_IN_LINK = Pattern.compile(
            Pattern.quote("http://localhost:5173/verify-email?token=") + "([A-Za-z0-9_-]+)");
    private static final Duration MAIL_WAIT = Duration.ofSeconds(10);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
            .withExposedPorts(SMTP_PORT, MAILPIT_API_PORT);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(SMTP_PORT));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void register_newUser_isUnverifiedAndLinkInMailVerifiesIt() throws Exception {
        String email = uniqueEmail();
        String accessToken = register(email);
        currentUser(accessToken).andExpect(jsonPath("$.emailVerified").value(false));

        List<String> tokens = tokensMailedTo(email, 1);
        verify(tokens.getFirst()).andExpect(status().isNoContent());

        currentUser(accessToken).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void verify_sameLinkTwice_secondTimeReturns400() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = tokensMailedTo(email, 1).getFirst();

        verify(token).andExpect(status().isNoContent());
        verify(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Doğrulama linki geçersiz veya süresi dolmuş, yeni bir link isteyin"));
    }

    @Test
    void verify_expiredLink_returns400AndKeepsUserUnverified() throws Exception {
        String email = uniqueEmail();
        String accessToken = register(email);
        String token = tokensMailedTo(email, 1).getFirst();
        jdbcTemplate.update("update email_verification_tokens set expiry_date = ? where token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), RefreshTokenService.hash(token));

        verify(token).andExpect(status().isBadRequest());
        currentUser(accessToken).andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void verify_unknownOrBlankToken_returns400() throws Exception {
        verify("not-a-real-token").andExpect(status().isBadRequest());
        verify("").andExpect(status().isBadRequest());
    }

    @Test
    void resend_unverifiedUser_mailsNewLinkAndOldLinkStopsWorking() throws Exception {
        String email = uniqueEmail();
        String accessToken = register(email);
        String firstToken = tokensMailedTo(email, 1).getFirst();

        resend(accessToken).andExpect(status().isNoContent());
        List<String> tokens = tokensMailedTo(email, 2);
        String newestToken = tokens.stream().filter(token -> !token.equals(firstToken)).findFirst().orElseThrow();

        verify(firstToken).andExpect(status().isBadRequest());
        verify(newestToken).andExpect(status().isNoContent());
    }

    @Test
    void resend_alreadyVerifiedUser_returns409() throws Exception {
        String email = uniqueEmail();
        String accessToken = register(email);
        verify(tokensMailedTo(email, 1).getFirst()).andExpect(status().isNoContent());

        resend(accessToken).andExpect(status().isConflict());
    }

    @Test
    void resend_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/users/me/email-verification")).andExpect(status().isUnauthorized());
    }

    @Test
    void verificationToken_storedInDatabase_isHashedNotRaw() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = tokensMailedTo(email, 1).getFirst();

        List<String> storedHashes = jdbcTemplate.queryForList("""
                select t.token_hash from email_verification_tokens t join users u on u.id = t.user_id
                where u.email = ?""", String.class, email);
        assertThat(storedHashes).containsExactly(RefreshTokenService.hash(token));
    }

    private String register(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "Sifre1234", "firstName": "Ali", "lastName": "Veli",
                 "phone": "+905321234567"}""".formatted(email);
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    private ResultActions resend(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/users/me/email-verification")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions currentUser(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    // Mailpit stores mails asynchronously after SMTP accepts them, so poll briefly
    private static List<String> tokensMailedTo(String email, int expectedMails) throws Exception {
        Instant deadline = Instant.now().plus(MAIL_WAIT);
        while (true) {
            List<String> ids = JsonPath.read(mailpitGet("/api/v1/search?query="
                    + URLEncoder.encode("to:\"" + email + "\"", StandardCharsets.UTF_8)), "$.messages[*].ID");
            if (ids.size() >= expectedMails) {
                return ids.stream().map(EmailVerificationIT::tokenInMessage).toList();
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Expected " + expectedMails + " mails to " + email + ", found " + ids.size());
            }
            Thread.sleep(100);
        }
    }

    private static String tokenInMessage(String messageId) {
        try {
            String text = JsonPath.read(mailpitGet("/api/v1/message/" + messageId), "$.Text");
            Matcher matcher = TOKEN_IN_LINK.matcher(text);
            assertThat(matcher.find()).as("verification link in mail body").isTrue();
            return matcher.group(1);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read mail " + messageId + " from Mailpit", ex);
        }
    }

    private static String mailpitGet(String path) throws Exception {
        URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(MAILPIT_API_PORT) + path);
        return HTTP.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String uniqueEmail() {
        return "verify-" + UUID.randomUUID() + "@satmis.com";
    }
}
