package com.satmis.listing.dto;

import com.satmis.listing.SoldVehicleSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record SoldVehiclePageRequest(
        @PositiveOrZero(message = "Sayfa numarası negatif olamaz")
        Integer page,

        @Min(value = 1, message = "Sayfa boyutu en az 1 olmalıdır")
        @Max(value = 50, message = "Sayfa boyutu en fazla 50 olabilir")
        Integer size,

        SoldVehicleSort sort
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public Pageable toPageable() {
        return PageRequest.of(
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size,
                (sort == null ? SoldVehicleSort.NEWEST : sort).toSort());
    }
}
