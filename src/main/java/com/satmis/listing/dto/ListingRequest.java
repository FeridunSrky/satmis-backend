package com.satmis.listing.dto;

import com.satmis.common.ValidationGroups;
import com.satmis.common.validation.HttpsUrlList;
import com.satmis.enums.CurrencyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// Used for both create and update; version is only required on update (optimistic locking)
public record ListingRequest(
        @NotNull(groups = ValidationGroups.OnUpdate.class, message = "Versiyon zorunludur")
        Long version,

        @NotBlank(message = "Başlık zorunludur")
        @Size(min = 5, max = 150, message = "Başlık 5 ile 150 karakter arasında olmalıdır")
        String title,

        @Size(max = 5000, message = "Açıklama en fazla 5000 karakter olabilir")
        String description,

        @NotNull(message = "Fiyat zorunludur")
        @Positive(message = "Fiyat sıfırdan büyük olmalıdır")
        @Digits(integer = 13, fraction = 2, message = "Fiyat en fazla 13 tam ve 2 ondalık haneli olabilir")
        BigDecimal price,

        @NotNull(message = "Para birimi zorunludur")
        CurrencyType currency,

        @NotNull(message = "İlçe zorunludur")
        Long districtId,

        @NotNull(message = "Araç bilgileri zorunludur")
        @Valid
        VehicleDetailRequest vehicle,

        @NotNull(message = "En az bir görsel zorunludur")
        @Size(min = 1, max = 20, message = "1 ile 20 arasında görsel eklenmelidir")
        @HttpsUrlList
        List<String> imageUrls
) {
}
