package com.satmis.comment.dto;

public record CommentQuotaResponse(Long listingId, int maxComments, int usedComments, int remainingComments) {
}
