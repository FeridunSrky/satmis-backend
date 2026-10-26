package com.satmis.catalog;

import com.satmis.catalog.dto.CatalogItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Small reference lists for dropdowns, returned whole instead of paged
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/brands")
    public List<CatalogItemResponse> getBrands() {
        return catalogService.getBrands();
    }

    @GetMapping("/brands/{brandId}/models")
    public List<CatalogItemResponse> getModels(@PathVariable Long brandId) {
        return catalogService.getModels(brandId);
    }

    @GetMapping("/models/{modelId}/series")
    public List<CatalogItemResponse> getSeries(@PathVariable Long modelId) {
        return catalogService.getSeries(modelId);
    }

    @GetMapping("/cities")
    public List<CatalogItemResponse> getCities() {
        return catalogService.getCities();
    }

    @GetMapping("/cities/{cityId}/districts")
    public List<CatalogItemResponse> getDistricts(@PathVariable Long cityId) {
        return catalogService.getDistricts(cityId);
    }
}
