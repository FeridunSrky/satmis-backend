package com.satmis.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(
        @NotNull Duration tokenTtl,
        // Frontend page that reads ?token=, asks for the new password and calls POST /api/auth/reset-password
        @NotBlank String linkBaseUrl
) {
}
