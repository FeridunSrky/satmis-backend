package com.satmis.catalog.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.catalog-enabled", havingValue = "true", matchIfMissing = true)
public class CatalogSeedRunner implements ApplicationRunner {

    private static final String LOCATIONS_FILE = "seed/locations.json";
    private static final String VEHICLES_FILE = "seed/vehicles.json";

    private final JsonMapper jsonMapper;
    private final LocationSeeder locationSeeder;
    private final VehicleCatalogSeeder vehicleCatalogSeeder;

    @Override
    public void run(ApplicationArguments args) {
        int locationRows = locationSeeder.seed(List.of(read(LOCATIONS_FILE, CitySeed[].class)));
        int vehicleRows = vehicleCatalogSeeder.seed(List.of(read(VEHICLES_FILE, BrandSeed[].class)));
        log.info("Catalog seed finished: {} location rows and {} vehicle catalog rows added",
                locationRows, vehicleRows);
    }

    private <T> T read(String path, Class<T> type) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return jsonMapper.readValue(input, type);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read catalog seed file " + path, ex);
        }
    }
}
