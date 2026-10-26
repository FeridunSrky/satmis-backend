package com.satmis.listing.dto;

import com.satmis.enums.ListingStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull(message = "Durum zorunludur")
        ListingStatus status
) {
}
