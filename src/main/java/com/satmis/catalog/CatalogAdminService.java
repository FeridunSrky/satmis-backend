package com.satmis.catalog;

import com.satmis.common.ApiException;
import com.satmis.catalog.dto.CatalogItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

// Add-only on purpose: CatalogSeedRunner re-inserts seed rows by name, so a renamed or deleted seed row would return
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private final BrandRepository brandRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final VehicleSeriesRepository vehicleSeriesRepository;
    private final CatalogMapper catalogMapper;

    @Transactional
    public CatalogItemResponse addBrand(String requestedName) {
        String name = normalize(requestedName);
        ensureUnique(brandRepository.findAll(), Brand::getName, name, "Bu isimde bir marka zaten var");
        Brand brand = new Brand();
        brand.setName(name);
        return catalogMapper.toResponse(brandRepository.saveAndFlush(brand));
    }

    @Transactional
    public CatalogItemResponse addModel(Long brandId, String requestedName) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Marka bulunamadı"));
        String name = normalize(requestedName);
        ensureUnique(vehicleModelRepository.findByBrandId(brandId), VehicleModel::getName, name,
                "Bu markada bu isimde bir model zaten var");
        VehicleModel model = new VehicleModel();
        model.setName(name);
        model.setBrand(brand);
        return catalogMapper.toResponse(vehicleModelRepository.saveAndFlush(model));
    }

    @Transactional
    public CatalogItemResponse addSeries(Long modelId, String requestedName) {
        VehicleModel model = vehicleModelRepository.findById(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model bulunamadı"));
        String name = normalize(requestedName);
        ensureUnique(vehicleSeriesRepository.findByModelId(modelId), VehicleSeries::getName, name,
                "Bu modelde bu isimde bir seri zaten var");
        VehicleSeries series = new VehicleSeries();
        series.setName(name);
        series.setModel(model);
        return catalogMapper.toResponse(vehicleSeriesRepository.saveAndFlush(series));
    }

    private static String normalize(String name) {
        return WHITESPACE_RUN.matcher(name.strip()).replaceAll(" ");
    }

    // The unique constraints are case-sensitive; "bmw" next to "BMW" would still be a duplicate for users.
    // A concurrent insert of the exact same name is caught by the constraint (409 via DataIntegrityViolation).
    private static <T> void ensureUnique(List<T> siblings, Function<T, String> nameOf, String name,
                                         String conflictMessage) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"));
        collator.setStrength(Collator.SECONDARY);
        if (siblings.stream().map(nameOf).anyMatch(existing -> collator.equals(existing, name))) {
            throw new ApiException(HttpStatus.CONFLICT, conflictMessage);
        }
    }
}
