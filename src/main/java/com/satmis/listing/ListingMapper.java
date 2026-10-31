package com.satmis.listing;

import com.satmis.catalog.District;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingRequest;
import com.satmis.listing.dto.ListingSummaryResponse;
import com.satmis.listing.dto.VehicleDetailRequest;
import com.satmis.storage.ObjectStorage;
import com.satmis.user.User;
import com.satmis.vehicle.VehicleDetail;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ListingMapper {

    @Mapping(target = "id", source = "listing.id")
    @Mapping(target = "seller", expression = "java(toSeller(listing.getOwner(), includeSellerPhone))")
    @Mapping(target = "location", source = "listing.district")
    @Mapping(target = "vehicle", source = "listing.vehicleDetail")
    ListingDetailResponse toDetail(Listing listing, long favoriteCount, boolean favoritedByMe,
                                   boolean includeSellerPhone, @Context ObjectStorage objectStorage);

    default ListingDetailResponse.Seller toSeller(User owner, boolean includePhone) {
        return new ListingDetailResponse.Seller(owner.getId(), owner.getFirstName(), owner.getLastName(),
                includePhone ? owner.getPhone() : null);
    }

    @Mapping(target = "cityId", source = "city.id")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "districtId", source = "id")
    @Mapping(target = "districtName", source = "name")
    ListingDetailResponse.Location toLocation(District district);

    @Mapping(target = "brandId", source = "series.model.brand.id")
    @Mapping(target = "brandName", source = "series.model.brand.name")
    @Mapping(target = "modelId", source = "series.model.id")
    @Mapping(target = "modelName", source = "series.model.name")
    @Mapping(target = "seriesId", source = "series.id")
    @Mapping(target = "seriesName", source = "series.name")
    ListingDetailResponse.Vehicle toVehicle(VehicleDetail vehicleDetail);

    // Only the storage key is persisted; the public address comes from configuration
    @Mapping(target = "url", expression = "java(objectStorage.publicUrl(image.getObjectKey()))")
    ListingDetailResponse.Image toImage(ListingImage image, @Context ObjectStorage objectStorage);

    @Mapping(target = "id", source = "listing.id")
    @Mapping(target = "cityName", source = "listing.district.city.name")
    @Mapping(target = "districtName", source = "listing.district.name")
    @Mapping(target = "brandName", source = "listing.vehicleDetail.series.model.brand.name")
    @Mapping(target = "modelName", source = "listing.vehicleDetail.series.model.name")
    @Mapping(target = "seriesName", source = "listing.vehicleDetail.series.name")
    @Mapping(target = "modelYear", source = "listing.vehicleDetail.modelYear")
    @Mapping(target = "kilometer", source = "listing.vehicleDetail.kilometer")
    @Mapping(target = "fuelType", source = "listing.vehicleDetail.fuelType")
    @Mapping(target = "gearType", source = "listing.vehicleDetail.gearType")
    ListingSummaryResponse toSummary(Listing listing, String coverImageUrl);

    // Relations, status, counters and version are owned by the service, never copied from the request
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "district", ignore = true)
    @Mapping(target = "vehicleDetail", ignore = true)
    @Mapping(target = "images", ignore = true)
    void updateListing(ListingRequest request, @MappingTarget Listing listing);

    @Mapping(target = "listingId", ignore = true)
    @Mapping(target = "listing", ignore = true)
    @Mapping(target = "series", ignore = true)
    void updateVehicleDetail(VehicleDetailRequest request, @MappingTarget VehicleDetail vehicleDetail);
}
