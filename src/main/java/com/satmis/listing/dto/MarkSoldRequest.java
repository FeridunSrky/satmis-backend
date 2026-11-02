package com.satmis.listing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// The sale price may differ from the asking price after bargaining; it is in the listing's currency
public record MarkSoldRequest(
        @NotNull(message = "Satış fiyatı zorunludur")
        @Positive(message = "Satış fiyatı sıfırdan büyük olmalıdır")
        @Digits(integer = 13, fraction = 2, message = "Satış fiyatı en fazla 13 tam ve 2 ondalık haneli olabilir")
        BigDecimal salePrice,

        @Size(max = 1000, message = "Satış açıklaması en fazla 1000 karakter olabilir")
        String note,

        @NotNull(message = "Versiyon zorunludur")
        Long version
) {
}
