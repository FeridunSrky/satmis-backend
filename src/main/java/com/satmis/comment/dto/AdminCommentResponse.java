package com.satmis.comment.dto;

import com.satmis.enums.CommentStatus;
import com.satmis.enums.ListingStatus;

import java.time.Instant;
import java.util.List;

// For moderation: full author details, and for a reply the comment it answers
public record AdminCommentResponse(
        Long id,
        Long version,
        Long listingId,
        String listingTitle,
        ListingStatus listingStatus,
        Author author,
        ParentComment parent,
        String body,
        CommentStatus status,
        String rejectionReason,
        List<AttachmentResponse> attachments,
        Instant createdAt,
        Instant reviewedAt
) {

    public record Author(Long id, String firstName, String lastName, String email) {
    }

    public record ParentComment(Long id, String authorName, String body, CommentStatus status) {
    }
}
