package com.satmis.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.listing-comments")
public record ListingCommentProperties(
        // Per user and listing, rejected and deleted comments included, so the moderation queue cannot be flooded
        @Positive int maxCommentsPerListing,
        // Replies the owner may send to one comment; a rejected reply leaves the remaining attempts
        @Positive int maxReplyAttempts,
        @Positive int maxAttachmentsPerComment,
        // Frontend listing page; the listing id is appended, e.g. https://satmis.com/ilan
        @NotBlank String listingLinkBaseUrl
) {
}
