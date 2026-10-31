package com.satmis.favorite;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestImages;
import com.satmis.TestJwtSecret;
import com.satmis.TestListings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class FavoriteIT {

    private static final String FAVORITE = "/api/listings/{id}/favorite";
    private static final String MY_FAVORITES = "/api/users/me/favorites";

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
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void add_twice_isIdempotentAndShownInDetail() throws Exception {
        long listingId = createListing(registerUser());
        String fan = registerUser();

        perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());
        perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());

        perform(get("/api/listings/{id}", listingId), fan, null)
                .andExpect(jsonPath("$.favoriteCount").value(1))
                .andExpect(jsonPath("$.favoritedByMe").value(true));
        mockMvc.perform(get("/api/listings/{id}", listingId))
                .andExpect(jsonPath("$.favoriteCount").value(1))
                .andExpect(jsonPath("$.favoritedByMe").value(false));
    }

    @Test
    void add_hiddenOrMissingListing_returns404() throws Exception {
        String owner = registerUser();
        long listingId = createListing(owner);
        perform(patch("/api/listings/{id}/status", listingId), owner, Map.of("status", "PASSIVE"));
        String fan = registerUser();

        perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNotFound());
        perform(put(FAVORITE, Long.MAX_VALUE), fan, null).andExpect(status().isNotFound());
    }

    @Test
    void add_withoutToken_returns401() throws Exception {
        long listingId = createListing(registerUser());

        mockMvc.perform(put(FAVORITE, listingId)).andExpect(status().isUnauthorized());
    }

    @Test
    void remove_twice_isIdempotent() throws Exception {
        long listingId = createListing(registerUser());
        String fan = registerUser();
        perform(put(FAVORITE, listingId), fan, null);

        perform(delete(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());
        perform(delete(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());

        perform(get("/api/listings/{id}", listingId), fan, null)
                .andExpect(jsonPath("$.favoriteCount").value(0))
                .andExpect(jsonPath("$.favoritedByMe").value(false));
    }

    @Test
    void getMyFavorites_newestFirstAndOnlyActiveListings() throws Exception {
        String owner = registerUser();
        long first = createListing(owner);
        long second = createListing(owner);
        long laterHidden = createListing(owner);
        String fan = registerUser();
        for (long listingId : List.of(first, second, laterHidden)) {
            perform(put(FAVORITE, listingId), fan, null).andExpect(status().isNoContent());
        }
        perform(patch("/api/listings/{id}/status", laterHidden), owner, Map.of("status", "PASSIVE"));

        assertThat(favoriteIds(fan, "")).containsExactly(second, first);
        perform(get(MY_FAVORITES + "?size=1&page=1"), fan, null)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void getMyFavorites_otherUsersFavorites_notIncluded() throws Exception {
        long listingId = createListing(registerUser());
        perform(put(FAVORITE, listingId), registerUser(), null);

        assertThat(favoriteIds(registerUser(), "")).isEmpty();
    }

    private List<Long> favoriteIds(String token, String query) throws Exception {
        String response = perform(get(MY_FAVORITES + query), token, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].id").stream().map(Number::longValue).toList();
    }

    private long createListing(String token) throws Exception {
        Map<String, Object> vehicle = Map.of(
                "seriesId", lookupId("select id from vehicle_series order by id limit 1"),
                "modelYear", 2021, "kilometer", 20_000, "fuelType", "DIESEL", "gearType", "MANUAL",
                "vehicleCondition", "USED", "fromWho", "OWNER", "hasDamageRecord", false, "exchangeable", false);
        Map<String, Object> body = Map.of(
                "title", "Favori testi ilanı", "price", 500_000, "currency", "TRY",
                "districtId", lookupId("select id from districts order by id limit 1"),
                "vehicle", vehicle, "imageIds", List.of(TestImages.uploaded(jdbcTemplate, userId(token))));
        String response = perform(post("/api/listings"), token, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long listingId = JsonPath.<Number>read(response, "$.id").longValue();
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    private long userId(String token) throws Exception {
        String response = perform(get("/api/users/me"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private long lookupId(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    // Listing creation needs a verified e-mail, so the helper verifies it directly in the database
    private String registerUser() throws Exception {
        String email = "fan-" + UUID.randomUUID() + "@satmis.com";
        Map<String, String> body = Map.of("email", email,
                "password", "Sifre1234", "firstName", "Can", "lastName", "Demir", "phone", "+905339876543");
        String response = perform(post("/api/auth/register"), null, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        return JsonPath.read(response, "$.accessToken");
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }
}
