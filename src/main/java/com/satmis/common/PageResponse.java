package com.satmis.common;

import org.springframework.data.domain.Page;

import java.util.List;

// Own page shape so the API contract does not depend on Spring Data's Page serialization
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> content, Page<?> page) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
