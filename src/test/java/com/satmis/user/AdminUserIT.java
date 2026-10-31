package com.satmis.user;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestImages;
import com.satmis.TestJwtSecret;
import com.satmis.TestListings;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.admin.emails= Boss@Satmis.com ,, nobody-registered@satmis.com")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class AdminUserIT {

    private static final String ADMIN_USERS = "/api/admin/users";
    private static final String PASSWORD = "Sifre1234";
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String REFRESH_COOKIE = "satmis_refresh";

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
    private AdminUserService adminUserService;

    @Test
    void promoteConfiguredAdmins_registeredConfiguredEmail_becomesAdmin() throws Exception {
        String token = accessToken(register("boss@satmis.com"));
        String bystander = uniqueEmail();
        register(bystander);

        adminUserService.promoteConfiguredAdmins();
        adminUserService.promoteConfiguredAdmins();

        perform(get("/api/users/me"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(roleOf(bystander)).isEqualTo("USER");
    }

    @Test
    void adminEndpoints_withoutAdminRole_return401Or403() throws Exception {
        String userToken = accessToken(register(uniqueEmail()));

        mockMvc.perform(get(ADMIN_USERS)).andExpect(status().isUnauthorized());
        perform(get(ADMIN_USERS), userToken, null).andExpect(status().isForbidden());
        perform(post(ADMIN_USERS + "/{id}/block", 1), userToken, null).andExpect(status().isForbidden());
    }

    @Test
    void search_asAdmin_filtersByEmailAndCountsListings() throws Exception {
        String admin = adminToken();
        String sellerEmail = uniqueEmail();
        String seller = verifiedSellerToken(sellerEmail);
        createListing(seller);
        createListing(seller);

        perform(get(ADMIN_USERS).param("q", sellerEmail.substring(0, 20).toUpperCase()), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(sellerEmail))
                .andExpect(jsonPath("$.content[0].listingCount").value(2))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
        perform(get(ADMIN_USERS).param("q", "%"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        perform(get(ADMIN_USERS).param("q", "a".repeat(101)), admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").isNotEmpty());
    }

    @Test
    void block_seller_hidesListingsAndEndsSessionsUntilUnblocked() throws Exception {
        String admin = adminToken();
        String sellerEmail = uniqueEmail();
        MvcResult sellerLogin = login(verifiedSellerEmail(sellerEmail));
        String seller = accessToken(sellerLogin);
        long listingId = createListing(seller);
        String viewer = accessToken(register(uniqueEmail()));
        perform(put("/api/listings/{id}/favorite", listingId), viewer, null).andExpect(status().isNoContent());
        long sellerId = userIdOf(sellerEmail);

        perform(post(ADMIN_USERS + "/{id}/block", sellerId), admin, null).andExpect(status().isNoContent());

        perform(get("/api/users/me"), seller, null).andExpect(status().isUnauthorized());
        refreshWithCookie(sellerLogin).andExpect(status().isUnauthorized());
        login(sellerEmail, status().isForbidden());
        assertListingVisible(listingId, viewer, false);
        perform(put("/api/listings/{id}/favorite", listingId), viewer, null).andExpect(status().isNotFound());

        perform(post(ADMIN_USERS + "/{id}/unblock", sellerId), admin, null).andExpect(status().isNoContent());

        login(sellerEmail, status().isOk());
        assertListingVisible(listingId, viewer, true);
    }

    @Test
    void block_adminSelfOrUnknownUser_isRejected() throws Exception {
        String adminEmail = uniqueEmail();
        String admin = adminToken(adminEmail);
        String otherAdminEmail = uniqueEmail();
        adminToken(otherAdminEmail);

        perform(post(ADMIN_USERS + "/{id}/block", userIdOf(adminEmail)), admin, null)
                .andExpect(status().isUnprocessableContent());
        perform(post(ADMIN_USERS + "/{id}/block", userIdOf(otherAdminEmail)), admin, null)
                .andExpect(status().isUnprocessableContent());
        perform(post(ADMIN_USERS + "/{id}/block", 99_999_999), admin, null).andExpect(status().isNotFound());
        perform(post(ADMIN_USERS + "/{id}/unblock", 99_999_999), admin, null).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select enabled from users where email = ?", Boolean.class,
                otherAdminEmail)).isTrue();
    }

    private void assertListingVisible(long listingId, String viewer, boolean visible) throws Exception {
        mockMvc.perform(get("/api/listings/{id}", listingId))
                .andExpect(visible ? status().isOk() : status().isNotFound());
        List<Long> searchIds = idsOf(mockMvc.perform(get("/api/listings").param("size", "50"))
                .andExpect(status().isOk()).andReturn());
        List<Long> favoriteIds = idsOf(perform(get("/api/users/me/favorites"), viewer, null)
                .andExpect(status().isOk()).andReturn());
        assertThat(searchIds.contains(listingId)).isEqualTo(visible);
        assertThat(favoriteIds.contains(listingId)).isEqualTo(visible);
    }

    private static List<Long> idsOf(MvcResult result) throws Exception {
        return JsonPath.<List<Number>>read(result.getResponse().getContentAsString(), "$.content[*].id").stream()
                .map(Number::longValue).toList();
    }

    private String adminToken() throws Exception {
        return adminToken(uniqueEmail());
    }

    // Authorities are reloaded from the database on every request, so the token works as admin right away
    private String adminToken(String email) throws Exception {
        String token = accessToken(register(email));
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", email);
        return token;
    }

    private String verifiedSellerToken(String email) throws Exception {
        return accessToken(login(verifiedSellerEmail(email)));
    }

    // Listing creation needs a verified e-mail, so it is set directly in the database
    private String verifiedSellerEmail(String email) throws Exception {
        register(email);
        jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        return email;
    }

    private long createListing(String token) throws Exception {
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
        body.put("price", 750_000);
        body.put("currency", "TRY");
        body.put("districtId", jdbcTemplate.queryForObject("select min(id) from districts", Long.class));
        body.put("vehicle", vehicle);
        body.put("imageIds", List.of(TestImages.uploaded(jdbcTemplate, userIdOfToken(token))));

        String response = perform(post("/api/listings"), token, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long listingId = JsonPath.<Number>read(response, "$.id").longValue();
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    private MvcResult register(String email) throws Exception {
        Map<String, String> body = Map.of("email", email, "password", PASSWORD,
                "firstName", "Ayşe", "lastName", "Yılmaz", "phone", "+905321234567");
        return perform(post("/api/auth/register"), null, body).andExpect(status().isCreated()).andReturn();
    }

    private MvcResult login(String email) throws Exception {
        return login(email, status().isOk());
    }

    private MvcResult login(String email, ResultMatcher expected)
            throws Exception {
        return perform(post("/api/auth/login"), null, Map.of("email", email, "password", PASSWORD))
                .andExpect(expected).andReturn();
    }

    private ResultActions refreshWithCookie(MvcResult loginResult) throws Exception {
        Cookie cookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN));
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
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

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long userIdOfToken(String token) throws Exception {
        String response = perform(get("/api/users/me"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private String roleOf(String email) {
        return jdbcTemplate.queryForObject("select role from users where email = ?", String.class, email);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@satmis.com";
    }
}
