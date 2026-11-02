package com.satmis.listing;

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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ListingSaleIT {

    private static final String LISTINGS = "/api/listings";
    private static final String PASSWORD = "Sifre1234";
    private static final int ASKING_PRICE = 750_000;

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
    void markSold_priceWithinRange_storesSaleAndShowsItToOwner() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);

        markSold(owner, listingId, 720_000, "  Pazarlıkla anlaştık.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.sale.salePrice").value(720_000))
                .andExpect(jsonPath("$.sale.note").value("Pazarlıkla anlaştık."))
                .andExpect(jsonPath("$.sale.soldAt").isNotEmpty())
                .andExpect(jsonPath("$.sale.hidden").value(false));

        perform(get(LISTINGS + "/{id}", listingId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale.salePrice").value(720_000));
        mockMvc.perform(get(LISTINGS + "/{id}", listingId)).andExpect(status().isNotFound());
    }

    @Test
    void markSold_blankNote_isStoredAsNull() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);

        markSold(owner, listingId, ASKING_PRICE, "   ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale.note").value(nullValue()));
    }

    @Test
    void markSold_priceAtRangeLimits_isAccepted() throws Exception {
        String owner = sellerToken();
        long lowest = liveListing(owner);
        long highest = liveListing(owner);

        markSold(owner, lowest, 600_000, null).andExpect(status().isOk());
        markSold(owner, highest, 825_000, null).andExpect(status().isOk());
    }

    @Test
    void markSold_priceOutsideRange_returns422WithCode() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);

        markSold(owner, listingId, new BigDecimal("599999.99"), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SALE_PRICE_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.message").value(
                        "Satış fiyatı ilan fiyatının %80'i ile %110'u arasında olmalıdır (600000 – 825000 TRY)"));
        markSold(owner, listingId, new BigDecimal("825000.01"), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SALE_PRICE_OUT_OF_RANGE"));
        assertThat(TestListings.statusOf(jdbcTemplate, listingId)).isEqualTo("ACTIVE");
    }

    @Test
    void markSold_invalidBody_returnsFieldErrors() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);

        perform(post(LISTINGS + "/{id}/sale", listingId), owner, Map.of("note", "x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.salePrice").isNotEmpty())
                .andExpect(jsonPath("$.errors.note").isNotEmpty())
                .andExpect(jsonPath("$.errors.version").isNotEmpty());
    }

    @Test
    void markSold_staleVersion_returns409() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);

        perform(post(LISTINGS + "/{id}/sale", listingId), owner,
                Map.of("salePrice", ASKING_PRICE, "version", versionOf(listingId) - 1))
                .andExpect(status().isConflict());
    }

    @Test
    void markSold_passiveListing_isAllowed() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);
        jdbcTemplate.update("update listings set status = 'PASSIVE' where id = ?", listingId);

        markSold(owner, listingId, ASKING_PRICE, null).andExpect(status().isOk());
    }

    @Test
    void markSold_listingAwaitingApprovalOrAlreadySold_returns422() throws Exception {
        String owner = sellerToken();
        long pending = createListing(owner);
        long sold = liveListing(owner);
        markSold(owner, sold, ASKING_PRICE, null).andExpect(status().isOk());

        markSold(owner, pending, ASKING_PRICE, null).andExpect(status().isUnprocessableContent());
        markSold(owner, sold, ASKING_PRICE, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void saleEndpoints_byNonOwner_return403() throws Exception {
        String owner = sellerToken();
        String stranger = sellerToken();
        long listingId = liveListing(owner);

        markSold(stranger, listingId, ASKING_PRICE, null).andExpect(status().isForbidden());
        markSold(owner, listingId, ASKING_PRICE, null).andExpect(status().isOk());
        perform(delete(LISTINGS + "/{id}/sale", listingId), stranger, null).andExpect(status().isForbidden());
    }

    @Test
    void relist_soldListing_removesSaleAndPublishesListing() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);
        markSold(owner, listingId, ASKING_PRICE, "Alıcı vazgeçti").andExpect(status().isOk());

        perform(delete(LISTINGS + "/{id}/sale", listingId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sale").value(nullValue()));

        mockMvc.perform(get(LISTINGS + "/{id}", listingId)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "select sale_price is null and sale_note is null and sold_at is null from listings where id = ?",
                Boolean.class, listingId)).isTrue();
    }

    @Test
    void relist_listingNotSold_returns422() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);

        perform(delete(LISTINGS + "/{id}/sale", listingId), owner, null)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void delete_soldListing_returns422AndKeepsIt() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);
        markSold(owner, listingId, ASKING_PRICE, null).andExpect(status().isOk());

        perform(delete(LISTINGS + "/{id}", listingId), owner, null).andExpect(status().isUnprocessableContent());

        assertThat(TestListings.statusOf(jdbcTemplate, listingId)).isEqualTo("SOLD");
    }

    @Test
    void saleConstraint_saleFieldsOnUnsoldListing_isRejectedByDatabase() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update listings set sale_price = 1 where id = ?", listingId))
                .hasMessageContaining("ck_listings_sale");
    }

    private ResultActions markSold(String token, long listingId, Number salePrice, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("salePrice", salePrice);
        body.put("note", note);
        body.put("version", versionOf(listingId));
        return perform(post(LISTINGS + "/{id}/sale", listingId), token, body);
    }

    private long liveListing(String token) throws Exception {
        long listingId = createListing(token);
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    private long createListing(String token) throws Exception {
        String response = perform(post(LISTINGS), token, listingBody(token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private Map<String, Object> listingBody(String token) throws Exception {
        Map<String, Object> vehicle = new HashMap<>();
        vehicle.put("seriesId", jdbcTemplate.queryForObject("select min(id) from vehicle_series", Long.class));
        vehicle.put("modelYear", 2020);
        vehicle.put("kilometer", 45_000);
        vehicle.put("fuelType", "GASOLINE");
        vehicle.put("gearType", "AUTOMATIC");
        vehicle.put("vehicleCondition", "USED");
        vehicle.put("fromWho", "OWNER");
        vehicle.put("hasDamageRecord", false);
        vehicle.put("exchangeable", true);
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Temiz aile aracı");
        body.put("description", "Bakımları yetkili serviste yapıldı.");
        body.put("price", ASKING_PRICE);
        body.put("currency", "TRY");
        body.put("districtId", jdbcTemplate.queryForObject("select min(id) from districts", Long.class));
        body.put("vehicle", vehicle);
        body.put("imageIds", TestImages.uploaded(jdbcTemplate, userIdOf(token), 1));
        return body;
    }

    private long versionOf(long listingId) {
        return jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, listingId);
    }

    // Listing creation needs a verified e-mail, so it is set directly in the database
    private String sellerToken() throws Exception {
        String email = "seller-" + UUID.randomUUID() + "@satmis.com";
        Map<String, String> body = Map.of("email", email, "password", PASSWORD,
                "firstName", "Ayşe", "lastName", "Yılmaz", "phone", "+905321234567");
        String response = perform(post("/api/auth/register"), null, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        return JsonPath.read(response, "$.accessToken");
    }

    private long userIdOf(String token) throws Exception {
        String response = perform(get("/api/users/me"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
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
