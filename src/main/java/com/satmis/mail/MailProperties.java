package com.satmis.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        // Off in tests that do not run a mail server
        boolean enabled,
        @NotBlank String from
) {
}
