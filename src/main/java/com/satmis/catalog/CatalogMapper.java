package com.satmis.catalog;

import com.satmis.catalog.dto.CatalogItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogMapper {

    CatalogItemResponse toResponse(Brand brand);

    CatalogItemResponse toResponse(VehicleModel model);

    CatalogItemResponse toResponse(VehicleSeries series);

    CatalogItemResponse toResponse(City city);

    CatalogItemResponse toResponse(District district);

    List<CatalogItemResponse> toBrandResponses(List<Brand> brands);

    List<CatalogItemResponse> toModelResponses(List<VehicleModel> models);

    List<CatalogItemResponse> toSeriesResponses(List<VehicleSeries> series);

    List<CatalogItemResponse> toCityResponses(List<City> cities);

    List<CatalogItemResponse> toDistrictResponses(List<District> districts);
}
