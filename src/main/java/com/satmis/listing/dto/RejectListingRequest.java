package com.satmis.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RejectListingRequest(
        @NotNull(message = "Versiyon zorunludur")
        Long version,

        // Shown to the owner as is
        @NotBlank(message = "Red nedeni zorunludur")
        @Size(max = 500, message = "Red nedeni en fazla 500 karakter olabilir")
        String reason
) {
}
