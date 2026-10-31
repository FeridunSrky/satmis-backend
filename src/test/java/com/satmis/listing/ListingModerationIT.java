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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
class ListingModerationIT {

    private static final String LISTINGS = "/api/listings";
    private static final String ADMIN_LISTINGS = "/api/admin/listings";
    private static final String PASSWORD = "Sifre1234";

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
    void create_newListing_isHiddenUntilAnAdminApprovesIt() throws Exception {
        String admin = adminToken();
        String owner = sellerToken();
        long listingId = createListing(owner, listingBody(owner));

        perform(get(LISTINGS + "/{id}", listingId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        mockMvc.perform(get(LISTINGS + "/{id}", listingId)).andExpect(status().isNotFound());
        assertThat(searchIds()).doesNotContain(listingId);
        assertThat(queueIds(admin, "")).contains(listingId);

        perform(post(ADMIN_LISTINGS + "/{id}/approve", listingId), admin, Map.of("version", versionOf(listingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.seller.phone").isNotEmpty());

        mockMvc.perform(get(LISTINGS + "/{id}", listingId)).andExpect(status().isOk());
        assertThat(searchIds()).contains(listingId);
        assertThat(queueIds(admin, "")).doesNotContain(listingId);
    }

    @Test
    void reject_pendingListing_ownerSeesReasonAndAnyEditResubmits() throws Exception {
        String admin = adminToken();
        String owner = sellerToken();
        Map<String, Object> body = listingBody(owner);
        long listingId = createListing(owner, body);

        perform(post(ADMIN_LISTINGS + "/{id}/reject", listingId), admin,
                Map.of("version", versionOf(listingId), "reason", "  Görseller araca ait değil  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Görseller araca ait değil"));

        perform(get(LISTINGS + "/{id}", listingId), owner, null)
                .andExpect(jsonPath("$.rejectionReason").value("Görseller araca ait değil"));
        perform(get(LISTINGS + "/{id}", listingId), sellerToken(), null).andExpect(status().isNotFound());
        assertThat(queueIds(admin, "?status=REJECTED")).contains(listingId);
        perform(patch(LISTINGS + "/{id}/status", listingId), owner, Map.of("status", "ACTIVE"))
                .andExpect(status().isUnprocessableContent());

        // Even a detail outside the reviewed content sends a rejected listing back to the queue
        vehicleOf(body).put("kilometer", 46_000);
        update(owner, listingId, body)
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.rejectionReason").value(nullValue()));
    }

    @Test
    void update_liveListing_onlyReviewedContentGoesBackToApproval() throws Exception {
        String owner = sellerToken();
        Map<String, Object> body = listingBody(owner, 2);
        long listingId = createListing(owner, body);
        TestListings.approve(jdbcTemplate, listingId);

        vehicleOf(body).put("kilometer", 46_000);
        body.put("imageIds", reversed(imageIdsOf(body)));
        update(owner, listingId, body).andExpect(jsonPath("$.status").value("ACTIVE"));

        body.put("price", 760_000);
        update(owner, listingId, body).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertThat(searchIds()).doesNotContain(listingId);

        TestListings.approve(jdbcTemplate, listingId);
        changeStatus(owner, listingId, "PASSIVE").andExpect(status().isOk());
        body.put("imageIds", List.of(imageIdsOf(body).getFirst()));
        update(owner, listingId, body).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        TestListings.approve(jdbcTemplate, listingId);
        body.put("title", "Yeni başlıklı ilan");
        update(owner, listingId, body).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void approveAndReject_staleVersionWrongStatusOrInvalidBody_areRejected() throws Exception {
        String admin = adminToken();
        String owner = sellerToken();
        long listingId = createListing(owner, listingBody(owner));
        long reviewedVersion = versionOf(listingId);
        jdbcTemplate.update("update listings set version = version + 1 where id = ?", listingId);

        perform(post(ADMIN_LISTINGS + "/{id}/approve", listingId), admin, Map.of("version", reviewedVersion))
                .andExpect(status().isConflict());
        perform(post(ADMIN_LISTINGS + "/{id}/reject", listingId), admin, Map.of("version", versionOf(listingId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").isNotEmpty());
        perform(post(ADMIN_LISTINGS + "/{id}/approve", listingId), admin, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").isNotEmpty());

        TestListings.approve(jdbcTemplate, listingId);
        perform(post(ADMIN_LISTINGS + "/{id}/approve", listingId), admin, Map.of("version", versionOf(listingId)))
                .andExpect(status().isUnprocessableContent());
        // Rejecting a live listing takes it down
        perform(post(ADMIN_LISTINGS + "/{id}/reject", listingId), admin,
                Map.of("version", versionOf(listingId), "reason", "Kurallara aykırı içerik"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        perform(post(ADMIN_LISTINGS + "/{id}/reject", listingId), admin,
                Map.of("version", versionOf(listingId), "reason", "Tekrar"))
                .andExpect(status().isUnprocessableContent());
        perform(post(ADMIN_LISTINGS + "/{id}/approve", 99_999_999), admin, Map.of("version", 0))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminListingEndpoints_forNonAdmins_return401Or403AndBadStatusIs400() throws Exception {
        String owner = sellerToken();
        long listingId = createListing(owner, listingBody(owner));

        mockMvc.perform(get(ADMIN_LISTINGS)).andExpect(status().isUnauthorized());
        perform(get(ADMIN_LISTINGS + "/{id}", listingId), owner, null).andExpect(status().isForbidden());
        perform(post(ADMIN_LISTINGS + "/{id}/approve", listingId), owner, Map.of("version", 0))
                .andExpect(status().isForbidden());
        perform(get(ADMIN_LISTINGS + "?status=FOO"), adminToken(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("Geçersiz değer"));
        perform(get(ADMIN_LISTINGS + "/{id}", listingId), adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    private ResultActions update(String token, long listingId, Map<String, Object> body) throws Exception {
        body.put("version", versionOf(listingId));
        return perform(put(LISTINGS + "/{id}", listingId), token, body).andExpect(status().isOk());
    }

    private ResultActions changeStatus(String token, long listingId, String targetStatus) throws Exception {
        return perform(patch(LISTINGS + "/{id}/status", listingId), token, Map.of("status", targetStatus));
    }

    private List<Long> searchIds() throws Exception {
        return idsOf(mockMvc.perform(get(LISTINGS).param("size", "50")).andExpect(status().isOk()));
    }

    private List<Long> queueIds(String admin, String query) throws Exception {
        return idsOf(perform(get(ADMIN_LISTINGS + query + (query.isEmpty() ? "?" : "&") + "size=50"), admin, null)
                .andExpect(status().isOk()));
    }

    private static List<Long> idsOf(ResultActions result) throws Exception {
        String response = result.andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$.content[*].id").stream().map(Number::longValue).toList();
    }

    private Map<String, Object> listingBody(String token) throws Exception {
        return listingBody(token, 1);
    }

    private Map<String, Object> listingBody(String token, int imageCount) throws Exception {
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
        body.put("imageIds", TestImages.uploaded(jdbcTemplate, userIdOf(token), imageCount));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> vehicleOf(Map<String, Object> body) {
        return (Map<String, Object>) body.get("vehicle");
    }

    @SuppressWarnings("unchecked")
    private static List<Long> imageIdsOf(Map<String, Object> body) {
        return (List<Long>) body.get("imageIds");
    }

    private static List<Long> reversed(List<Long> ids) {
        List<Long> copy = new ArrayList<>(ids);
        Collections.reverse(copy);
        return copy;
    }

    private long createListing(String token, Map<String, Object> body) throws Exception {
        String response = perform(post(LISTINGS), token, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
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
