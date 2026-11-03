package com.satmis.comment.dto;

import java.time.Instant;
import java.util.List;

// A published comment. authorName is masked ("Ayşe Y."); the reply is the owner's and carries no name.
// canReply is true only for the listing owner while this comment still accepts a reply.
public record CommentResponse(
        Long id,
        String authorName,
        String body,
        List<AttachmentResponse> attachments,
        Instant createdAt,
        CommentReplyResponse reply,
        boolean canReply
) {
}
