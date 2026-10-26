package com.satmis.catalog.seed;

import com.satmis.catalog.Brand;
import com.satmis.catalog.BrandRepository;
import com.satmis.catalog.VehicleModel;
import com.satmis.catalog.VehicleModelRepository;
import com.satmis.catalog.VehicleSeries;
import com.satmis.catalog.VehicleSeriesRepository;
import com.satmis.catalog.seed.BrandSeed.ModelSeed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
class VehicleCatalogSeeder {

    private final BrandRepository brandRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final VehicleSeriesRepository vehicleSeriesRepository;

    // Insert-only: rows already in the database are kept, so it is safe to run on every startup
    @Transactional
    int seed(List<BrandSeed> brandSeeds) {
        Map<String, Brand> brandsByName = SeedIndexes.byName(brandRepository.findAll(), Brand::getName);
        Map<String, VehicleModel> modelsByKey = new HashMap<>();
        for (VehicleModel model : vehicleModelRepository.findAll()) {
            modelsByKey.put(modelKey(model.getBrand().getId(), model.getName()), model);
        }
        Map<Long, Set<String>> seriesNamesByModel = SeedIndexes.namesByParentId(
                vehicleSeriesRepository.findAll(), series -> series.getModel().getId(), VehicleSeries::getName);

        int createdCount = 0;
        for (BrandSeed brandSeed : brandSeeds) {
            Brand brand = brandsByName.get(brandSeed.name());
            if (brand == null) {
                brand = brandRepository.save(newBrand(brandSeed.name()));
                createdCount++;
            }
            for (ModelSeed modelSeed : brandSeed.models()) {
                createdCount += seedModel(brand, modelSeed, modelsByKey, seriesNamesByModel);
            }
        }
        return createdCount;
    }

    private int seedModel(Brand brand, ModelSeed modelSeed, Map<String, VehicleModel> modelsByKey,
                          Map<Long, Set<String>> seriesNamesByModel) {
        int createdCount = 0;
        VehicleModel model = modelsByKey.get(modelKey(brand.getId(), modelSeed.name()));
        if (model == null) {
            model = vehicleModelRepository.save(newModel(modelSeed.name(), brand));
            createdCount++;
        }
        Set<String> existingSeries = seriesNamesByModel.getOrDefault(model.getId(), Set.of());
        VehicleModel parent = model;
        List<VehicleSeries> missing = modelSeed.series().stream()
                .filter(name -> !existingSeries.contains(name))
                .map(name -> newSeries(name, parent))
                .toList();
        vehicleSeriesRepository.saveAll(missing);
        return createdCount + missing.size();
    }

    private static String modelKey(Long brandId, String modelName) {
        return brandId + "|" + modelName;
    }

    private static Brand newBrand(String name) {
        Brand brand = new Brand();
        brand.setName(name);
        return brand;
    }

    private static VehicleModel newModel(String name, Brand brand) {
        VehicleModel model = new VehicleModel();
        model.setName(name);
        model.setBrand(brand);
        return model;
    }

    private static VehicleSeries newSeries(String name, VehicleModel model) {
        VehicleSeries series = new VehicleSeries();
        series.setName(name);
        series.setModel(model);
        return series;
    }
}
