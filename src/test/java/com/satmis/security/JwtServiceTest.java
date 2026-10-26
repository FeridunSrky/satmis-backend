package com.satmis.security;

import com.satmis.TestJwtSecret;
import com.satmis.enums.Role;
import com.satmis.user.User;
import org.junit.jupiter.api.Test;

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
    void generateAccessToken_validSecret_producesTokenValidForSameUser() {
        JwtService jwtService = new JwtService(properties(TestJwtSecret.random()));
        CustomUserDetails userDetails = new CustomUserDetails(user("ayse@satmis.com"));

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractUsername(token)).isEqualTo("ayse@satmis.com");
        assertThat(jwtService.isValid(token, userDetails)).isTrue();
        assertThat(jwtService.isValid(token, new CustomUserDetails(user("other@satmis.com")))).isFalse();
    }

    @Test
    void isValid_tokenSignedWithAnotherSecret_returnsFalse() {
        CustomUserDetails userDetails = new CustomUserDetails(user("ayse@satmis.com"));
        String foreignToken = new JwtService(properties(TestJwtSecret.random())).generateAccessToken(userDetails);

        assertThat(new JwtService(properties(TestJwtSecret.random())).isValid(foreignToken, userDetails)).isFalse();
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setRole(Role.USER);
        return user;
    }
}
