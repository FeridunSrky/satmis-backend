package com.satmis.listing.dto;

import com.satmis.enums.CurrencyType;
import com.satmis.enums.FuelType;
import com.satmis.enums.GearType;
import com.satmis.enums.ListingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingSummaryResponse(
        Long id,
        String title,
        BigDecimal price,
        CurrencyType currency,
        ListingStatus status,
        Instant createdAt,
        String cityName,
        String districtName,
        String brandName,
        String modelName,
        String seriesName,
        Integer modelYear,
        Integer kilometer,
        FuelType fuelType,
        GearType gearType,
        String coverImageUrl
) {
}
