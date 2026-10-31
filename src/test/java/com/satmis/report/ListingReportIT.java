package com.satmis.report;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ListingReportIT {

    private static final String ADMIN_REPORTS = "/api/admin/reports";
    private static final String PASSWORD = "Sifre1234";
    private static final Map<String, Object> FRAUD = Map.of("reason", "FRAUD_SUSPECTED");

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
    void report_visibleListing_returns201AndRepeatWhileOpenIs409() throws Exception {
        long listingId = liveListing(sellerToken());
        String reporter = userToken();

        report(reporter, listingId, Map.of("reason", "WRONG_INFO", "description", "  Kilometre yanlış  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value(listingId))
                .andExpect(jsonPath("$.reason").value("WRONG_INFO"))
                .andExpect(jsonPath("$.description").value("Kilometre yanlış"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        report(reporter, listingId, FRAUD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bu ilanı zaten şikayet ettiniz, inceleniyor"));
        report(userToken(), listingId, FRAUD).andExpect(status().isCreated());
    }

    @Test
    void report_ownHiddenMissingOrInvalid_isRejected() throws Exception {
        String owner = sellerToken();
        long liveListingId = liveListing(owner);
        long pendingListingId = createListing(owner);
        String reporter = userToken();

        report(owner, liveListingId, FRAUD).andExpect(status().isUnprocessableContent());
        report(reporter, pendingListingId, FRAUD).andExpect(status().isNotFound());
        report(reporter, 99_999_999, FRAUD).andExpect(status().isNotFound());
        report(null, liveListingId, FRAUD).andExpect(status().isUnauthorized());
        report(reporter, liveListingId, Map.of("reason", "OTHER", "description", "   "))
                .andExpect(status().isBadRequest());
        report(reporter, liveListingId, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").isNotEmpty());
        report(reporter, liveListingId, Map.of("reason", "FOO")).andExpect(status().isBadRequest());

        report(reporter, liveListingId, Map.of("reason", "OTHER", "description", "Fotoğraflar başka ilandan"))
                .andExpect(status().isCreated());
    }

    @Test
    void getReportedListings_groupsOpenReportsByListing_mostReportedFirst() throws Exception {
        String admin = adminToken();
        long lessReported = liveListing(sellerToken());
        long mostReported = liveListing(sellerToken());
        report(userToken(), lessReported, FRAUD).andExpect(status().isCreated());
        report(userToken(), mostReported, FRAUD).andExpect(status().isCreated());
        report(userToken(), mostReported, FRAUD).andExpect(status().isCreated());
        report(userToken(), mostReported, Map.of("reason", "ALREADY_SOLD")).andExpect(status().isCreated());

        String queue = perform(get(ADMIN_REPORTS).param("size", "50"), admin, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = JsonPath.<List<Number>>read(queue, "$.content[*].listing.id").stream()
                .map(Number::longValue).toList();
        assertThat(ids).contains(mostReported, lessReported);
        assertThat(ids.indexOf(mostReported)).isLessThan(ids.indexOf(lessReported));

        String entry = "$.content[?(@.listing.id == " + mostReported + ")]";
        assertThat(JsonPath.<List<Number>>read(queue, entry + ".openReportCount")).containsExactly(3);
        assertThat(JsonPath.<List<Number>>read(queue, entry + ".reasons.FRAUD_SUSPECTED")).containsExactly(2);
        assertThat(JsonPath.<List<Number>>read(queue, entry + ".reasons.ALREADY_SOLD")).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(queue, entry + ".listing.status")).containsExactly("ACTIVE");
    }

    @Test
    void dismiss_closesOpenReports_andTheUserCanReportAgain() throws Exception {
        String admin = adminToken();
        long listingId = liveListing(sellerToken());
        String reporter = userToken();
        report(reporter, listingId, FRAUD).andExpect(status().isCreated());

        perform(post(ADMIN_REPORTS + "/listings/{id}/dismiss", listingId), admin, null)
                .andExpect(status().isNoContent());

        assertThat(reportedListingIds(admin)).doesNotContain(listingId);
        perform(get(ADMIN_REPORTS + "/listings/{id}", listingId), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("DISMISSED"))
                .andExpect(jsonPath("$.content[0].resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].reporter.email").isNotEmpty());
        assertThat(TestListings.statusOf(jdbcTemplate, listingId)).isEqualTo("ACTIVE");
        perform(post(ADMIN_REPORTS + "/listings/{id}/dismiss", listingId), admin, null)
                .andExpect(status().isUnprocessableContent());

        report(reporter, listingId, FRAUD).andExpect(status().isCreated());
    }

    @Test
    void takeDown_rejectsListingWithReasonAndResolvesItsReports() throws Exception {
        String admin = adminToken();
        long listingId = liveListing(sellerToken());
        report(userToken(), listingId, FRAUD).andExpect(status().isCreated());
        report(userToken(), listingId, FRAUD).andExpect(status().isCreated());
        long reviewedVersion = versionOf(listingId);

        perform(post(ADMIN_REPORTS + "/listings/{id}/take-down", listingId), admin,
                Map.of("version", reviewedVersion + 1, "reason", "Dolandırıcılık şüphesi"))
                .andExpect(status().isConflict());
        assertThat(reportedListingIds(admin)).contains(listingId);

        perform(post(ADMIN_REPORTS + "/listings/{id}/take-down", listingId), admin,
                Map.of("version", reviewedVersion, "reason", "Dolandırıcılık şüphesi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Dolandırıcılık şüphesi"));

        mockMvc.perform(get("/api/listings/{id}", listingId)).andExpect(status().isNotFound());
        assertThat(reportedListingIds(admin)).doesNotContain(listingId);
        perform(get(ADMIN_REPORTS + "/listings/{id}", listingId), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].status").value("ACTION_TAKEN"))
                .andExpect(jsonPath("$.content[1].status").value("ACTION_TAKEN"));
        perform(post(ADMIN_REPORTS + "/listings/{id}/take-down", listingId), admin,
                Map.of("version", versionOf(listingId), "reason", "Tekrar"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void adminReportEndpoints_forNonAdmins_return401Or403() throws Exception {
        String user = userToken();

        mockMvc.perform(get(ADMIN_REPORTS)).andExpect(status().isUnauthorized());
        perform(get(ADMIN_REPORTS), user, null).andExpect(status().isForbidden());
        perform(get(ADMIN_REPORTS + "/listings/{id}", 1), user, null).andExpect(status().isForbidden());
        perform(post(ADMIN_REPORTS + "/listings/{id}/dismiss", 1), user, null).andExpect(status().isForbidden());
        perform(post(ADMIN_REPORTS + "/listings/{id}/take-down", 1), user,
                Map.of("version", 0, "reason", "x")).andExpect(status().isForbidden());
    }

    private ResultActions report(String token, long listingId, Map<String, Object> body) throws Exception {
        return perform(post("/api/listings/{id}/reports", listingId), token, body);
    }

    private List<Long> reportedListingIds(String admin) throws Exception {
        String response = perform(get(ADMIN_REPORTS).param("size", "50"), admin, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].listing.id").stream()
                .map(Number::longValue).toList();
    }

    private long liveListing(String ownerToken) throws Exception {
        long listingId = createListing(ownerToken);
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    private long createListing(String ownerToken) throws Exception {
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
        body.put("price", 750_000);
        body.put("currency", "TRY");
        body.put("districtId", jdbcTemplate.queryForObject("select min(id) from districts", Long.class));
        body.put("vehicle", vehicle);
        body.put("imageIds", TestImages.uploaded(jdbcTemplate, userIdOf(ownerToken), 1));
        String response = perform(post("/api/listings"), ownerToken, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private long versionOf(long listingId) {
        return jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, listingId);
    }

    // Authorities are reloaded from the database on every request, so the token works as admin right away
    private String adminToken() throws Exception {
        String email = uniqueEmail();
        String token = register(email);
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", email);
        return token;
    }

    // Listing creation needs a verified e-mail, so it is set directly in the database
    private String sellerToken() throws Exception {
        String email = uniqueEmail();
        String token = register(email);
        jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        return token;
    }

    private String userToken() throws Exception {
        return register(uniqueEmail());
    }

    private String register(String email) throws Exception {
        Map<String, String> body = Map.of("email", email, "password", PASSWORD,
                "firstName", "Ayşe", "lastName", "Yılmaz", "phone", "+905321234567");
        String response = perform(post("/api/auth/register"), null, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@satmis.com";
    }
}
