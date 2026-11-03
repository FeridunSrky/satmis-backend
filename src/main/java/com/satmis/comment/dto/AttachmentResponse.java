package com.satmis.comment.dto;

import com.satmis.enums.AttachmentType;

// PDFs are served as downloads; images can be shown inline
public record AttachmentResponse(Long id, AttachmentType type, String url, Long sizeBytes) {
}
