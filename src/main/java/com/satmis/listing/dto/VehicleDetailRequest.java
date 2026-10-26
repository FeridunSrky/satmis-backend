package com.satmis.listing.dto;

import com.satmis.common.validation.ModelYear;
import com.satmis.enums.BodyType;
import com.satmis.enums.FromWho;
import com.satmis.enums.FuelType;
import com.satmis.enums.GearType;
import com.satmis.enums.Traction;
import com.satmis.enums.VehicleCondition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VehicleDetailRequest(
        @NotNull(message = "Seri zorunludur")
        Long seriesId,

        @NotNull(message = "Model yılı zorunludur")
        @ModelYear
        Integer modelYear,

        @NotNull(message = "Kilometre zorunludur")
        @PositiveOrZero(message = "Kilometre negatif olamaz")
        @Max(value = 2_000_000, message = "Kilometre en fazla 2.000.000 olabilir")
        Integer kilometer,

        @NotNull(message = "Yakıt tipi zorunludur")
        FuelType fuelType,

        @NotNull(message = "Vites tipi zorunludur")
        GearType gearType,

        BodyType bodyType,

        @Size(max = 30, message = "Renk en fazla 30 karakter olabilir")
        String color,

        @Positive(message = "Motor gücü pozitif olmalıdır")
        @Max(value = 2_000, message = "Motor gücü en fazla 2000 hp olabilir")
        Integer enginePower,

        @Positive(message = "Motor hacmi pozitif olmalıdır")
        @Max(value = 10_000, message = "Motor hacmi en fazla 10000 cc olabilir")
        Integer engineCapacity,

        Traction traction,

        @NotNull(message = "Araç durumu zorunludur")
        VehicleCondition vehicleCondition,

        @NotNull(message = "Kimden bilgisi zorunludur")
        FromWho fromWho,

        @NotNull(message = "Hasar kaydı bilgisi zorunludur")
        Boolean hasDamageRecord,

        @NotNull(message = "Takas bilgisi zorunludur")
        Boolean exchangeable
) {
}
