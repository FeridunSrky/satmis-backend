package com.satmis.comment.dto;

import com.satmis.enums.CommentStatus;

import java.time.Instant;
import java.util.List;

// The author's own comment or reply (parentId set), in any status; rejectionReason only while REJECTED
public record MyCommentResponse(
        Long id,
        Long listingId,
        String listingTitle,
        Long parentId,
        String body,
        CommentStatus status,
        String rejectionReason,
        List<AttachmentResponse> attachments,
        Instant createdAt
) {
}
