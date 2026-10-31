package com.satmis.listing.dto;

import com.satmis.common.validation.ModelYear;
import com.satmis.enums.BodyType;
import com.satmis.enums.CurrencyType;
import com.satmis.enums.FromWho;
import com.satmis.enums.FuelType;
import com.satmis.enums.GearType;
import com.satmis.enums.VehicleCondition;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// All filters are optional; price filters compare raw amounts, so pair them with currency.
// q matches brand, model, series, title and description by word prefix; every term must match.
public record ListingSearchCriteria(
        @Size(max = 100) String q,
        @Positive Long brandId,
        @Positive Long modelId,
        @Positive Long seriesId,
        @Positive Long cityId,
        @Positive Long districtId,
        @PositiveOrZero BigDecimal minPrice,
        @PositiveOrZero BigDecimal maxPrice,
        CurrencyType currency,
        @ModelYear Integer minYear,
        @ModelYear Integer maxYear,
        @PositiveOrZero Integer maxKm,
        FuelType fuelType,
        GearType gearType,
        BodyType bodyType,
        VehicleCondition vehicleCondition,
        FromWho fromWho,
        Boolean hasDamageRecord
) {
}
