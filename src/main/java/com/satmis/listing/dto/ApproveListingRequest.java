package com.satmis.listing.dto;

import jakarta.validation.constraints.NotNull;

// The version the admin reviewed: if the owner edited meanwhile, approving fails instead of publishing unseen content
public record ApproveListingRequest(
        @NotNull(message = "Versiyon zorunludur")
        Long version
) {
}
