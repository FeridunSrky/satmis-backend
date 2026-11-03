package com.satmis.comment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.comment-attachments")
public record CommentAttachmentProperties(
        @NotNull DataSize maxSize,
        // Uploaded files not sent in a comment yet; caps how much storage one account can fill
        @Positive int maxUnattachedPerUser,
        // How long a presigned upload URL stays usable if it leaks
        @NotNull Duration uploadUrlTtl,
        // Uploads not sent in a comment within this time are removed by the cleanup job
        @NotNull Duration abandonedAfter
) {
}
