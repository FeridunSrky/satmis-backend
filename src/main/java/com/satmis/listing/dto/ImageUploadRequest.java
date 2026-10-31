package com.satmis.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Type and size are signed into the upload URL; the storage rejects a file that differs from them
public record ImageUploadRequest(
        @NotBlank(message = "Dosya türü zorunludur")
        String contentType,

        @NotNull(message = "Dosya boyutu zorunludur")
        @Positive(message = "Dosya boyutu sıfırdan büyük olmalıdır")
        Long sizeBytes
) {
}
