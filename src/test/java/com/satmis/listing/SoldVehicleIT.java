package com.satmis.listing;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestImages;
import com.satmis.TestJwtSecret;
import com.satmis.TestListings;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class SoldVehicleIT {

    private static final String LISTINGS = "/api/listings";
    private static final String SOLD_VEHICLES = "/api/sold-vehicles";
    private static final String ADMIN_SOLD_VEHICLES = "/api/admin/sold-vehicles";
    private static final String PASSWORD = "Sifre1234";
    private static final int ASKING_PRICE = 750_000;
    private static final int MAX_QUERIES_PER_PAGE = 4;

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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void search_soldListing_isListedWithBothPricesAndNoSeller() throws Exception {
        String owner = sellerToken();
        String keyword = uniqueKeyword();
        long soldId = soldListing(owner, keyword, 700_000);
        liveListing(owner, keyword, ASKING_PRICE, "TRY");

        mockMvc.perform(get(SOLD_VEHICLES).param("q", keyword).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(soldId))
                .andExpect(jsonPath("$.content[0].listingPrice").value(ASKING_PRICE))
                .andExpect(jsonPath("$.content[0].salePrice").value(700_000))
                .andExpect(jsonPath("$.content[0].soldAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].coverImageUrl").isNotEmpty())
                .andExpect(jsonPath("$.content[0].seller").doesNotExist());
    }

    @Test
    void search_hiddenSaleOrBlockedOwner_isNotListed() throws Exception {
        String owner = sellerToken();
        String blockedOwner = sellerToken();
        String keyword = uniqueKeyword();
        long visible = soldListing(owner, keyword, ASKING_PRICE);
        long hidden = soldListing(owner, keyword, ASKING_PRICE);
        soldListing(blockedOwner, keyword, ASKING_PRICE);
        jdbcTemplate.update("update listings set sale_hidden = true where id = ?", hidden);
        jdbcTemplate.update("update users set enabled = false where id = ?", userIdOf(blockedOwner));

        assertThat(soldIds("q=" + keyword)).containsExactly(visible);
    }

    @Test
    void search_relistedListing_leavesSoldList() throws Exception {
        String owner = sellerToken();
        String keyword = uniqueKeyword();
        long listingId = soldListing(owner, keyword, ASKING_PRICE);

        perform(delete(LISTINGS + "/{id}/sale", listingId), owner, null).andExpect(status().isOk());

        assertThat(soldIds("q=" + keyword)).isEmpty();
    }

    @Test
    void search_priceFilters_useSalePrice() throws Exception {
        String owner = sellerToken();
        String keyword = uniqueKeyword();
        long listingId = soldListing(owner, keyword, 700_000);

        assertThat(soldIds("q=" + keyword + "&maxPrice=710000")).containsExactly(listingId);
        assertThat(soldIds("q=" + keyword + "&minPrice=720000")).isEmpty();
    }

    @Test
    void search_sortedBySalePrice_ordersBySalePriceNotAskingPrice() throws Exception {
        String owner = sellerToken();
        String keyword = uniqueKeyword();
        long cheaperSale = soldListing(owner, keyword, 600_000);
        long dearerSale = soldListing(owner, keyword, 825_000);

        assertThat(soldIds("q=" + keyword + "&sort=PRICE_ASC")).containsExactly(cheaperSale, dearerSale);
        assertThat(soldIds("q=" + keyword + "&sort=PRICE_DESC")).containsExactly(dearerSale, cheaperSale);
        assertThat(soldIds("q=" + keyword)).as("newest sale first").containsExactly(dearerSale, cheaperSale);
    }

    @Test
    void search_invalidSortOrPageSize_returns400() throws Exception {
        mockMvc.perform(get(SOLD_VEHICLES).param("sort", "NAME")).andExpect(status().isBadRequest());
        mockMvc.perform(get(SOLD_VEHICLES).param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    void search_fullPage_usesFixedNumberOfQueries() throws Exception {
        String owner = sellerToken();
        String keyword = uniqueKeyword();
        for (int index = 0; index < 5; index++) {
            soldListing(owner, keyword, ASKING_PRICE);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get(SOLD_VEHICLES).param("q", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));

        // Lower bound guards against a silently disabled statistics collector making this test vacuous
        assertThat(statistics.getPrepareStatementCount()).isBetween(2L, (long) MAX_QUERIES_PER_PAGE);
    }

    @Test
    void summarize_matchingSales_returnsStatsPerCurrency() throws Exception {
        String owner = sellerToken();
        String keyword = uniqueKeyword();
        soldListing(owner, keyword, 600_000);
        soldListing(owner, keyword, 700_000);
        soldListing(owner, keyword, 800_000);
        long euroListing = liveListing(owner, keyword, 20_000, "EUR");
        markSold(owner, euroListing, 19_000).andExpect(status().isOk());
        liveListing(owner, keyword, ASKING_PRICE, "TRY");

        mockMvc.perform(get(SOLD_VEHICLES + "/summary").param("q", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", hasSize(2)))
                .andExpect(jsonPath("$.currencies[0].currency").value("TRY"))
                .andExpect(jsonPath("$.currencies[0].count").value(3))
                .andExpect(jsonPath("$.currencies[0].average").value(700_000))
                .andExpect(jsonPath("$.currencies[0].min").value(600_000))
                .andExpect(jsonPath("$.currencies[0].max").value(800_000))
                .andExpect(jsonPath("$.currencies[1].currency").value("EUR"))
                .andExpect(jsonPath("$.currencies[1].count").value(1))
                .andExpect(jsonPath("$.currencies[1].average").value(19_000));
    }

    @Test
    void summarize_noMatch_returnsEmptyList() throws Exception {
        mockMvc.perform(get(SOLD_VEHICLES + "/summary").param("q", uniqueKeyword()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", hasSize(0)));
    }

    @Test
    void getDetail_soldListing_returnsSaleWithoutSeller() throws Exception {
        String owner = sellerToken();
        long listingId = liveListing(owner, uniqueKeyword(), ASKING_PRICE, "TRY");
        perform(post(LISTINGS + "/{id}/sale", listingId), owner,
                Map.of("salePrice", 720_000, "note", "Pazarlıkla anlaştık", "version", versionOf(listingId)))
                .andExpect(status().isOk());

        mockMvc.perform(get(SOLD_VEHICLES + "/{id}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingPrice").value(ASKING_PRICE))
                .andExpect(jsonPath("$.salePrice").value(720_000))
                .andExpect(jsonPath("$.saleNote").value("Pazarlıkla anlaştık"))
                .andExpect(jsonPath("$.vehicle.modelYear").value(2020))
                .andExpect(jsonPath("$.location.cityName").isNotEmpty())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.seller").doesNotExist());
    }

    @Test
    void getDetail_notPubliclySold_returns404() throws Exception {
        String owner = sellerToken();
        long live = liveListing(owner, uniqueKeyword(), ASKING_PRICE, "TRY");
        long hidden = soldListing(owner, uniqueKeyword(), ASKING_PRICE);
        jdbcTemplate.update("update listings set sale_hidden = true where id = ?", hidden);

        mockMvc.perform(get(SOLD_VEHICLES + "/{id}", live)).andExpect(status().isNotFound());
        mockMvc.perform(get(SOLD_VEHICLES + "/{id}", hidden)).andExpect(status().isNotFound());
        mockMvc.perform(get(SOLD_VEHICLES + "/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void hide_byAdmin_removesSaleFromPageButKeepsListingSold() throws Exception {
        String admin = adminToken();
        String owner = sellerToken();
        String keyword = uniqueKeyword();
        long listingId = soldListing(owner, keyword, ASKING_PRICE);

        perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.sale.hidden").value(true));
        perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), admin, null).andExpect(status().isOk());

        assertThat(soldIds("q=" + keyword)).isEmpty();
        mockMvc.perform(get(SOLD_VEHICLES + "/summary").param("q", keyword))
                .andExpect(jsonPath("$.currencies", hasSize(0)));
        perform(get(LISTINGS + "/{id}", listingId), owner, null)
                .andExpect(jsonPath("$.sale.hidden").value(true));
    }

    @Test
    void hide_listingNotSoldOrMissing_returns422Or404() throws Exception {
        String admin = adminToken();
        long live = liveListing(sellerToken(), uniqueKeyword(), ASKING_PRICE, "TRY");

        perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", live), admin, null)
                .andExpect(status().isUnprocessableContent());
        perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", Long.MAX_VALUE), admin, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void hide_byNonAdmin_returns403() throws Exception {
        String owner = sellerToken();
        long listingId = soldListing(owner, uniqueKeyword(), ASKING_PRICE);

        perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), owner, null).andExpect(status().isForbidden());
        perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), null, null)
                .andExpect(status().isUnauthorized());
    }

    // Authorities are reloaded from the database on every request, so the token works as admin right away
    private String adminToken() throws Exception {
        String token = sellerToken();
        jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", userIdOf(token));
        return token;
    }

    private List<Long> soldIds(String query) throws Exception {
        String response = mockMvc.perform(get(SOLD_VEHICLES + "?size=50&" + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].id").stream().map(Number::longValue).toList();
    }

    private long soldListing(String token, String keyword, int salePrice) throws Exception {
        long listingId = liveListing(token, keyword, ASKING_PRICE, "TRY");
        markSold(token, listingId, salePrice).andExpect(status().isOk());
        return listingId;
    }

    private ResultActions markSold(String token, long listingId, int salePrice) throws Exception {
        return perform(post(LISTINGS + "/{id}/sale", listingId), token,
                Map.of("salePrice", salePrice, "version", versionOf(listingId)));
    }

    private long liveListing(String token, String keyword, int price, String currency) throws Exception {
        String response = perform(post(LISTINGS), token, listingBody(token, keyword, price, currency))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long listingId = JsonPath.<Number>read(response, "$.id").longValue();
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    private Map<String, Object> listingBody(String token, String keyword, int price, String currency)
            throws Exception {
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
        body.put("title", "Temiz aile aracı " + keyword);
        body.put("description", "Bakımları yetkili serviste yapıldı.");
        body.put("price", price);
        body.put("currency", currency);
        body.put("districtId", jdbcTemplate.queryForObject("select min(id) from districts", Long.class));
        body.put("vehicle", vehicle);
        body.put("imageIds", TestImages.uploaded(jdbcTemplate, userIdOf(token), 1));
        return body;
    }

    // Letters only, so the same keyword never matches listings created by other tests
    private static String uniqueKeyword() {
        return "kw" + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
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
