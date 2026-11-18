package com.satmis.security;

import com.satmis.TestJwtSecret;
import com.satmis.enums.Role;
import com.satmis.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final long ACCESS_EXPIRATION_MS = 60_000;
    private static final long REFRESH_EXPIRATION_MS = 120_000;

    @Test
    void constructor_unresolvedPlaceholder_failsWithActionableMessage() {
        assertThatThrownBy(() -> new JwtService(properties("${JWT_SECRET}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("openssl rand -base64 32");
    }

    @Test
    void constructor_secretShorterThan32Bytes_failsWithoutLeakingSecret() {
        String shortSecret = "c2hvcnQ=";

        assertThatThrownBy(() -> new JwtService(properties(shortSecret)))
                .isInstanceOf(IllegalStateException.class)
                .message().doesNotContain(shortSecret);
    }

    @Test
    void generateAccessToken_validSecret_carriesUsername() {
        JwtService jwtService = new JwtService(properties(TestJwtSecret.random()));

        String token = jwtService.generateAccessToken(new CustomUserDetails(user("ayse@satmis.com")));

        assertThat(jwtService.extractUsername(token)).isEqualTo("ayse@satmis.com");
    }

    @Test
    void extractUsername_tokenSignedWithAnotherSecret_throwsJwtException() {
        CustomUserDetails userDetails = new CustomUserDetails(user("ayse@satmis.com"));
        String foreignToken = new JwtService(properties(TestJwtSecret.random())).generateAccessToken(userDetails);
        JwtService jwtService = new JwtService(properties(TestJwtSecret.random()));

        assertThatThrownBy(() -> jwtService.extractUsername(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void extractUsername_expiredToken_throwsJwtException() {
        String secret = TestJwtSecret.random();
        // A negative lifetime puts the expiry a minute in the past, so the test does not depend on timing
        JwtService expiringService = new JwtService(
                new JwtProperties(secret, -60_000, REFRESH_EXPIRATION_MS, Duration.ofSeconds(30)));
        String token = expiringService.generateAccessToken(new CustomUserDetails(user("ayse@satmis.com")));

        assertThatThrownBy(() -> new JwtService(properties(secret)).extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS, Duration.ofSeconds(30));
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setRole(Role.USER);
        return user;
    }
}
