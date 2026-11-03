package com.satmis.comment;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The file goes to SeaweedFS over real HTTP with the presigned URL, exactly as the browser will send it.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class CommentAttachmentUploadIT {

    private static final String ATTACHMENTS = "/api/comment-attachments";
    private static final String PDF = "application/pdf";
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

    @Test
    void uploadFlow_validPdf_isCheckedAndServedAsDownload() throws Exception {
        String token = register(true);
        byte[] file = pdf(4096);

        String response = requestUpload(token, PDF, file.length)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadHeaders['content-type']").value(PDF))
                .andExpect(jsonPath("$.uploadHeaders['content-disposition']").value("attachment"))
                .andReturn().getResponse().getContentAsString();
        assertThat(putFile(response, file)).isEqualTo(200);

        String completed = perform(post(ATTACHMENTS + "/{id}/complete", idOf(response)), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PDF"))
                .andExpect(jsonPath("$.sizeBytes").value(file.length))
                .andExpect(jsonPath("$.url", startsWith(storage.endpoint() + "/" + SeaweedFsContainer.BUCKET + "/comments/")))
                .andReturn().getResponse().getContentAsString();

        HttpResponse<byte[]> publicRead = HTTP.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(completed, "$.url"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(publicRead.body()).isEqualTo(file);
        assertThat(publicRead.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        assertThat(statusOf(idOf(response))).isEqualTo("UPLOADED");
    }

    @Test
    void uploadFlow_jpeg_isNotForcedToDownload() throws Exception {
        String token = register(true);
        byte[] file = jpeg(1024);

        String response = requestUpload(token, "image/jpeg", file.length)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadHeaders['content-disposition']").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(putFile(response, file)).isEqualTo(200);

        perform(post(ATTACHMENTS + "/{id}/complete", idOf(response)), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("JPEG"));
    }

    @Test
    void completeUpload_htmlDeclaredAsPdf_isRejectedAndRemoved() throws Exception {
        String token = register(true);
        byte[] file = padded("<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII), 512);
        String response = requestUpload(token, PDF, file.length).andReturn().getResponse().getContentAsString();
        assertThat(putFile(response, file)).isEqualTo(200);
        String objectKey = objectKeyOf(idOf(response));

        perform(post(ATTACHMENTS + "/{id}/complete", idOf(response)), token, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_INVALID"));

        awaitRemoved(idOf(response), objectKey);
    }

    @Test
    void requestUpload_unsupportedTypeTooLargeOrUnverified_isRejected() throws Exception {
        String verified = register(true);

        requestUpload(verified, "image/svg+xml", 100).andExpect(status().isBadRequest());
        requestUpload(verified, PDF, 11L * 1024 * 1024).andExpect(status().isBadRequest());
        requestUpload(register(false), PDF, 100)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        requestUpload(null, PDF, 100).andExpect(status().isUnauthorized());
    }

    @Test
    void completeUpload_beforeTheFileIsPut_returns422AndStaysPending() throws Exception {
        String token = register(true);
        long attachmentId = idOf(requestUpload(token, PDF, 100).andReturn().getResponse().getContentAsString());

        perform(post(ATTACHMENTS + "/{id}/complete", attachmentId), token, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_UPLOADED"));
        perform(post(ATTACHMENTS + "/{id}/complete", attachmentId), register(true), null)
                .andExpect(status().isNotFound());
        assertThat(statusOf(attachmentId)).isEqualTo("PENDING");
    }

    @Test
    void deleteUpload_unattachedFile_removesFileAndRow() throws Exception {
        String token = register(true);
        byte[] file = pdf(256);
        String response = requestUpload(token, PDF, file.length).andReturn().getResponse().getContentAsString();
        putFile(response, file);
        perform(post(ATTACHMENTS + "/{id}/complete", idOf(response)), token, null).andExpect(status().isOk());
        String objectKey = objectKeyOf(idOf(response));

        perform(delete(ATTACHMENTS + "/{id}", idOf(response)), token, null).andExpect(status().isNoContent());

        awaitRemoved(idOf(response), objectKey);
    }

    private ResultActions requestUpload(String token, String contentType, long sizeBytes) throws Exception {
        return perform(post(ATTACHMENTS), token, Map.of("contentType", contentType, "sizeBytes", sizeBytes));
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

    // Uploading evidence needs a verified e-mail, so the helper verifies it directly in the database
    private String register(boolean emailVerified) throws Exception {
        String email = "commenter-" + UUID.randomUUID() + "@satmis.com";
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

    // Files of deleted attachments are removed asynchronously after the commit, then their rows
    private void awaitRemoved(long attachmentId, String objectKey) throws InterruptedException {
        Instant deadline = Instant.now().plus(REMOVAL_TIMEOUT);
        while (rowExists(attachmentId) && Instant.now().isBefore(deadline)) {
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        assertThat(rowExists(attachmentId)).as("attachment row removed").isFalse();
        assertThat(objectStorage.find(objectKey)).as("stored file removed").isEmpty();
    }

    private boolean rowExists(long attachmentId) {
        return jdbcTemplate.queryForObject("select count(*) from comment_attachments where id = ?", Integer.class,
                attachmentId) > 0;
    }

    private String statusOf(long attachmentId) {
        return jdbcTemplate.queryForObject("select status from comment_attachments where id = ?", String.class,
                attachmentId);
    }

    private String objectKeyOf(long attachmentId) {
        return jdbcTemplate.queryForObject("select object_key from comment_attachments where id = ?", String.class,
                attachmentId);
    }

    private static long idOf(String response) {
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private static byte[] pdf(int size) {
        return padded("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), size);
    }

    // A minimal JPEG start (SOI + APP0 marker); enough for the signature check
    private static byte[] jpeg(int size) {
        return padded(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, size);
    }

    private static byte[] padded(byte[] header, int size) {
        return Arrays.copyOf(header, size);
    }
}
