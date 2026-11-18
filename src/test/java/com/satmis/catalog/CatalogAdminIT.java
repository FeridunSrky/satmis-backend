package com.satmis.catalog;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestApi;
import com.satmis.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestApi.class)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class CatalogAdminIT {

    private static final String ADMIN_CATALOG = "/api/admin/catalog";

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
    private TestApi api;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void addBrandModelSeries_asAdmin_createsUsableCatalogEntries() throws Exception {
        String admin = api.adminToken();
        String brandName = "Marka " + uniqueSuffix();

        long brandId = idOf(add(admin, "/brands", "  " + brandName.replace(" ", "   ") + " ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(brandName)));
        long modelId = idOf(add(admin, "/brands/" + brandId + "/models", "Model X").andExpect(status().isCreated()));
        long seriesId = idOf(add(admin, "/models/" + modelId + "/series", "1.6 Dizel").andExpect(status().isCreated()));

        assertThat(namesAt("/api/catalog/brands")).contains(brandName);
        assertThat(namesAt("/api/catalog/brands/" + brandId + "/models")).containsExactly("Model X");
        assertThat(namesAt("/api/catalog/models/" + modelId + "/series")).containsExactly("1.6 Dizel");
        assertThat(jdbcTemplate.queryForObject("select model_id from vehicle_series where id = ?", Long.class,
                seriesId)).isEqualTo(modelId);
    }

    @Test
    void add_sameNameIgnoringTurkishCase_returns409() throws Exception {
        String admin = api.adminToken();
        String suffix = uniqueSuffix();
        long brandId = idOf(add(admin, "/brands", "Şahin " + suffix).andExpect(status().isCreated()));

        add(admin, "/brands", "ŞAHİN " + suffix.toUpperCase(Locale.ROOT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bu isimde bir marka zaten var"));
        add(admin, "/brands", "BMW").andExpect(status().isConflict());

        long modelId = idOf(add(admin, "/brands/" + brandId + "/models", "Doğan").andExpect(status().isCreated()));
        add(admin, "/brands/" + brandId + "/models", "doğan").andExpect(status().isConflict());
        add(admin, "/models/" + modelId + "/series", "1.6").andExpect(status().isCreated());
        add(admin, "/models/" + modelId + "/series", " 1.6 ").andExpect(status().isConflict());
        // Same series name under another model is fine
        long otherModelId = idOf(add(admin, "/brands/" + brandId + "/models", "Kartal").andExpect(status().isCreated()));
        add(admin, "/models/" + otherModelId + "/series", "1.6").andExpect(status().isCreated());
    }

    @Test
    void add_invalidNameOrUnknownParent_isRejected() throws Exception {
        String admin = api.adminToken();

        add(admin, "/brands", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("İsim zorunludur"));
        add(admin, "/brands", "a".repeat(51))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
        add(admin, "/brands/99999999/models", "Model").andExpect(status().isNotFound());
        add(admin, "/models/99999999/series", "Seri").andExpect(status().isNotFound());
    }

    @Test
    void add_withoutAdminRole_returns401Or403() throws Exception {
        mockMvc.perform(post(ADMIN_CATALOG + "/brands").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Yetkisiz\"}"))
                .andExpect(status().isUnauthorized());
        add(api.register(TestApi.uniqueEmail()), "/brands", "Yetkisiz").andExpect(status().isForbidden());
        assertThat(namesAt("/api/catalog/brands")).doesNotContain("Yetkisiz");
    }

    private ResultActions add(String token, String path, String name) throws Exception {
        return api.perform(post(ADMIN_CATALOG + path), token, Map.of("name", name));
    }

    private List<String> namesAt(String path) throws Exception {
        String response = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$[*].name");
    }

    private static long idOf(ResultActions result) throws Exception {
        return JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), "$.id").longValue();
    }

    // Six letters without i (digits shifted to q-z): ROOT upper-casing i gives I, a different letter in Turkish
    private static String uniqueSuffix() {
        StringBuilder suffix = new StringBuilder();
        for (char hex : UUID.randomUUID().toString().replace("-", "").substring(0, 6).toCharArray()) {
            suffix.append(Character.isDigit(hex) ? (char) ('q' + (hex - '0')) : hex);
        }
        return suffix.toString();
    }
}
