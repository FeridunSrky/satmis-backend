package com.satmis.comment.dto;

import java.time.Instant;
import java.util.List;

public record CommentReplyResponse(Long id, String body, List<AttachmentResponse> attachments, Instant createdAt) {
}
