package com.satmis.comment;

// Published when a comment (not a reply) goes live, so the listing owner can answer it
public record CommentApprovedEvent(Long listingId, String listingTitle, String ownerEmail, String ownerFirstName) {
}
