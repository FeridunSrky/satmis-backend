package com.satmis.auth;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class RefreshTokenCleanupIT {

    private static final String COUNT_TOKENS_OF_USER = "select count(*) from refresh_tokens where user_id = ?";

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
    private RefreshTokenCleanupJob refreshTokenCleanupJob;

    @Test
    void deleteExpiredTokens_mixedTokens_removesOnlyExpiredOnes() throws Exception {
        String email = "cleanup-" + UUID.randomUUID() + "@satmis.com";
        String liveRefreshToken = registerAndGetRefreshToken(email);
        Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
        Instant now = Instant.now();
        insertToken(userId, now.minus(Duration.ofDays(1)), false);
        insertToken(userId, now.minus(Duration.ofDays(1)), true);
        String revokedButLiveHash = insertToken(userId, now.plus(Duration.ofDays(1)), true);

        refreshTokenCleanupJob.deleteExpiredTokens();

        assertThat(jdbcTemplate.queryForObject(COUNT_TOKENS_OF_USER, Long.class, userId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_tokens where token_hash = ?",
                Long.class, revokedButLiveHash)).isEqualTo(1);
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("refreshToken", liveRefreshToken))))
                .andExpect(status().isOk());
    }

    private String registerAndGetRefreshToken(String email) throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of("email", email, "password", "Sifre1234",
                "firstName", "Ali", "lastName", "Veli", "phone", "+905321234567"));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.refreshToken");
    }

    private String insertToken(Long userId, Instant expiryDate, boolean revoked) {
        String tokenHash = RefreshTokenService.hash(UUID.randomUUID().toString());
        jdbcTemplate.update("insert into refresh_tokens (user_id, token_hash, expiry_date, revoked) values (?, ?, ?, ?)",
                userId, tokenHash, Timestamp.from(expiryDate), revoked);
        return tokenHash;
    }
}
