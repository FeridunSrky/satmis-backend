package com.satmis.security;

import com.satmis.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.capacity=3",
        "app.rate-limit.login.period=1h",
        "app.rate-limit.image-upload.capacity=2",
        "app.rate-limit.image-upload.period=1h",
        "app.rate-limit.listing-report.capacity=2",
        "app.rate-limit.listing-report.period=1h"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class RateLimitIT {

    private static final int LOGIN_CAPACITY = 3;
    private static final int IMAGE_UPLOAD_CAPACITY = 2;
    private static final int LISTING_REPORT_CAPACITY = 2;
    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);
    private static final String WRONG_LOGIN = """
            {"email": "nobody@satmis.com", "password": "YanlisSifre1"}""";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_overLimit_returns429WithRetryAfter() throws Exception {
        String clientIp = uniqueIp();
        for (int attempt = 0; attempt < LOGIN_CAPACITY; attempt++) {
            login(clientIp).andExpect(status().isUnauthorized());
        }

        login(clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void login_limitReachedByOtherClient_isNotAffected() throws Exception {
        String blockedIp = uniqueIp();
        for (int attempt = 0; attempt <= LOGIN_CAPACITY; attempt++) {
            login(blockedIp);
        }

        login(uniqueIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void otherEndpoints_afterLoginLimitReached_stayAvailable() throws Exception {
        String clientIp = uniqueIp();
        for (int attempt = 0; attempt <= LOGIN_CAPACITY; attempt++) {
            login(clientIp);
        }

        mockMvc.perform(post("/api/auth/register").with(fromIp(clientIp))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/catalog/brands").with(fromIp(clientIp)))
                .andExpect(status().isOk());
    }

    // Counted before authentication, so even anonymous callers cannot request upload URLs without limit
    @Test
    void imageUpload_overLimit_returns429() throws Exception {
        String clientIp = uniqueIp();
        for (int attempt = 0; attempt < IMAGE_UPLOAD_CAPACITY; attempt++) {
            requestImageUpload(clientIp).andExpect(status().isUnauthorized());
        }

        requestImageUpload(clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.path").value("/api/listing-images"));
    }

    // The listing id is part of the path, yet one client shares a single bucket across all listings
    @Test
    void listingReport_overLimitAcrossListings_returns429() throws Exception {
        String clientIp = uniqueIp();
        for (int listingId = 1; listingId <= LISTING_REPORT_CAPACITY; listingId++) {
            reportListing(clientIp, listingId).andExpect(status().isUnauthorized());
        }

        reportListing(clientIp, 99)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.path").value("/api/listings/99/reports"));
        reportListing(uniqueIp(), 99).andExpect(status().isUnauthorized());
    }

    private ResultActions reportListing(String clientIp, long listingId) throws Exception {
        return mockMvc.perform(post("/api/listings/{id}/reports", listingId).with(fromIp(clientIp))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"reason": "FRAUD_SUSPECTED"}"""));
    }

    private ResultActions requestImageUpload(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/listing-images").with(fromIp(clientIp))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"contentType": "image/jpeg", "sizeBytes": 1000}"""));
    }

    private ResultActions login(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(fromIp(clientIp))
                .contentType(MediaType.APPLICATION_JSON).content(WRONG_LOGIN));
    }

    private static RequestPostProcessor fromIp(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    // Each test gets its own address so bucket state never leaks between tests
    private static String uniqueIp() {
        return "10.0.0." + NEXT_CLIENT.getAndIncrement();
    }
}
