package com.satmis.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// For lists whose order is fixed by the endpoint, so clients only choose page and size
public record PagingRequest(
        @PositiveOrZero(message = "Sayfa numarası negatif olamaz")
        Integer page,

        @Min(value = 1, message = "Sayfa boyutu en az 1 olmalıdır")
        @Max(value = 50, message = "Sayfa boyutu en fazla 50 olabilir")
        Integer size
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public Pageable toPageable() {
        return PageRequest.of(page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
    }
}
