package com.satmis.auth;

import com.jayway.jsonpath.JsonPath;
import com.satmis.MailpitInbox;
import com.satmis.RefreshCookies;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.mail.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetIT {

    private static final String LINK_BASE_URL = "http://localhost:5173/reset-password";
    private static final String OLD_PASSWORD = "EskiSifre123";
    private static final String NEW_PASSWORD = "YeniSifre456";
    private static final String INVALID_LINK_MESSAGE = "Şifre sıfırlama linki geçersiz veya süresi dolmuş, yeni bir link isteyin";
    private static final long NO_MAIL_WAIT_MS = 1_500;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Container
    static GenericContainer<?> mailpit = MailpitInbox.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(MailpitInbox.SMTP_PORT));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resetPassword_viaMailedLink_changesPasswordAndEndsAllSessions() throws Exception {
        String email = uniqueEmail();
        String oldRefreshToken = register(email);

        forgotPassword(email).andExpect(status().isNoContent());
        resetPassword(resetTokenMailedTo(email, 1), NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());
        String accessToken = JsonPath.read(login(email, NEW_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        refresh(oldRefreshToken).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void forgotPassword_unknownEmail_returns204AndSendsNothing() throws Exception {
        String email = uniqueEmail();

        forgotPassword(email).andExpect(status().isNoContent());

        Thread.sleep(NO_MAIL_WAIT_MS);
        assertThat(MailpitInbox.messageIds(mailpit, email)).isEmpty();
    }

    @Test
    void forgotPassword_emailInDifferentCase_findsTheAccount() throws Exception {
        String email = uniqueEmail();
        register(email);

        forgotPassword(email.toUpperCase(Locale.ROOT)).andExpect(status().isNoContent());

        assertThat(resetTokenMailedTo(email, 1)).isNotBlank();
    }

    @Test
    void resetPassword_sameLinkTwice_secondTimeReturns400() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgotPassword(email);
        String token = resetTokenMailedTo(email, 1);

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
        resetPassword(token, "BaskaSifre789").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_LINK_MESSAGE));
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetPassword_expiredLink_returns400AndKeepsOldPassword() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgotPassword(email);
        String token = resetTokenMailedTo(email, 1);
        jdbcTemplate.update("update password_reset_tokens set expiry_date = ? where token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), RefreshTokenService.hash(token));

        resetPassword(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(email, OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void forgotPassword_requestedTwice_onlyNewestLinkWorks() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgotPassword(email);
        String firstToken = resetTokenMailedTo(email, 1);
        forgotPassword(email);
        String newestToken = MailpitInbox.tokensMailedTo(mailpit, email, LINK_BASE_URL, 2).stream()
                .filter(token -> !token.equals(firstToken)).findFirst().orElseThrow();

        resetPassword(firstToken, NEW_PASSWORD).andExpect(status().isBadRequest());
        resetPassword(newestToken, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_tooShortPassword_returns400AndKeepsLinkUsable() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgotPassword(email);
        String token = resetTokenMailedTo(email, 1);

        resetPassword(token, "kisa").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_unknownToken_returns400() throws Exception {
        resetPassword("not-a-real-token", NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void resetToken_storedInDatabase_isHashedNotRaw() throws Exception {
        String email = uniqueEmail();
        register(email);
        forgotPassword(email);
        String token = resetTokenMailedTo(email, 1);

        List<String> storedHashes = jdbcTemplate.queryForList("""
                select t.token_hash from password_reset_tokens t join users u on u.id = t.user_id
                where u.email = ?""", String.class, email);
        assertThat(storedHashes).containsExactly(RefreshTokenService.hash(token));
    }

    private String register(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "%s", "firstName": "Ali", "lastName": "Veli",
                 "phone": "+905321234567"}""".formatted(email, OLD_PASSWORD);
        return RefreshCookies.from(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return postJson("/api/auth/forgot-password", "{\"email\": \"%s\"}".formatted(email));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return postJson("/api/auth/reset-password",
                "{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, newPassword));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(RefreshCookies.refresh(refreshToken));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // Registration also mails a verification link, so only reset-password links are picked here
    private static String resetTokenMailedTo(String email, int expectedResetMails) throws Exception {
        return MailpitInbox.tokensMailedTo(mailpit, email, LINK_BASE_URL, expectedResetMails).getFirst();
    }

    private static String uniqueEmail() {
        return "reset-" + UUID.randomUUID() + "@satmis.com";
    }
}
