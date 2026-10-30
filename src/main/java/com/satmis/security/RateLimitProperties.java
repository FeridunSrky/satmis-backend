package com.satmis.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        // Upper bound on remembered clients, so requests from many addresses cannot exhaust memory
        @Positive int maxTrackedClients,
        @NotNull @Valid Rule login,
        @NotNull @Valid Rule register,
        @NotNull @Valid Rule refresh,
        @NotNull @Valid Rule resendVerification
) {

    // At most `capacity` requests per client within `period`, refilled gradually
    public record Rule(@Positive int capacity, @NotNull Duration period) {
    }
}
