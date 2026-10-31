package com.satmis.listing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.listing-images")
public record ListingImageProperties(
        @NotNull DataSize maxSize,
        // Uploaded images not saved in a listing yet; caps how much storage one account can fill
        @Positive int maxUnattachedPerUser,
        // How long a presigned upload URL stays usable if it leaks
        @NotNull Duration uploadUrlTtl
) {
}
