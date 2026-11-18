package com.satmis.user;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestApi;
import com.satmis.TestJwtSecret;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestApi.class)
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
    private TestApi api;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminUserService adminUserService;

    @Test
    void promoteConfiguredAdmins_registeredConfiguredEmail_becomesAdmin() throws Exception {
        String token = accessToken(register("boss@satmis.com"));
        String bystander = TestApi.uniqueEmail();
        register(bystander);

        adminUserService.promoteConfiguredAdmins();
        adminUserService.promoteConfiguredAdmins();

        api.perform(get("/api/users/me"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(roleOf(bystander)).isEqualTo("USER");
    }

    @Test
    void adminEndpoints_withoutAdminRole_return401Or403() throws Exception {
        String userToken = accessToken(register(TestApi.uniqueEmail()));

        mockMvc.perform(get(ADMIN_USERS)).andExpect(status().isUnauthorized());
        api.perform(get(ADMIN_USERS), userToken, null).andExpect(status().isForbidden());
        api.perform(post(ADMIN_USERS + "/{id}/block", 1), userToken, null).andExpect(status().isForbidden());
    }

    @Test
    void search_asAdmin_filtersByEmailAndCountsListings() throws Exception {
        String admin = adminToken();
        String sellerEmail = TestApi.uniqueEmail();
        String seller = verifiedSellerToken(sellerEmail);
        api.liveListing(seller);
        api.liveListing(seller);

        api.perform(get(ADMIN_USERS).param("q", sellerEmail.substring(0, 20).toUpperCase()), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(sellerEmail))
                .andExpect(jsonPath("$.content[0].listingCount").value(2))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
        api.perform(get(ADMIN_USERS).param("q", "%"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        api.perform(get(ADMIN_USERS).param("q", "a".repeat(101)), admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").isNotEmpty());
    }

    @Test
    void block_seller_hidesListingsAndEndsSessionsUntilUnblocked() throws Exception {
        String admin = adminToken();
        String sellerEmail = TestApi.uniqueEmail();
        MvcResult sellerLogin = login(verifiedSellerEmail(sellerEmail));
        String seller = accessToken(sellerLogin);
        long listingId = api.liveListing(seller);
        String viewer = accessToken(register(TestApi.uniqueEmail()));
        api.perform(put("/api/listings/{id}/favorite", listingId), viewer, null).andExpect(status().isNoContent());
        long sellerId = userIdOf(sellerEmail);

        api.perform(post(ADMIN_USERS + "/{id}/block", sellerId), admin, null).andExpect(status().isNoContent());

        api.perform(get("/api/users/me"), seller, null).andExpect(status().isUnauthorized());
        refreshWithCookie(sellerLogin).andExpect(status().isUnauthorized());
        login(sellerEmail, status().isForbidden());
        assertListingVisible(listingId, viewer, false);
        api.perform(put("/api/listings/{id}/favorite", listingId), viewer, null).andExpect(status().isNotFound());

        api.perform(post(ADMIN_USERS + "/{id}/unblock", sellerId), admin, null).andExpect(status().isNoContent());

        login(sellerEmail, status().isOk());
        assertListingVisible(listingId, viewer, true);
    }

    @Test
    void block_adminSelfOrUnknownUser_isRejected() throws Exception {
        String adminEmail = TestApi.uniqueEmail();
        String admin = adminToken(adminEmail);
        String otherAdminEmail = TestApi.uniqueEmail();
        adminToken(otherAdminEmail);

        api.perform(post(ADMIN_USERS + "/{id}/block", userIdOf(adminEmail)), admin, null)
                .andExpect(status().isUnprocessableContent());
        api.perform(post(ADMIN_USERS + "/{id}/block", userIdOf(otherAdminEmail)), admin, null)
                .andExpect(status().isUnprocessableContent());
        api.perform(post(ADMIN_USERS + "/{id}/block", 99_999_999), admin, null).andExpect(status().isNotFound());
        api.perform(post(ADMIN_USERS + "/{id}/unblock", 99_999_999), admin, null).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select enabled from users where email = ?", Boolean.class,
                otherAdminEmail)).isTrue();
    }

    private void assertListingVisible(long listingId, String viewer, boolean visible) throws Exception {
        mockMvc.perform(get("/api/listings/{id}", listingId))
                .andExpect(visible ? status().isOk() : status().isNotFound());
        List<Long> searchIds = idsOf(mockMvc.perform(get("/api/listings").param("size", "50"))
                .andExpect(status().isOk()).andReturn());
        List<Long> favoriteIds = idsOf(api.perform(get("/api/users/me/favorites"), viewer, null)
                .andExpect(status().isOk()).andReturn());
        assertThat(searchIds.contains(listingId)).isEqualTo(visible);
        assertThat(favoriteIds.contains(listingId)).isEqualTo(visible);
    }

    private static List<Long> idsOf(MvcResult result) throws Exception {
        return JsonPath.<List<Number>>read(result.getResponse().getContentAsString(), "$.content[*].id").stream()
                .map(Number::longValue).toList();
    }

    private String adminToken() throws Exception {
        return adminToken(TestApi.uniqueEmail());
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

    private MvcResult register(String email) throws Exception {
        Map<String, String> body = Map.of("email", email, "password", PASSWORD,
                "firstName", "Ayşe", "lastName", "Yılmaz", "phone", "+905321234567");
        return api.perform(post("/api/auth/register"), null, body).andExpect(status().isCreated()).andReturn();
    }

    private MvcResult login(String email) throws Exception {
        return login(email, status().isOk());
    }

    private MvcResult login(String email, ResultMatcher expected)
            throws Exception {
        return api.perform(post("/api/auth/login"), null, Map.of("email", email, "password", PASSWORD))
                .andExpect(expected).andReturn();
    }

    private ResultActions refreshWithCookie(MvcResult loginResult) throws Exception {
        Cookie cookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN));
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private String roleOf(String email) {
        return jdbcTemplate.queryForObject("select role from users where email = ?", String.class, email);
    }
}
