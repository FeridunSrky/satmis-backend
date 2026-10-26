package com.satmis.catalog;

import com.satmis.catalog.dto.CatalogItemResponse;
import com.satmis.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private final BrandRepository brandRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final VehicleSeriesRepository vehicleSeriesRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final CatalogMapper catalogMapper;

    public List<CatalogItemResponse> getBrands() {
        return sortByName(catalogMapper.toBrandResponses(brandRepository.findAll()));
    }

    public List<CatalogItemResponse> getModels(Long brandId) {
        ensureExists(brandRepository, brandId, "Marka bulunamadı");
        return sortByName(catalogMapper.toModelResponses(vehicleModelRepository.findByBrandId(brandId)));
    }

    public List<CatalogItemResponse> getSeries(Long modelId) {
        ensureExists(vehicleModelRepository, modelId, "Model bulunamadı");
        return sortByName(catalogMapper.toSeriesResponses(vehicleSeriesRepository.findByModelId(modelId)));
    }

    public List<CatalogItemResponse> getCities() {
        return sortByName(catalogMapper.toCityResponses(cityRepository.findAll()));
    }

    public List<CatalogItemResponse> getDistricts(Long cityId) {
        ensureExists(cityRepository, cityId, "İl bulunamadı");
        return sortByName(catalogMapper.toDistrictResponses(districtRepository.findByCityId(cityId)));
    }

    private static void ensureExists(JpaRepository<?, Long> repository, Long id, String notFoundMessage) {
        if (!repository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
    }

    // Sorted in Java with a Turkish collator: database collation would put Ç, İ, Ş in the wrong place
    private static List<CatalogItemResponse> sortByName(List<CatalogItemResponse> items) {
        Comparator<CatalogItemResponse> byName =
                Comparator.comparing(CatalogItemResponse::name, Collator.getInstance(TURKISH));
        return items.stream().sorted(byName).toList();
    }
}
