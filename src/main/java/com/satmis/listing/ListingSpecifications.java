package com.satmis.listing;

import com.satmis.enums.ListingStatus;
import com.satmis.listing.dto.ListingSearchCriteria;
import com.satmis.vehicle.VehicleDetail;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class ListingSpecifications {

    private static final String ID = "id";

    private ListingSpecifications() {
    }

    static Specification<Listing> activeMatching(ListingSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), ListingStatus.ACTIVE));
            addListingPredicates(predicates, criteria, root, builder);
            addVehiclePredicates(predicates, criteria, root.join("vehicleDetail"), builder);
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    static Specification<Listing> ownedBy(Long ownerId) {
        return (root, query, builder) -> builder.equal(root.get("owner").get(ID), ownerId);
    }

    private static void addListingPredicates(List<Predicate> predicates, ListingSearchCriteria criteria,
                                             Root<Listing> root, CriteriaBuilder builder) {
        Path<BigDecimal> price = root.get("price");
        addIfPresent(predicates, criteria.districtId(),
                () -> builder.equal(root.get("district").get(ID), criteria.districtId()));
        addIfPresent(predicates, criteria.cityId(),
                () -> builder.equal(root.get("district").get("city").get(ID), criteria.cityId()));
        addIfPresent(predicates, criteria.currency(),
                () -> builder.equal(root.get("currency"), criteria.currency()));
        addIfPresent(predicates, criteria.minPrice(),
                () -> builder.greaterThanOrEqualTo(price, criteria.minPrice()));
        addIfPresent(predicates, criteria.maxPrice(),
                () -> builder.lessThanOrEqualTo(price, criteria.maxPrice()));
    }

    private static void addVehiclePredicates(List<Predicate> predicates, ListingSearchCriteria criteria,
                                             Join<Listing, VehicleDetail> vehicle, CriteriaBuilder builder) {
        Path<Integer> modelYear = vehicle.get("modelYear");
        addIfPresent(predicates, criteria.seriesId(),
                () -> builder.equal(vehicle.get("series").get(ID), criteria.seriesId()));
        addIfPresent(predicates, criteria.modelId(),
                () -> builder.equal(vehicle.get("series").get("model").get(ID), criteria.modelId()));
        addIfPresent(predicates, criteria.brandId(),
                () -> builder.equal(vehicle.get("series").get("model").get("brand").get(ID), criteria.brandId()));
        addIfPresent(predicates, criteria.minYear(), () -> builder.greaterThanOrEqualTo(modelYear, criteria.minYear()));
        addIfPresent(predicates, criteria.maxYear(), () -> builder.lessThanOrEqualTo(modelYear, criteria.maxYear()));
        addIfPresent(predicates, criteria.maxKm(),
                () -> builder.lessThanOrEqualTo(vehicle.get("kilometer"), criteria.maxKm()));
        addIfPresent(predicates, criteria.fuelType(), () -> builder.equal(vehicle.get("fuelType"), criteria.fuelType()));
        addIfPresent(predicates, criteria.gearType(), () -> builder.equal(vehicle.get("gearType"), criteria.gearType()));
        addIfPresent(predicates, criteria.bodyType(), () -> builder.equal(vehicle.get("bodyType"), criteria.bodyType()));
        addIfPresent(predicates, criteria.vehicleCondition(),
                () -> builder.equal(vehicle.get("vehicleCondition"), criteria.vehicleCondition()));
        addIfPresent(predicates, criteria.fromWho(), () -> builder.equal(vehicle.get("fromWho"), criteria.fromWho()));
        addIfPresent(predicates, criteria.hasDamageRecord(),
                () -> builder.equal(vehicle.get("hasDamageRecord"), criteria.hasDamageRecord()));
    }

    private static void addIfPresent(List<Predicate> predicates, Object filterValue, Supplier<Predicate> predicate) {
        if (filterValue != null) {
            predicates.add(predicate.get());
        }
    }
}
