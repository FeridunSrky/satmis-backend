package com.satmis;

import com.jayway.jsonpath.JsonPath;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Accounts, listings and authenticated calls shared by the MockMvc integration tests; add with @Import(TestApi.class)
@TestComponent
public class TestApi {

    public static final String PASSWORD = "Sifre1234";

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private final JdbcTemplate jdbcTemplate;

    public TestApi(MockMvc mockMvc, JsonMapper jsonMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@satmis.com";
    }

    // E-mail not verified: may browse, but not list or comment
    public String register(String email) throws Exception {
        Map<String, String> body = Map.of("email", email, "password", PASSWORD,
                "firstName", "Ayşe", "lastName", "Yılmaz", "phone", "+905321234567");
        String response = perform(post("/api/auth/register"), null, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    public String userToken() throws Exception {
        return register(uniqueEmail());
    }

    // Allowed to list and comment; the e-mail is verified directly in the database
    public String verifiedToken(String email) throws Exception {
        String token = register(email);
        jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        return token;
    }

    public String verifiedToken() throws Exception {
        return verifiedToken(uniqueEmail());
    }

    // Authorities are reloaded from the database on every request, so the token works as admin right away
    public String adminToken() throws Exception {
        String email = uniqueEmail();
        String token = verifiedToken(email);
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", email);
        return token;
    }

    public long userIdOf(String token) throws Exception {
        String response = perform(get("/api/users/me"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    // A valid listing request with the first catalog series and district; maps are mutable so tests can adjust them
    public Map<String, Object> listingBody(String token, int imageCount) throws Exception {
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

    public Map<String, Object> listingBody(String token) throws Exception {
        return listingBody(token, 1);
    }

    // Waits for approval, like every new listing
    public long createListing(String token, Map<String, Object> body) throws Exception {
        String response = perform(post("/api/listings"), token, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    public long createListing(String token) throws Exception {
        return createListing(token, listingBody(token));
    }

    // Approved, so everyone can see it
    public long liveListing(String token) throws Exception {
        long listingId = createListing(token);
        TestListings.approve(jdbcTemplate, listingId);
        return listingId;
    }

    public long listingVersionOf(long listingId) {
        return jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, listingId);
    }
}
