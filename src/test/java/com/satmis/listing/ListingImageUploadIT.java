package com.satmis.listing;

import com.jayway.jsonpath.JsonPath;
import com.satmis.SeaweedFsContainer;
import com.satmis.TestJwtSecret;
import com.satmis.storage.ObjectStorage;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The file goes to SeaweedFS over real HTTP with the presigned URL, exactly as the browser will send it.
@SpringBootTest(properties = "app.listing-images.max-unattached-per-user=3")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ListingImageUploadIT {

    private static final String IMAGES = "/api/listing-images";
    private static final String JPEG = "image/jpeg";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Duration REMOVAL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Container
    static SeaweedFsContainer storage = new SeaweedFsContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
        storage.registerStorageProperties(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectStorage objectStorage;

    @Autowired
    private ListingImageCleanupService listingImageCleanupService;

    @Test
    void uploadFlow_validJpeg_isStoredCheckedAndPubliclyReadable() throws Exception {
        String token = registerVerifiedUser();
        byte[] file = jpeg(2048);

        String response = requestUpload(token, JPEG, file.length)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.uploadHeaders['content-type']").value(JPEG))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(putFile(response, file)).isEqualTo(200);

        String completed = perform(post(IMAGES + "/{id}/complete", idOf(response)), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOf(response)))
                .andExpect(jsonPath("$.url", startsWith(storage.endpoint() + "/" + SeaweedFsContainer.BUCKET + "/listings/")))
                .andReturn().getResponse().getContentAsString();

        HttpResponse<byte[]> publicRead = HTTP.send(HttpRequest.newBuilder(URI.create(JsonPath.read(completed, "$.url"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(publicRead.body()).isEqualTo(file);
        assertThat(statusOf(idOf(response))).isEqualTo("UPLOADED");
    }

    @Test
    void completeUpload_calledTwice_returnsTheSameImage() throws Exception {
        String token = registerVerifiedUser();
        long imageId = uploadAndComplete(token);

        perform(post(IMAGES + "/{id}/complete", imageId), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(imageId));
    }

    @Test
    void completeUpload_beforeTheFileArrives_returns422AndCanBeRetried() throws Exception {
        String token = registerVerifiedUser();
        byte[] file = jpeg(1024);
        String response = requestUpload(token, JPEG, file.length).andReturn().getResponse().getContentAsString();

        perform(post(IMAGES + "/{id}/complete", idOf(response)), token, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_UPLOADED"));
        assertThat(statusOf(idOf(response))).isEqualTo("PENDING");

        putFile(response, file);
        perform(post(IMAGES + "/{id}/complete", idOf(response)), token, null).andExpect(status().isOk());
    }

    @Test
    void completeUpload_pngBytesDeclaredAsJpeg_returns422AndRemovesTheFile() throws Exception {
        String token = registerVerifiedUser();
        byte[] png = png(1024);
        String response = requestUpload(token, JPEG, png.length).andReturn().getResponse().getContentAsString();
        assertThat(putFile(response, png)).isEqualTo(200);
        String objectKey = objectKeyOf(idOf(response));

        perform(post(IMAGES + "/{id}/complete", idOf(response)), token, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"));

        awaitRemoved(idOf(response), objectKey);
        perform(post(IMAGES + "/{id}/complete", idOf(response)), token, null).andExpect(status().isNotFound());
    }

    @Test
    void completeUpload_anotherUsersImage_returns404() throws Exception {
        long imageId = uploadAndComplete(registerVerifiedUser());

        perform(post(IMAGES + "/{id}/complete", imageId), registerVerifiedUser(), null)
                .andExpect(status().isNotFound());
        perform(delete(IMAGES + "/{id}", imageId), registerVerifiedUser(), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void requestUpload_unverifiedEmail_returns422WithEmailNotVerifiedCode() throws Exception {
        String token = registerUser();

        requestUpload(token, JPEG, 1000)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void requestUpload_invalidRequests_return400() throws Exception {
        String token = registerVerifiedUser();

        requestUpload(token, "image/svg+xml", 1000).andExpect(status().isBadRequest());
        requestUpload(token, JPEG, 11L * 1024 * 1024).andExpect(status().isBadRequest());
        perform(post(IMAGES), token, Map.of("contentType", JPEG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sizeBytes").isNotEmpty());
    }

    @Test
    void requestUpload_anonymous_returns401() throws Exception {
        perform(post(IMAGES), null, Map.of("contentType", JPEG, "sizeBytes", 1000)).andExpect(status().isUnauthorized());
    }

    @Test
    void requestUpload_unattachedLimitReached_returns422UntilAnImageIsDeleted() throws Exception {
        String token = registerVerifiedUser();
        long firstImageId = idOf(requestUpload(token, JPEG, 1000).andReturn().getResponse().getContentAsString());
        requestUpload(token, JPEG, 1000).andExpect(status().isCreated());
        requestUpload(token, JPEG, 1000).andExpect(status().isCreated());

        requestUpload(token, JPEG, 1000)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_LIMIT_REACHED"));

        perform(delete(IMAGES + "/{id}", firstImageId), token, null).andExpect(status().isNoContent());
        requestUpload(token, JPEG, 1000).andExpect(status().isCreated());
    }

    @Test
    void deleteUpload_uploadedImage_removesFileAndRowAfterCommit() throws Exception {
        String token = registerVerifiedUser();
        long imageId = uploadAndComplete(token);
        String objectKey = objectKeyOf(imageId);

        perform(delete(IMAGES + "/{id}", imageId), token, null).andExpect(status().isNoContent());

        awaitRemoved(imageId, objectKey);
        perform(delete(IMAGES + "/{id}", imageId), token, null).andExpect(status().isNotFound());
    }

    @Test
    void cleanUp_uploadAbandonedForADay_isRemovedAndFreshOneKept() throws Exception {
        String token = registerVerifiedUser();
        long abandonedId = uploadAndComplete(token);
        long freshId = uploadAndComplete(token);
        String abandonedKey = objectKeyOf(abandonedId);
        jdbcTemplate.update("update listing_images set created_at = now() - interval '25 hours' where id = ?", abandonedId);

        listingImageCleanupService.cleanUp();

        assertThat(rowExists(abandonedId)).isFalse();
        assertThat(objectStorage.find(abandonedKey)).isEmpty();
        assertThat(statusOf(freshId)).isEqualTo("UPLOADED");
        assertThat(objectStorage.find(objectKeyOf(freshId))).isPresent();
    }

    @Test
    void cleanUp_imageInAListing_isKeptHoweverOld() throws Exception {
        String token = registerVerifiedUser();
        long imageId = uploadAndComplete(token);
        perform(post("/api/listings"), token, listingBody(imageId)).andExpect(status().isCreated());
        jdbcTemplate.update("update listing_images set created_at = now() - interval '30 days' where id = ?", imageId);

        listingImageCleanupService.cleanUp();

        assertThat(statusOf(imageId)).isEqualTo("ATTACHED");
        assertThat(objectStorage.find(objectKeyOf(imageId))).isPresent();
    }

    @Test
    void uploadedImage_usedInAListing_isShownWithItsPublicUrlAndReleasedOnDelete() throws Exception {
        String token = registerVerifiedUser();
        long imageId = uploadAndComplete(token);
        String imageUrl = JsonPath.read(perform(post(IMAGES + "/{id}/complete", imageId), token, null)
                .andReturn().getResponse().getContentAsString(), "$.url");

        String listing = perform(post("/api/listings"), token, listingBody(imageId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images[0].id").value(imageId))
                .andExpect(jsonPath("$.images[0].url").value(imageUrl))
                .andExpect(jsonPath("$.images[0].cover").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(statusOf(imageId)).isEqualTo("ATTACHED");
        perform(delete(IMAGES + "/{id}", imageId), token, null).andExpect(status().isConflict());

        String objectKey = objectKeyOf(imageId);
        perform(delete("/api/listings/{id}", idOf(listing)), token, null).andExpect(status().isNoContent());
        awaitRemoved(imageId, objectKey);
    }

    private Map<String, Object> listingBody(long imageId) {
        Map<String, Object> vehicle = Map.of(
                "seriesId", jdbcTemplate.queryForObject("select id from vehicle_series order by id limit 1", Long.class),
                "modelYear", 2021, "kilometer", 20_000, "fuelType", "DIESEL", "gearType", "MANUAL",
                "vehicleCondition", "USED", "fromWho", "OWNER", "hasDamageRecord", false, "exchangeable", false);
        return Map.of("title", "Yüklenen görselli ilan", "price", 500_000, "currency", "TRY",
                "districtId", jdbcTemplate.queryForObject("select id from districts order by id limit 1", Long.class),
                "vehicle", vehicle, "imageIds", List.of(imageId));
    }

    private long uploadAndComplete(String token) throws Exception {
        byte[] file = jpeg(512);
        String response = requestUpload(token, JPEG, file.length).andReturn().getResponse().getContentAsString();
        putFile(response, file);
        perform(post(IMAGES + "/{id}/complete", idOf(response)), token, null).andExpect(status().isOk());
        return idOf(response);
    }

    private ResultActions requestUpload(String token, String contentType, long sizeBytes) throws Exception {
        return perform(post(IMAGES), token, Map.of("contentType", contentType, "sizeBytes", sizeBytes));
    }

    // Sends the file the way the browser will: PUT to the presigned URL with exactly the returned headers
    private static int putFile(String uploadResponse, byte[] file) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(JsonPath.read(uploadResponse, "$.uploadUrl")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file));
        JsonPath.<Map<String, String>>read(uploadResponse, "$.uploadHeaders").forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
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

    // Listing (and so uploading) needs a verified e-mail, so the helper verifies it directly in the database
    private String registerVerifiedUser() throws Exception {
        return register(true);
    }

    private String registerUser() throws Exception {
        return register(false);
    }

    private String register(boolean emailVerified) throws Exception {
        String email = "uploader-" + UUID.randomUUID() + "@satmis.com";
        Map<String, String> body = Map.of("email", email, "password", "Sifre1234",
                "firstName", "Ayşe", "lastName", "Yılmaz", "phone", "+905321234567");
        String response = perform(post("/api/auth/register"), null, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        if (emailVerified) {
            jdbcTemplate.update("update users set email_verified = true where email = ?", email);
        }
        return JsonPath.read(response, "$.accessToken");
    }

    // Files of deleted images are removed asynchronously after the commit, then their rows
    private void awaitRemoved(long imageId, String objectKey) throws InterruptedException {
        Instant deadline = Instant.now().plus(REMOVAL_TIMEOUT);
        while (rowExists(imageId) && Instant.now().isBefore(deadline)) {
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        assertThat(rowExists(imageId)).as("image row removed").isFalse();
        assertThat(objectStorage.find(objectKey)).as("stored file removed").isEmpty();
    }

    private boolean rowExists(long imageId) {
        return jdbcTemplate.queryForObject("select count(*) from listing_images where id = ?", Integer.class, imageId) > 0;
    }

    private String statusOf(long imageId) {
        return jdbcTemplate.queryForObject("select status from listing_images where id = ?", String.class, imageId);
    }

    private String objectKeyOf(long imageId) {
        return jdbcTemplate.queryForObject("select object_key from listing_images where id = ?", String.class, imageId);
    }

    private static long idOf(String response) {
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    // A minimal JPEG start (SOI + APP0 marker) padded to the wanted size; enough for the signature check
    private static byte[] jpeg(int size) {
        return withHeader(size, 0xFF, 0xD8, 0xFF, 0xE0);
    }

    private static byte[] png(int size) {
        return withHeader(size, 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n');
    }

    private static byte[] withHeader(int size, int... header) {
        byte[] file = new byte[size];
        for (int index = 0; index < header.length; index++) {
            file[index] = (byte) header[index];
        }
        return file;
    }
}
