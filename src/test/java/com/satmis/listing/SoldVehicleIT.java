package com.satmis.listing;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestApi;
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
import org.springframework.context.annotation.Import;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestApi.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class SoldVehicleIT {

    private static final String LISTINGS = "/api/listings";
    private static final String SOLD_VEHICLES = "/api/sold-vehicles";
    private static final String ADMIN_SOLD_VEHICLES = "/api/admin/sold-vehicles";
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
    private TestApi api;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void search_soldListing_isListedWithBothPricesAndNoSeller() throws Exception {
        String owner = api.verifiedToken();
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
        String owner = api.verifiedToken();
        String blockedOwner = api.verifiedToken();
        String keyword = uniqueKeyword();
        long visible = soldListing(owner, keyword, ASKING_PRICE);
        long hidden = soldListing(owner, keyword, ASKING_PRICE);
        soldListing(blockedOwner, keyword, ASKING_PRICE);
        jdbcTemplate.update("update listings set sale_hidden = true where id = ?", hidden);
        jdbcTemplate.update("update users set enabled = false where id = ?", api.userIdOf(blockedOwner));

        assertThat(soldIds("q=" + keyword)).containsExactly(visible);
    }

    @Test
    void search_relistedListing_leavesSoldList() throws Exception {
        String owner = api.verifiedToken();
        String keyword = uniqueKeyword();
        long listingId = soldListing(owner, keyword, ASKING_PRICE);

        api.perform(delete(LISTINGS + "/{id}/sale", listingId), owner, null).andExpect(status().isOk());

        assertThat(soldIds("q=" + keyword)).isEmpty();
    }

    @Test
    void search_priceFilters_useSalePrice() throws Exception {
        String owner = api.verifiedToken();
        String keyword = uniqueKeyword();
        long listingId = soldListing(owner, keyword, 700_000);

        assertThat(soldIds("q=" + keyword + "&maxPrice=710000")).containsExactly(listingId);
        assertThat(soldIds("q=" + keyword + "&minPrice=720000")).isEmpty();
    }

    @Test
    void search_sortedBySalePrice_ordersBySalePriceNotAskingPrice() throws Exception {
        String owner = api.verifiedToken();
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
        String owner = api.verifiedToken();
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
        String owner = api.verifiedToken();
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
        String owner = api.verifiedToken();
        long listingId = liveListing(owner, uniqueKeyword(), ASKING_PRICE, "TRY");
        api.perform(post(LISTINGS + "/{id}/sale", listingId), owner,
                Map.of("salePrice", 720_000, "note", "Pazarlıkla anlaştık", "version", api.listingVersionOf(listingId)))
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
        String owner = api.verifiedToken();
        long live = liveListing(owner, uniqueKeyword(), ASKING_PRICE, "TRY");
        long hidden = soldListing(owner, uniqueKeyword(), ASKING_PRICE);
        jdbcTemplate.update("update listings set sale_hidden = true where id = ?", hidden);

        mockMvc.perform(get(SOLD_VEHICLES + "/{id}", live)).andExpect(status().isNotFound());
        mockMvc.perform(get(SOLD_VEHICLES + "/{id}", hidden)).andExpect(status().isNotFound());
        mockMvc.perform(get(SOLD_VEHICLES + "/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void hide_byAdmin_removesSaleFromPageButKeepsListingSold() throws Exception {
        String admin = api.adminToken();
        String owner = api.verifiedToken();
        String keyword = uniqueKeyword();
        long listingId = soldListing(owner, keyword, ASKING_PRICE);

        api.perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.sale.hidden").value(true));
        api.perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), admin, null).andExpect(status().isOk());

        assertThat(soldIds("q=" + keyword)).isEmpty();
        mockMvc.perform(get(SOLD_VEHICLES + "/summary").param("q", keyword))
                .andExpect(jsonPath("$.currencies", hasSize(0)));
        api.perform(get(LISTINGS + "/{id}", listingId), owner, null)
                .andExpect(jsonPath("$.sale.hidden").value(true));
    }

    @Test
    void hide_listingNotSoldOrMissing_returns422Or404() throws Exception {
        String admin = api.adminToken();
        long live = liveListing(api.verifiedToken(), uniqueKeyword(), ASKING_PRICE, "TRY");

        api.perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", live), admin, null)
                .andExpect(status().isUnprocessableContent());
        api.perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", Long.MAX_VALUE), admin, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void hide_byNonAdmin_returns403() throws Exception {
        String owner = api.verifiedToken();
        long listingId = soldListing(owner, uniqueKeyword(), ASKING_PRICE);

        api.perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), owner, null).andExpect(status().isForbidden());
        api.perform(post(ADMIN_SOLD_VEHICLES + "/{id}/hide", listingId), null, null)
                .andExpect(status().isUnauthorized());
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
        return api.perform(post(LISTINGS + "/{id}/sale", listingId), token,
                Map.of("salePrice", salePrice, "version", api.listingVersionOf(listingId)));
    }

    private long liveListing(String token, String keyword, int price, String currency) throws Exception {
        Map<String, Object> body = api.listingBody(token);
        body.put("title", "Temiz aile aracı " + keyword);
        body.put("price", price);
        body.put("currency", currency);
        long listingId = api.createListing(token, body);
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    // Letters only, so the same keyword never matches listings created by other tests
    private static String uniqueKeyword() {
        return "kw" + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
    }
}
