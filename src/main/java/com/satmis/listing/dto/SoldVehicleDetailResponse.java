package com.satmis.listing.dto;

import com.satmis.enums.CurrencyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// No seller data: the page publishes prices, not who sold the car
public record SoldVehicleDetailResponse(
        Long id,
        String title,
        String description,
        BigDecimal listingPrice,
        BigDecimal salePrice,
        CurrencyType currency,
        String saleNote,
        Instant soldAt,
        Instant createdAt,
        ListingDetailResponse.Location location,
        ListingDetailResponse.Vehicle vehicle,
        List<ListingDetailResponse.Image> images
) {
}
