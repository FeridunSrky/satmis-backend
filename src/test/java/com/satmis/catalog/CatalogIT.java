package com.satmis.catalog;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestJwtSecret;
import com.satmis.catalog.seed.CatalogSeedRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class CatalogIT {

    private static final int CITY_COUNT = 81;
    private static final int DISTRICT_COUNT = 973;
    private static final int ISTANBUL_DISTRICT_COUNT = 39;
    private static final String COUNT_ALL_CATALOG_ROWS = """
            select (select count(*) from brands) + (select count(*) from vehicle_models)
                 + (select count(*) from vehicle_series) + (select count(*) from cities)
                 + (select count(*) from districts)""";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogSeedRunner catalogSeedRunner;

    @Test
    void seed_onStartup_loadsAllCitiesAndDistricts() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from cities", Integer.class)).isEqualTo(CITY_COUNT);
        assertThat(jdbcTemplate.queryForObject("select count(*) from districts", Integer.class))
                .isEqualTo(DISTRICT_COUNT);
    }

    @Test
    void seed_runTwice_addsNoDuplicates() {
        Integer before = jdbcTemplate.queryForObject(COUNT_ALL_CATALOG_ROWS, Integer.class);

        catalogSeedRunner.run(new DefaultApplicationArguments());

        assertThat(jdbcTemplate.queryForObject(COUNT_ALL_CATALOG_ROWS, Integer.class)).isEqualTo(before);
    }

    @Test
    void getCities_withoutToken_returnsTurkishSortedList() throws Exception {
        String body = mockMvc.perform(get("/api/catalog/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CITY_COUNT)))
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(body, "$[*].name");
        assertThat(names.getFirst()).isEqualTo("Adana");
        assertThat(names).containsSubsequence("Bursa", "Çanakkale", "Denizli");
        assertThat(names).containsSubsequence("Iğdır", "Isparta", "İstanbul", "İzmir");
        assertThat(names).containsSubsequence("Sivas", "Şanlıurfa", "Tekirdağ");
    }

    @Test
    void getDistricts_existingCity_returnsItsDistricts() throws Exception {
        Long istanbulId = jdbcTemplate.queryForObject("select id from cities where name = 'İstanbul'", Long.class);

        mockMvc.perform(get("/api/catalog/cities/{cityId}/districts", istanbulId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(ISTANBUL_DISTRICT_COUNT)))
                .andExpect(jsonPath("$[*].name", hasItem("Kadıköy")));
    }

    @Test
    void getBrandModelSeries_drillDown_returnsNestedItems() throws Exception {
        String brands = mockMvc.perform(get("/api/catalog/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Audi"))
                .andReturn().getResponse().getContentAsString();
        Integer renaultId = JsonPath.<List<Integer>>read(brands, "$[?(@.name == 'Renault')].id").getFirst();

        String models = mockMvc.perform(get("/api/catalog/brands/{brandId}/models", renaultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Clio")))
                .andReturn().getResponse().getContentAsString();
        Integer clioId = JsonPath.<List<Integer>>read(models, "$[?(@.name == 'Clio')].id").getFirst();

        mockMvc.perform(get("/api/catalog/models/{modelId}/series", clioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("1.0 TCe")));
    }

    @Test
    void getModels_unknownBrand_returns404() throws Exception {
        mockMvc.perform(get("/api/catalog/brands/{brandId}/models", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Marka bulunamadı"));
    }

    @Test
    void getDistricts_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/catalog/cities/abc/districts"))
                .andExpect(status().isBadRequest());
    }
}
