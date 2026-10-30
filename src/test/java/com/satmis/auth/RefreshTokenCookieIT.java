package com.satmis.auth;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestJwtSecret;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class RefreshTokenCookieIT {

    private static final String COOKIE_NAME = "satmis_refresh";
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final int SEVEN_DAYS_IN_SECONDS = 7 * 24 * 60 * 60;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void login_validCredentials_setsHttpOnlyRefreshCookieMatchingBody() throws Exception {
        String email = uniqueEmail();
        register(email);

        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"Sifre1234\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(COOKIE_NAME, true))
                .andExpect(cookie().secure(COOKIE_NAME, true))
                .andExpect(cookie().sameSite(COOKIE_NAME, "Strict"))
                .andExpect(cookie().path(COOKIE_NAME, "/api/auth"))
                .andExpect(cookie().maxAge(COOKIE_NAME, SEVEN_DAYS_IN_SECONDS))
                .andReturn();

        String bodyToken = JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
        assertThat(result.getResponse().getCookie(COOKIE_NAME).getValue()).isEqualTo(bodyToken);
    }

    @Test
    void register_validRequest_setsRefreshCookie() throws Exception {
        MvcResult result = register(uniqueEmail());

        assertThat(result.getResponse().getCookie(COOKIE_NAME)).isNotNull();
        assertThat(result.getResponse().getCookie(COOKIE_NAME).isHttpOnly()).isTrue();
    }

    @Test
    void refresh_cookieFromFrontendOrigin_rotatesTokenAndCookie() throws Exception {
        String firstToken = refreshCookieValue(register(uniqueEmail()));

        MvcResult result = refreshWithCookie(firstToken, FRONTEND_ORIGIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String secondToken = refreshCookieValue(result);
        assertThat(secondToken).isNotEqualTo(firstToken);
        refreshWithCookie(secondToken, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void refresh_cookieWithoutOrigin_returns403AndKeepsToken() throws Exception {
        String token = refreshCookieValue(register(uniqueEmail()));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE_NAME, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("İstek kaynağı doğrulanamadı"));

        refreshWithCookie(token, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void refresh_cookieFromForeignOrigin_returns403() throws Exception {
        String token = refreshCookieValue(register(uniqueEmail()));

        refreshWithCookie(token, "https://evil.example").andExpect(status().isForbidden());
    }

    @Test
    void refresh_noBodyAndNoCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token bulunamadı"));
    }

    @Test
    void refresh_tokenInBodyWithoutOrigin_stillWorksForApiClients() throws Exception {
        String token = refreshCookieValue(register(uniqueEmail()));

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(COOKIE_NAME));
    }

    @Test
    void refresh_sameTokenFromSecondTabWithinGrace_returnsRotatedCodeAndKeepsSession() throws Exception {
        String firstToken = refreshCookieValue(register(uniqueEmail()));
        String secondToken = refreshCookieValue(refreshWithCookie(firstToken, FRONTEND_ORIGIN)
                .andExpect(status().isOk()).andReturn());

        refreshWithCookie(firstToken, FRONTEND_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_ROTATED"));

        refreshWithCookie(secondToken, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void refresh_rotatedTokenAfterGrace_returns401WithoutCodeAndEndsSession() throws Exception {
        String firstToken = refreshCookieValue(register(uniqueEmail()));
        String secondToken = refreshCookieValue(refreshWithCookie(firstToken, FRONTEND_ORIGIN)
                .andExpect(status().isOk()).andReturn());
        jdbcTemplate.update("update refresh_tokens set rotated_at = ? where token_hash = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))), RefreshTokenService.hash(firstToken));

        refreshWithCookie(firstToken, FRONTEND_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());

        refreshWithCookie(secondToken, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_loggedOutTokenReplayed_isNotTreatedAsRotation() throws Exception {
        String token = refreshCookieValue(register(uniqueEmail()));
        logoutWithCookie(token).andExpect(status().isNoContent());

        refreshWithCookie(token, FRONTEND_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void logout_withCookie_revokesTokenAndClearsCookie() throws Exception {
        String token = refreshCookieValue(register(uniqueEmail()));

        logoutWithCookie(token)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE_NAME, 0))
                .andExpect(cookie().path(COOKIE_NAME, "/api/auth"));

        refreshWithCookie(token, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutAnyToken_returns204AndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));
    }

    @Test
    void preflight_fromFrontendOrigin_allowsCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    private MvcResult register(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "Sifre1234", "firstName": "Ali", "lastName": "Veli",
                 "phone": "+905321234567"}""".formatted(email);
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private ResultActions refreshWithCookie(String token, String origin) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(COOKIE_NAME, token))
                .header(HttpHeaders.ORIGIN, origin));
    }

    private ResultActions logoutWithCookie(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie(COOKIE_NAME, token))
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN));
    }

    private static String refreshCookieValue(MvcResult result) {
        return result.getResponse().getCookie(COOKIE_NAME).getValue();
    }

    private static String uniqueEmail() {
        return "cookie-" + UUID.randomUUID() + "@satmis.com";
    }
}
