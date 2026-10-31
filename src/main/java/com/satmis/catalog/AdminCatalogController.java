package com.satmis.catalog;

import com.satmis.catalog.dto.CatalogItemResponse;
import com.satmis.catalog.dto.CatalogNameRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// No Location header: the catalog has no single-item endpoint, clients reload the parent's list
@RestController
@RequestMapping("/api/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final CatalogAdminService catalogAdminService;

    @PostMapping("/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse addBrand(@Valid @RequestBody CatalogNameRequest request) {
        return catalogAdminService.addBrand(request.name());
    }

    @PostMapping("/brands/{brandId}/models")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse addModel(@PathVariable Long brandId, @Valid @RequestBody CatalogNameRequest request) {
        return catalogAdminService.addModel(brandId, request.name());
    }

    @PostMapping("/models/{modelId}/series")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse addSeries(@PathVariable Long modelId, @Valid @RequestBody CatalogNameRequest request) {
        return catalogAdminService.addSeries(modelId, request.name());
    }
}
