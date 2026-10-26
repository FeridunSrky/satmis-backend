package com.satmis.catalog.seed;

import com.satmis.catalog.City;
import com.satmis.catalog.CityRepository;
import com.satmis.catalog.District;
import com.satmis.catalog.DistrictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
class LocationSeeder {

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    // Insert-only: rows already in the database are kept, so it is safe to run on every startup
    @Transactional
    int seed(List<CitySeed> citySeeds) {
        Map<String, City> citiesByName = SeedIndexes.byName(cityRepository.findAll(), City::getName);
        Map<Long, Set<String>> districtNamesByCity = SeedIndexes.namesByParentId(
                districtRepository.findAll(), district -> district.getCity().getId(), District::getName);

        int createdCount = 0;
        for (CitySeed citySeed : citySeeds) {
            City city = citiesByName.get(citySeed.name());
            if (city == null) {
                city = cityRepository.save(newCity(citySeed.name()));
                createdCount++;
            }
            Set<String> existingDistricts = districtNamesByCity.getOrDefault(city.getId(), Set.of());
            createdCount += saveMissingDistricts(city, citySeed.districts(), existingDistricts);
        }
        return createdCount;
    }

    private int saveMissingDistricts(City city, List<String> districtNames, Set<String> existingNames) {
        List<District> missing = districtNames.stream()
                .filter(name -> !existingNames.contains(name))
                .map(name -> newDistrict(name, city))
                .toList();
        districtRepository.saveAll(missing);
        return missing.size();
    }

    private static City newCity(String name) {
        City city = new City();
        city.setName(name);
        return city;
    }

    private static District newDistrict(String name, City city) {
        District district = new District();
        district.setName(name);
        district.setCity(city);
        return district;
    }
}
