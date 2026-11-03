package com.satmis.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

// Used for comments and for the owner's replies. attachmentIds: order = display order; may be empty.
public record CommentRequest(
        @NotBlank(message = "Yorum metni zorunludur")
        @Size(min = 20, max = 2000, message = "Yorum 20 ile 2000 karakter arasında olmalıdır")
        String body,

        List<Long> attachmentIds
) {

    public List<Long> attachmentIdsOrEmpty() {
        return attachmentIds == null ? List.of() : attachmentIds;
    }
}
