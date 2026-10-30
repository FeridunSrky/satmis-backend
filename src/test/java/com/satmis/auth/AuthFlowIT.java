package com.satmis.auth;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestJwtSecret;
import com.satmis.enums.Role;
import com.satmis.security.CustomUserDetails;
import com.satmis.security.JwtProperties;
import com.satmis.security.JwtService;
import com.satmis.user.User;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIT {

    private static final String PASSWORD = "Sifre1234";
    private static final String NEW_PASSWORD = "YeniSifre5678";
    private static final String PHONE = "+905321234567";
    private static final long ALREADY_EXPIRED_MS = -60_000;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void register_validRequest_returnsTokens() throws Exception {
        String email = uniqueEmail();

        register(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void register_duplicateEmail_returnsConflict() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        register(email.toUpperCase(Locale.ROOT)).andExpect(status().isConflict());
    }

    @Test
    void register_invalidBody_returnsFieldErrors() throws Exception {
        postJson("/api/auth/register", Map.of(
                "email", "gecersiz-eposta", "password", "kisa", "firstName", "Ali", "lastName", "Veli"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    void register_missingOrInvalidPhone_returns400WithPhoneError() throws Exception {
        postJson("/api/auth/register", Map.of(
                "email", uniqueEmail(), "password", PASSWORD, "firstName", "Ali", "lastName", "Veli"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").isNotEmpty());
        postJson("/api/auth/register", Map.of(
                "email", uniqueEmail(), "password", PASSWORD, "firstName", "Ali", "lastName", "Veli", "phone", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").isNotEmpty());
    }

    @Test
    void updateProfile_changePhone_returnsUpdatedPhone() throws Exception {
        String accessToken = field(register(uniqueEmail()), "$.accessToken");

        updateProfile(accessToken, Map.of("firstName", "Ali", "lastName", "Veli", "phone", "05441112233"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("05441112233"));
    }

    @Test
    void updateProfile_blankOrMissingPhone_returns400AndKeepsPhone() throws Exception {
        String accessToken = field(register(uniqueEmail()), "$.accessToken");

        updateProfile(accessToken, Map.of("firstName", "Ali", "lastName", "Veli", "phone", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").isNotEmpty());
        updateProfile(accessToken, Map.of("firstName", "Ali", "lastName", "Veli"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").isNotEmpty());
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(jsonPath("$.phone").value(PHONE));
    }

    @Test
    void publicEndpoint_expiredOrInvalidToken_returns401SoClientRefreshes() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        mockMvc.perform(get("/api/listings").header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken(email))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/catalog/brands").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/listings")).andExpect(status().isOk());
    }

    @Test
    void authEndpoints_staleTokenHeader_stillWork() throws Exception {
        String email = uniqueEmail();
        String refreshToken = field(register(email), "$.refreshToken");
        String staleHeader = bearer(expiredAccessToken(email));

        mockMvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, staleHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.AUTHORIZATION, staleHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());
    }

    @Test
    void login_wrongPasswordThenCorrect_returns401Then200() throws Exception {
        String email = uniqueEmail();
        register(email);

        login(email, "YanlisSifre99").andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void me_withAndWithoutToken_requiresAuthentication() throws Exception {
        String email = uniqueEmail();
        String accessToken = field(register(email), "$.accessToken");

        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void refresh_rotatedTokenReplayedAfterGrace_revokesWholeSession() throws Exception {
        String firstRefresh = field(register(uniqueEmail()), "$.refreshToken");

        ResultActions rotated = refresh(firstRefresh).andExpect(status().isOk());
        String secondRefresh = field(rotated, "$.refreshToken");
        jdbcTemplate.update("update refresh_tokens set rotated_at = ? where token_hash = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))), RefreshTokenService.hash(firstRefresh));
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        refresh(firstRefresh).andExpect(status().isUnauthorized());
        refresh(secondRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_thenRefresh_returns401() throws Exception {
        String refreshToken = field(register(uniqueEmail()), "$.refreshToken");

        postJson("/api/auth/logout", Map.of("refreshToken", refreshToken)).andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_valid_revokesTokensAndSwapsPassword() throws Exception {
        String email = uniqueEmail();
        ResultActions registered = register(email);
        String accessToken = field(registered, "$.accessToken");
        String refreshToken = field(registered, "$.refreshToken");

        mockMvc.perform(put("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_userRole_returns403() throws Exception {
        String accessToken = field(register(uniqueEmail()), "$.accessToken");

        mockMvc.perform(get("/api/admin/anything").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshToken_storedInDatabase_isHashedNotRaw() throws Exception {
        String email = uniqueEmail();
        String refreshToken = field(register(email), "$.refreshToken");

        List<String> storedHashes = jdbcTemplate.queryForList(
                "select rt.token_hash from refresh_tokens rt join users u on u.id = rt.user_id where u.email = ?",
                String.class, email);

        assertThat(storedHashes).hasSize(1).doesNotContain(refreshToken);
        assertThat(storedHashes.getFirst()).isEqualTo(RefreshTokenService.hash(refreshToken));
    }

    private ResultActions register(String email) throws Exception {
        return postJson("/api/auth/register", Map.of(
                "email", email, "password", PASSWORD, "firstName", "Ali", "lastName", "Veli", "phone", PHONE));
    }

    private ResultActions updateProfile(String accessToken, Map<String, String> body) throws Exception {
        return mockMvc.perform(put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private String expiredAccessToken(String email) {
        JwtService expiringJwtService = new JwtService(new JwtProperties(
                jwtProperties.secret(), ALREADY_EXPIRED_MS, jwtProperties.refreshExpirationMs(),
                jwtProperties.refreshReuseGrace()));
        User user = new User();
        user.setEmail(email);
        user.setRole(Role.USER);
        return expiringJwtService.generateAccessToken(new CustomUserDetails(user));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    private ResultActions postJson(String url, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private String json(Map<String, String> body) {
        return jsonMapper.writeValueAsString(body);
    }

    private static String field(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@satmis.com";
    }
}
