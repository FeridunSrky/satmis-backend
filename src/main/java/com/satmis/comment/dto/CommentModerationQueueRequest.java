package com.satmis.comment.dto;

import com.satmis.enums.CommentStatus;

// Waiting comments by default; APPROVED with a listingId lets an admin find a published comment to take down
public record CommentModerationQueueRequest(CommentStatus status, Long listingId) {

    public CommentStatus statusOrDefault() {
        return status == null ? CommentStatus.PENDING_APPROVAL : status;
    }
}
