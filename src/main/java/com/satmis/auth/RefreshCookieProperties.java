package com.satmis.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
public record RefreshCookieProperties(
        @NotBlank String name,
        // Off only in dev, where the API is plain http
        boolean secure,
        @NotBlank String sameSite
) {
}
