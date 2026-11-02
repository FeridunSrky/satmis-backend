package com.satmis.listing.dto;

import com.satmis.enums.BodyType;
import com.satmis.enums.CurrencyType;
import com.satmis.enums.FromWho;
import com.satmis.enums.FuelType;
import com.satmis.enums.GearType;
import com.satmis.enums.ListingStatus;
import com.satmis.enums.Traction;
import com.satmis.enums.VehicleCondition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ListingDetailResponse(
        Long id,
        Long version,
        String title,
        String description,
        BigDecimal price,
        CurrencyType currency,
        ListingStatus status,
        // Only while REJECTED, which only the owner and admins can see
        String rejectionReason,
        @Schema(nullable = true, description = "Sadece SOLD ilanda dolu")
        Sale sale,
        int viewCount,
        long favoriteCount,
        boolean favoritedByMe,
        Instant createdAt,
        Instant updatedAt,
        Seller seller,
        Location location,
        Vehicle vehicle,
        List<Image> images
) {

    // E-mail is never exposed; phone is null for anonymous viewers so bots cannot harvest numbers
    public record Seller(
            Long id,
            String firstName,
            String lastName,
            @Schema(nullable = true, description = "Sadece giriş yapmış kullanıcılara döner, anonim istekte null")
            String phone
    ) {
    }

    public record Location(Long cityId, String cityName, Long districtId, String districtName) {
    }

    public record Vehicle(
            Long brandId,
            String brandName,
            Long modelId,
            String modelName,
            Long seriesId,
            String seriesName,
            Integer modelYear,
            Integer kilometer,
            FuelType fuelType,
            GearType gearType,
            BodyType bodyType,
            String color,
            Integer enginePower,
            Integer engineCapacity,
            Traction traction,
            VehicleCondition vehicleCondition,
            FromWho fromWho,
            boolean hasDamageRecord,
            boolean exchangeable
    ) {
    }

    // hidden: an admin removed the sale from the sold vehicles page
    public record Sale(BigDecimal salePrice, String note, Instant soldAt, boolean hidden) {
    }

    public record Image(Long id, String url, int displayOrder, boolean cover) {
    }
}
