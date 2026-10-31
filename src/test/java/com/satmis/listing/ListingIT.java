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
import org.springframework.jdbc.core.ConnectionCallback;
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

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ListingIT {

    private static final String LISTINGS = "/api/listings";
    private static final String PASSWORD = "Sifre1234";
    private static final String SELLER_PHONE = "+905321234567";
    private static final int MAX_QUERIES_PER_SEARCH_PAGE = 4;

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
    void create_validRequest_returns201WithDetail() throws Exception {
        String token = registerUser();
        Map<String, Object> body = withImages(listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy")), token);
        long coverImageId = imageIdsOf(body).getFirst();

        perform(post(LISTINGS), token, body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString(LISTINGS + "/")))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.vehicle.brandName").value("Renault"))
                .andExpect(jsonPath("$.vehicle.modelName").value("Clio"))
                .andExpect(jsonPath("$.location.cityName").value("İstanbul"))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].id").value(coverImageId))
                .andExpect(jsonPath("$.images[0].url").value(publicUrlOf(coverImageId)))
                .andExpect(jsonPath("$.images[0].cover").value(true))
                .andExpect(jsonPath("$.images[1].cover").value(false));
        assertThat(TestImages.statusOf(jdbcTemplate, coverImageId)).isEqualTo("ATTACHED");
    }

    @Test
    void getDetail_reloadedFromDatabase_returnsImagesInDisplayOrder() throws Exception {
        String token = registerUser();
        List<Long> imageIds = TestImages.uploaded(jdbcTemplate, userId(token), 2);
        Map<String, Object> body = listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy"));
        body.put("imageIds", List.of(imageIds.get(1), imageIds.get(0)));
        long listingId = createListing(token, body);

        mockMvc.perform(get(LISTINGS + "/{id}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(imageIds.get(1)))
                .andExpect(jsonPath("$.images[0].url").value(publicUrlOf(imageIds.get(1))))
                .andExpect(jsonPath("$.images[0].cover").value(true))
                .andExpect(jsonPath("$.images[1].id").value(imageIds.get(0)))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void create_invalidBody_returnsFieldErrors() throws Exception {
        Map<String, Object> body = listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy"));
        body.put("title", "");
        body.put("price", -5);
        body.put("imageIds", List.of(1, 1));
        vehicleOf(body).put("modelYear", 1900);

        perform(post(LISTINGS), registerUser(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").isNotEmpty())
                .andExpect(jsonPath("$.errors.price").isNotEmpty())
                .andExpect(jsonPath("$.errors.imageIds").isNotEmpty())
                .andExpect(jsonPath("$.errors['vehicle.modelYear']").isNotEmpty());
    }

    @Test
    void create_unknownSeries_returns422() throws Exception {
        String token = registerUser();
        Map<String, Object> body = withImages(listingBody(Long.MAX_VALUE, district("İstanbul", "Kadıköy")), token);

        perform(post(LISTINGS), token, body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void create_ownerWithUnverifiedEmail_returns422WithEmailNotVerifiedCode() throws Exception {
        String token = registerUser();
        long ownerId = userId(token);
        jdbcTemplate.update("update users set email_verified = false where id = ?", ownerId);
        Map<String, Object> body = withImages(listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy")), token);

        perform(post(LISTINGS), token, body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").value("İlan vermek için e-posta adresinizi doğrulayın"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from listings where user_id = ?", Integer.class,
                ownerId)).isZero();
    }

    @Test
    void create_ownerWithoutPhone_returns422WithPhoneRequiredCode() throws Exception {
        String token = registerUser();
        long ownerId = userId(token);
        jdbcTemplate.update("update users set phone = null where id = ?", ownerId);
        Map<String, Object> body = withImages(listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy")), token);

        perform(post(LISTINGS), token, body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PHONE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("İlan vermek için profilinize telefon numarası ekleyin"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from listings where user_id = ?", Integer.class,
                ownerId)).isZero();
    }

    @Test
    void getDetail_sellerPhone_onlyVisibleToAuthenticatedViewers() throws Exception {
        long listingId = createListing(registerUser(), series("Renault", "Clio", "1.0 TCe"),
                district("İstanbul", "Kadıköy"));

        mockMvc.perform(get(LISTINGS + "/{id}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seller.phone").value(nullValue()))
                .andExpect(jsonPath("$.seller.email").doesNotExist());
        perform(get(LISTINGS + "/{id}", listingId), registerUser(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seller.phone").value(SELLER_PHONE))
                .andExpect(jsonPath("$.seller.email").doesNotExist());
    }

    @Test
    void search_summaries_neverContainSellerPhone() throws Exception {
        createListing(registerUser(), series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy"));

        String response = perform(get(LISTINGS), registerUser(), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(SELLER_PHONE);
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        Map<String, Object> body = listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy"));

        perform(post(LISTINGS), null, body).andExpect(status().isUnauthorized());
    }

    @Test
    void getDetail_passiveListing_hiddenFromOthersButVisibleToOwner() throws Exception {
        String owner = registerUser();
        long listingId = createListing(owner, series("Fiat", "Egea", "1.3 Multijet"), district("Ankara", "Çankaya"));
        changeStatus(owner, listingId, "PASSIVE").andExpect(status().isOk());

        mockMvc.perform(get(LISTINGS + "/{id}", listingId)).andExpect(status().isNotFound());
        perform(get(LISTINGS + "/{id}", listingId), registerUser(), null).andExpect(status().isNotFound());
        perform(get(LISTINGS + "/{id}", listingId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSIVE"));
    }

    @Test
    void getDetail_viewedByOthers_countsViewsExceptOwners() throws Exception {
        String owner = registerUser();
        long listingId = createListing(owner, series("Fiat", "Egea", "1.3 Multijet"), district("Ankara", "Çankaya"));

        mockMvc.perform(get(LISTINGS + "/{id}", listingId)).andExpect(status().isOk());
        perform(get(LISTINGS + "/{id}", listingId), registerUser(), null).andExpect(status().isOk());
        perform(get(LISTINGS + "/{id}", listingId), owner, null).andExpect(status().isOk());

        perform(get(LISTINGS + "/{id}", listingId), owner, null).andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void updateAndDelete_byNonOwner_return403() throws Exception {
        long seriesId = series("Toyota", "Corolla", "1.6");
        long districtId = district("İzmir", "Karşıyaka");
        long listingId = createListing(registerUser(), seriesId, districtId);
        String stranger = registerUser();
        Map<String, Object> body = withImages(listingBody(seriesId, districtId), stranger);
        body.put("version", 0);

        perform(put(LISTINGS + "/{id}", listingId), stranger, body).andExpect(status().isForbidden());
        perform(delete(LISTINGS + "/{id}", listingId), stranger, null).andExpect(status().isForbidden());
        changeStatus(stranger, listingId, "SOLD").andExpect(status().isForbidden());
    }

    @Test
    void create_imageOfAnotherUser_returns422ImageNotAvailable() throws Exception {
        String token = registerUser();
        Map<String, Object> body = listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy"));
        body.put("imageIds", List.of(TestImages.uploaded(jdbcTemplate, userId(registerUser()))));

        perform(post(LISTINGS), token, body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_AVAILABLE"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from listings where user_id = ?", Integer.class,
                userId(token))).isZero();
    }

    @Test
    void create_imageAlreadyInAnotherListingOrNotCompleted_returns422() throws Exception {
        String owner = registerUser();
        long seriesId = series("Renault", "Clio", "1.0 TCe");
        long districtId = district("İstanbul", "Kadıköy");
        long usedImageId = imageIdsOfListing(createListing(owner, seriesId, districtId)).getFirst();
        long pendingImageId = TestImages.uploaded(jdbcTemplate, userId(owner));
        jdbcTemplate.update("update listing_images set status = 'PENDING' where id = ?", pendingImageId);

        for (long imageId : List.of(usedImageId, pendingImageId)) {
            Map<String, Object> body = listingBody(seriesId, districtId);
            body.put("imageIds", List.of(imageId));
            perform(post(LISTINGS), owner, body)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("IMAGE_NOT_AVAILABLE"));
        }
    }

    @Test
    void update_reorderedImages_changesCoverAndKeepsBoth() throws Exception {
        String owner = registerUser();
        long seriesId = series("Toyota", "Corolla", "1.6");
        long districtId = district("İzmir", "Karşıyaka");
        long listingId = createListing(owner, seriesId, districtId);
        List<Long> imageIds = imageIdsOfListing(listingId);

        Map<String, Object> body = listingBody(seriesId, districtId);
        body.put("version", currentVersion(listingId));
        body.put("imageIds", List.of(imageIds.get(1), imageIds.get(0)));
        perform(put(LISTINGS + "/{id}", listingId), owner, body).andExpect(status().isOk());

        mockMvc.perform(get(LISTINGS + "/{id}", listingId))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].id").value(imageIds.get(1)))
                .andExpect(jsonPath("$.images[0].cover").value(true))
                .andExpect(jsonPath("$.images[1].id").value(imageIds.get(0)))
                .andExpect(jsonPath("$.images[1].cover").value(false));
    }

    @Test
    void update_staleVersion_returns409AndEveryEditBumpsVersion() throws Exception {
        String owner = registerUser();
        long seriesId = series("Toyota", "Corolla", "1.6");
        long districtId = district("İzmir", "Karşıyaka");
        long listingId = createListing(owner, seriesId, districtId);
        List<Long> originalImageIds = imageIdsOfListing(listingId);
        long replacementImageId = TestImages.uploaded(jdbcTemplate, userId(owner));

        Map<String, Object> body = listingBody(seriesId, districtId);
        body.put("version", 0);
        body.put("title", "Güncellenmiş başlık");
        body.put("imageIds", List.of(replacementImageId));
        String firstUpdate = perform(put(LISTINGS + "/{id}", listingId), owner, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Güncellenmiş başlık"))
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].id").value(replacementImageId))
                .andReturn().getResponse().getContentAsString();
        long versionAfterFirst = JsonPath.<Number>read(firstUpdate, "$.version").longValue();
        assertThat(versionAfterFirst).isPositive();
        assertThat(originalImageIds).allSatisfy(imageId ->
                assertThat(TestImages.statusOf(jdbcTemplate, imageId)).isEqualTo("DELETED"));

        perform(put(LISTINGS + "/{id}", listingId), owner, body).andExpect(status().isConflict());

        // Only images change here: the listing row itself is untouched but its version must still move
        body.put("version", versionAfterFirst);
        body.put("imageIds", List.of(TestImages.uploaded(jdbcTemplate, userId(owner)), replacementImageId));
        String secondUpdate = perform(put(LISTINGS + "/{id}", listingId), owner, body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(secondUpdate, "$.version").longValue()).isGreaterThan(versionAfterFirst);
    }

    @Test
    void update_missingVersion_returns400() throws Exception {
        String owner = registerUser();
        long seriesId = series("Toyota", "Corolla", "1.6");
        long districtId = district("İzmir", "Karşıyaka");
        long listingId = createListing(owner, seriesId, districtId);

        perform(put(LISTINGS + "/{id}", listingId), owner, listingBody(seriesId, districtId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").isNotEmpty());
    }

    @Test
    void changeStatus_ownerTransitions_followRules() throws Exception {
        String owner = registerUser();
        long seriesId = series("Hyundai", "i20", "1.4 MPI");
        long districtId = district("Bursa", "Nilüfer");
        long listingId = createListing(owner, seriesId, districtId);

        changeStatus(owner, listingId, "PENDING_APPROVAL").andExpect(status().isUnprocessableContent());
        changeStatus(owner, listingId, "PASSIVE").andExpect(status().isOk());
        changeStatus(owner, listingId, "ACTIVE").andExpect(status().isOk());
        changeStatus(owner, listingId, "SOLD").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));
        changeStatus(owner, listingId, "ACTIVE").andExpect(status().isUnprocessableContent());

        Map<String, Object> body = withImages(listingBody(seriesId, districtId), owner);
        body.put("version", currentVersion(listingId));
        perform(put(LISTINGS + "/{id}", listingId), owner, body).andExpect(status().isUnprocessableContent());
    }

    @Test
    void delete_ownListing_removesDetailImagesAndFavorites() throws Exception {
        String owner = registerUser();
        long listingId = createListing(owner, series("Hyundai", "i20", "1.4 MPI"), district("Bursa", "Nilüfer"));
        List<Long> imageIds = imageIdsOfListing(listingId);
        long favoritingUserId = userId(registerUser());
        jdbcTemplate.update("insert into favorites (user_id, listing_id, created_at) values (?, ?, now())",
                favoritingUserId, listingId);

        perform(delete(LISTINGS + "/{id}", listingId), owner, null).andExpect(status().isNoContent());

        mockMvc.perform(get(LISTINGS + "/{id}", listingId)).andExpect(status().isNotFound());
        for (String table : List.of("vehicle_details", "listing_images", "favorites")) {
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from " + table + " where listing_id = ?", Integer.class, listingId))
                    .as(table).isZero();
        }
        // Image rows stay until the cleanup removes their stored files
        assertThat(imageIds).isNotEmpty().allSatisfy(imageId ->
                assertThat(TestImages.statusOf(jdbcTemplate, imageId)).isEqualTo("DELETED"));
    }

    @Test
    void search_withFilters_returnsOnlyMatchingActiveListings() throws Exception {
        String owner = registerUser();
        long seriesId = series("Volkswagen", "Golf", "1.5 eTSI");
        long districtId = district("Antalya", "Muratpaşa");
        long newCar = createListing(owner, withVehicle(listingBody(seriesId, districtId), 2022, 30_000));
        long oldCar = createListing(owner, withVehicle(listingBody(seriesId, districtId), 2015, 150_000));
        long passiveCar = createListing(owner, withVehicle(listingBody(seriesId, districtId), 2022, 10_000));
        changeStatus(owner, passiveCar, "PASSIVE");

        String base = "seriesId=" + seriesId + "&districtId=" + districtId;
        assertThat(searchIds(base)).containsExactlyInAnyOrder(newCar, oldCar);
        assertThat(searchIds(base + "&maxKm=100000")).containsExactly(newCar);
        assertThat(searchIds(base + "&minYear=2018&fuelType=GASOLINE")).containsExactly(newCar);
        assertThat(searchIds("brandId=" + brandOf(seriesId) + "&cityId=" + cityOf(districtId)))
                .contains(newCar, oldCar).doesNotContain(passiveCar);
        assertThat(searchIds(base + "&currency=EUR")).isEmpty();
    }

    @Test
    void search_sortedByPrice_ordersAndPages() throws Exception {
        String owner = registerUser();
        long seriesId = series("Honda", "City", "1.5 i-VTEC");
        long districtId = district("Sinop", "Boyabat");
        long expensive = createListing(owner, withPrice(listingBody(seriesId, districtId), 300_000));
        long cheap = createListing(owner, withPrice(listingBody(seriesId, districtId), 100_000));
        long middle = createListing(owner, withPrice(listingBody(seriesId, districtId), 200_000));
        String filter = "seriesId=" + seriesId + "&sort=PRICE_ASC&size=2";

        mockMvc.perform(get(LISTINGS + "?" + filter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].coverImageUrl").value(publicUrlOf(imageIdsOfListing(cheap).getFirst())));
        assertThat(searchIds(filter)).containsExactly(cheap, middle);
        assertThat(searchIds(filter + "&page=1")).containsExactly(expensive);
    }

    @Test
    void search_invalidPagingOrSort_returns400() throws Exception {
        mockMvc.perform(get(LISTINGS + "?size=100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").isNotEmpty());
        mockMvc.perform(get(LISTINGS + "?sort=FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort").value("Geçersiz değer"));
    }

    @Test
    void search_fullPage_usesFixedNumberOfQueries() throws Exception {
        String owner = registerUser();
        long seriesId = series("Opel", "Astra", "1.4 Turbo");
        long districtId = district("Konya", "Selçuklu");
        for (int index = 0; index < 5; index++) {
            createListing(owner, seriesId, districtId);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get(LISTINGS + "?seriesId=" + seriesId + "&q=astra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));

        // Lower bound guards against a silently disabled statistics collector making this test vacuous
        assertThat(statistics.getPrepareStatementCount()).isBetween(2L, (long) MAX_QUERIES_PER_SEARCH_PAGE);
    }

    @Test
    void search_keyword_ignoresCaseAndTurkishCharacters() throws Exception {
        String owner = registerUser();
        String marker = uniqueKeyword();
        Map<String, Object> body = listingBody(series("Toyota", "Corolla", "1.6"), district("İzmir", "Karşıyaka"));
        body.put("title", "IŞIKLI " + marker + " ÇAĞDAŞ donanım");
        long listingId = createListing(owner, body);

        assertThat(keywordSearchIds("ışıklı " + marker)).containsExactly(listingId);
        assertThat(keywordSearchIds("isikli " + marker.toUpperCase(Locale.ROOT))).containsExactly(listingId);
        assertThat(keywordSearchIds("cagdas " + marker)).containsExactly(listingId);
    }

    @Test
    void search_keyword_matchesCatalogNamesByPrefixAndRequiresEveryTerm() throws Exception {
        String owner = registerUser();
        String marker = uniqueKeyword();
        long seriesId = series("Volkswagen", "Golf", "1.5 eTSI");
        Map<String, Object> body = withVehicle(listingBody(seriesId, district("Konya", "Selçuklu")), 2022, 30_000);
        body.put("description", "Sahibinden " + marker + " değişensiz");
        long listingId = createListing(owner, body);

        assertThat(keywordSearchIds("golf " + marker)).containsExactly(listingId);
        assertThat(keywordSearchIds("volks deği " + marker)).containsExactly(listingId);
        assertThat(keywordSearchIds("passat " + marker)).isEmpty();
        assertThat(searchIds("q=" + marker + "&seriesId=" + seriesId + "&maxKm=50000")).containsExactly(listingId);
        assertThat(searchIds("q=" + marker + "&maxKm=10000")).isEmpty();
    }

    @Test
    void search_keywordWithQuerySyntax_isTreatedAsPlainText() throws Exception {
        String owner = registerUser();
        String marker = uniqueKeyword();
        Map<String, Object> body = listingBody(series("Renault", "Clio", "1.0 TCe"), district("İstanbul", "Kadıköy"));
        body.put("title", "Temiz " + marker);
        long listingId = createListing(owner, body);

        assertThat(keywordSearchIds(marker + ":* | !(temiz) & '")).containsExactly(listingId);
        // Nothing searchable left: the keyword filter is skipped instead of failing
        mockMvc.perform(get(LISTINGS).param("q", "&|!():*'\\"))
                .andExpect(status().isOk());
    }

    @Test
    void search_keywordTooLong_returns400() throws Exception {
        mockMvc.perform(get(LISTINGS).param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").isNotEmpty());
    }

    @Test
    void update_changedTitle_isFoundByNewKeyword() throws Exception {
        String owner = registerUser();
        String oldMarker = uniqueKeyword();
        String newMarker = uniqueKeyword();
        long seriesId = series("Toyota", "Corolla", "1.6");
        long districtId = district("İzmir", "Karşıyaka");
        Map<String, Object> body = listingBody(seriesId, districtId);
        body.put("title", "İlan " + oldMarker);
        long listingId = createListing(owner, body);

        body.put("title", "İlan " + newMarker);
        body.put("version", currentVersion(listingId));
        body.put("imageIds", imageIdsOfListing(listingId));
        perform(put(LISTINGS + "/{id}", listingId), owner, body).andExpect(status().isOk());
        // A new title goes back to approval; publish it to check the search text followed the edit
        TestListings.approve(jdbcTemplate, listingId);

        assertThat(keywordSearchIds(newMarker)).containsExactly(listingId);
        assertThat(keywordSearchIds(oldMarker)).isEmpty();
    }

    @Test
    void search_keywordPredicate_usesGinIndex() {
        String predicate = ListingSearchFunctions.TEXT_MATCHES_SQL
                .replace("?1", "search_text").replace("?2", "'golf:*'");
        // The tiny test table would always be scanned sequentially; disabling that shows whether the index fits
        List<String> plan = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set enable_seqscan = off");
                List<String> lines = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery(
                        "explain select id from listings where " + predicate + " = true")) {
                    while (rows.next()) {
                        lines.add(rows.getString(1));
                    }
                }
                statement.execute("reset enable_seqscan");
                return lines;
            }
        });
        assertThat(String.join("\n", plan)).contains("idx_listings_search");
    }

    @Test
    void getMyListings_includesEveryStatus() throws Exception {
        String owner = registerUser();
        long seriesId = series("Peugeot", "208", "1.2 PureTech");
        long districtId = district("Eskişehir", "Tepebaşı");
        long active = createListing(owner, seriesId, districtId);
        long sold = createListing(owner, seriesId, districtId);
        changeStatus(owner, sold, "SOLD");
        createListing(registerUser(), seriesId, districtId);

        String body = perform(get("/api/users/me/listings"), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Number>>read(body, "$.content[*].id"))
                .extracting(Number::longValue).containsExactlyInAnyOrder(active, sold);
    }

    private Map<String, Object> listingBody(long seriesId, long districtId) {
        Map<String, Object> vehicle = new HashMap<>();
        vehicle.put("seriesId", seriesId);
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
        body.put("price", 750_000);
        body.put("currency", "TRY");
        body.put("districtId", districtId);
        body.put("vehicle", vehicle);
        return body;
    }

    // Listings need the owner's uploaded images; createListing adds fresh ones unless the body names its own
    private Map<String, Object> withImages(Map<String, Object> body, String token) throws Exception {
        body.put("imageIds", TestImages.uploaded(jdbcTemplate, userId(token), 2));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> imageIdsOf(Map<String, Object> body) {
        return (List<Long>) body.get("imageIds");
    }

    // In display order, so the first one is the cover
    private List<Long> imageIdsOfListing(long listingId) {
        return jdbcTemplate.queryForList("select id from listing_images where listing_id = ? order by display_order",
                Long.class, listingId);
    }

    // Matches the test profile's app.storage.public-base-url
    private String publicUrlOf(long imageId) {
        return "http://localhost:9/satmis-images/" + TestImages.objectKeyOf(jdbcTemplate, imageId);
    }

    private static Map<String, Object> withVehicle(Map<String, Object> body, int modelYear, int kilometer) {
        vehicleOf(body).put("modelYear", modelYear);
        vehicleOf(body).put("kilometer", kilometer);
        return body;
    }

    private static Map<String, Object> withPrice(Map<String, Object> body, int price) {
        body.put("price", price);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> vehicleOf(Map<String, Object> body) {
        return (Map<String, Object>) body.get("vehicle");
    }

    private long createListing(String token, long seriesId, long districtId) throws Exception {
        return createListing(token, listingBody(seriesId, districtId));
    }

    // Published right away: most tests here are about live listings, approval is covered by ListingModerationIT
    private long createListing(String token, Map<String, Object> body) throws Exception {
        if (!body.containsKey("imageIds")) {
            withImages(body, token);
        }
        String response = perform(post(LISTINGS), token, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long listingId = JsonPath.<Number>read(response, "$.id").longValue();
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    private ResultActions changeStatus(String token, long listingId, String targetStatus) throws Exception {
        return perform(patch(LISTINGS + "/{id}/status", listingId), token, Map.of("status", targetStatus));
    }

    private List<Long> searchIds(String query) throws Exception {
        String response = mockMvc.perform(get(LISTINGS + "?" + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].id").stream().map(Number::longValue).toList();
    }

    private List<Long> keywordSearchIds(String keyword) throws Exception {
        String response = mockMvc.perform(get(LISTINGS).param("q", keyword))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].id").stream().map(Number::longValue).toList();
    }

    // Letters only, so the same keyword never matches listings created by other tests
    private static String uniqueKeyword() {
        return "kw" + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
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

    // Listing creation needs a verified e-mail, so the helper verifies it directly in the database
    private String registerUser() throws Exception {
        String email = "seller-" + UUID.randomUUID() + "@satmis.com";
        Map<String, String> body = Map.of("email", email,
                "password", PASSWORD, "firstName", "Ayşe", "lastName", "Yılmaz", "phone", SELLER_PHONE);
        String response = perform(post("/api/auth/register"), null, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        return JsonPath.read(response, "$.accessToken");
    }

    private long userId(String token) throws Exception {
        String response = perform(get("/api/users/me"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private long series(String brand, String model, String series) {
        return jdbcTemplate.queryForObject("""
                select s.id from vehicle_series s
                join vehicle_models m on m.id = s.model_id
                join brands b on b.id = m.brand_id
                where b.name = ? and m.name = ? and s.name = ?""", Long.class, brand, model, series);
    }

    private long district(String city, String district) {
        return jdbcTemplate.queryForObject("""
                select d.id from districts d join cities c on c.id = d.city_id
                where c.name = ? and d.name = ?""", Long.class, city, district);
    }

    private long brandOf(long seriesId) {
        return jdbcTemplate.queryForObject("""
                select m.brand_id from vehicle_series s join vehicle_models m on m.id = s.model_id
                where s.id = ?""", Long.class, seriesId);
    }

    private long cityOf(long districtId) {
        return jdbcTemplate.queryForObject("select city_id from districts where id = ?", Long.class, districtId);
    }

    private long currentVersion(long listingId) {
        return jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, listingId);
    }
}
