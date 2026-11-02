package com.satmis.listing;

import org.springframework.data.domain.Sort;

// Price sorts use the sale price: the sold vehicles page is about what cars actually sold for
public enum SoldVehicleSort {
    NEWEST(Sort.Direction.DESC, "soldAt"),
    OLDEST(Sort.Direction.ASC, "soldAt"),
    PRICE_ASC(Sort.Direction.ASC, "salePrice"),
    PRICE_DESC(Sort.Direction.DESC, "salePrice"),
    YEAR_DESC(Sort.Direction.DESC, "vehicleDetail.modelYear"),
    YEAR_ASC(Sort.Direction.ASC, "vehicleDetail.modelYear"),
    KM_ASC(Sort.Direction.ASC, "vehicleDetail.kilometer"),
    KM_DESC(Sort.Direction.DESC, "vehicleDetail.kilometer");

    private final Sort.Direction direction;
    private final String property;

    SoldVehicleSort(Sort.Direction direction, String property) {
        this.direction = direction;
        this.property = property;
    }

    // id as tie-breaker keeps paging stable when many rows share the sorted value
    public Sort toSort() {
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
