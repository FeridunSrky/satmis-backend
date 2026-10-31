package com.satmis.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotNull URI endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        // Where browsers read objects: the R2 custom domain in prod, the bucket path on SeaweedFS in dev
        @NotBlank String publicBaseUrl,
        // SeaweedFS only understands bucket-in-path addressing; R2 accepts both
        boolean pathStyleAccess,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        // Upper bound for one call including retries
        @NotNull Duration callTimeout
) {
}
