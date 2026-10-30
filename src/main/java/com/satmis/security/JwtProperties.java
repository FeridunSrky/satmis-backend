package com.satmis.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long accessExpirationMs,
        @Positive long refreshExpirationMs,
        // How long a just-rotated refresh token may come back without counting as theft
        @NotNull Duration refreshReuseGrace
) {
}
