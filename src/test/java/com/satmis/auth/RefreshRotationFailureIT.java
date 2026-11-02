package com.satmis.auth;

import com.satmis.RefreshCookies;
import com.satmis.TestJwtSecret;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The frontend retries a refresh that failed with a server error using the same cookie, so a failure after the
// rotation UPDATE must roll it back: no new cookie, and the old token still works
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class RefreshRotationFailureIT {

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

    @MockitoSpyBean
    private RefreshTokenService refreshTokenService;

    @Test
    void refresh_failureAfterRotation_returns500AndKeepsOldTokenValid() throws Exception {
        String oldToken = RefreshCookies.from(register());
        // Fails after the rotation UPDATE ran, inside the same transaction
        doThrow(new IllegalStateException("simulated failure")).when(refreshTokenService).create(any(User.class));

        mockMvc.perform(RefreshCookies.refresh(oldToken))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(jdbcTemplate.queryForObject(
                "select not revoked and rotated_at is null from refresh_tokens where token_hash = ?",
                Boolean.class, RefreshTokenService.hash(oldToken))).isTrue();

        reset(refreshTokenService);
        mockMvc.perform(RefreshCookies.refresh(oldToken))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    private MvcResult register() throws Exception {
        String body = """
                {"email": "%s", "password": "Sifre1234", "firstName": "Ayşe", "lastName": "Yılmaz",
                 "phone": "+905321234567"}""".formatted("user-" + UUID.randomUUID() + "@satmis.com");
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
