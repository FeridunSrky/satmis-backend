package com.satmis.listing;

import org.springframework.data.domain.Sort;

public enum ListingSort {
    NEWEST(Sort.Direction.DESC, "createdAt"),
    OLDEST(Sort.Direction.ASC, "createdAt"),
    PRICE_ASC(Sort.Direction.ASC, "price"),
    PRICE_DESC(Sort.Direction.DESC, "price"),
    YEAR_DESC(Sort.Direction.DESC, "vehicleDetail.modelYear"),
    YEAR_ASC(Sort.Direction.ASC, "vehicleDetail.modelYear"),
    KM_ASC(Sort.Direction.ASC, "vehicleDetail.kilometer"),
    KM_DESC(Sort.Direction.DESC, "vehicleDetail.kilometer");

    private final Sort.Direction direction;
    private final String property;

    ListingSort(Sort.Direction direction, String property) {
        this.direction = direction;
        this.property = property;
    }

    // id as tie-breaker keeps paging stable when many rows share the sorted value
    public Sort toSort() {
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
