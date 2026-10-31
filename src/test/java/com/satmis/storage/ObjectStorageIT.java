package com.satmis.storage;

import com.satmis.SeaweedFsContainer;
import com.satmis.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Uploads go over real HTTP with the presigned URL, the way a browser would send them.
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ObjectStorageIT {

    private static final Duration VALID_FOR = Duration.ofMinutes(10);
    private static final String JPEG = "image/jpeg";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

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
    private ObjectStorage objectStorage;

    @Test
    void presignUpload_matchingFile_isStoredAndPubliclyReadable() throws Exception {
        String key = newKey();
        byte[] file = bytes(1000);

        PresignedUpload upload = objectStorage.presignUpload(key, JPEG, file.length, VALID_FOR);

        assertThat(upload.expiresAt()).isCloseTo(Instant.now().plus(VALID_FOR), within(Duration.ofSeconds(30)));
        assertThat(upload.headers()).containsKeys("content-type", "cache-control").doesNotContainKeys("host", "content-length");
        assertThat(put(upload, file)).isEqualTo(200);
        assertThat(objectStorage.find(key)).contains(new StoredObject(file.length, JPEG));

        HttpResponse<byte[]> publicRead = HTTP.send(HttpRequest.newBuilder(URI.create(objectStorage.publicUrl(key))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(publicRead.statusCode()).isEqualTo(200);
        assertThat(publicRead.body()).isEqualTo(file);
        assertThat(publicRead.headers().firstValue("cache-control")).contains(ObjectStorage.CACHE_CONTROL);
    }

    @Test
    void presignUpload_largerFileThanSigned_isRejectedByStorage() throws Exception {
        String key = newKey();
        PresignedUpload upload = objectStorage.presignUpload(key, JPEG, 1000, VALID_FOR);

        assertThat(put(upload, bytes(5000))).isEqualTo(403);
        assertThat(objectStorage.find(key)).isEmpty();
    }

    @Test
    void presignUpload_otherContentTypeThanSigned_isRejectedByStorage() throws Exception {
        String key = newKey();
        byte[] file = bytes(1000);
        PresignedUpload upload = objectStorage.presignUpload(key, JPEG, file.length, VALID_FOR);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.url()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file));
        upload.headers().forEach(request::header);
        request.setHeader("content-type", "image/svg+xml");

        assertThat(HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        assertThat(objectStorage.find(key)).isEmpty();
    }

    @Test
    void find_missingKey_returnsEmpty() {
        assertThat(objectStorage.find(newKey())).isEmpty();
    }

    @Test
    void readFirstBytes_storedFile_returnsOnlyThePrefix() throws Exception {
        String key = newKey();
        byte[] file = bytes(1000);
        put(objectStorage.presignUpload(key, JPEG, file.length, VALID_FOR), file);

        assertThat(objectStorage.readFirstBytes(key, 16)).containsExactly(Arrays.copyOf(file, 16));
    }

    @Test
    void delete_storedFile_removesItAndCanBeRepeated() throws Exception {
        String key = newKey();
        byte[] file = bytes(1000);
        put(objectStorage.presignUpload(key, JPEG, file.length, VALID_FOR), file);

        objectStorage.delete(key);
        objectStorage.delete(key);

        assertThat(objectStorage.find(key)).isEmpty();
    }

    private static int put(PresignedUpload upload, byte[] file) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.url()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file));
        upload.headers().forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String newKey() {
        return "listings/test/" + UUID.randomUUID() + ".jpg";
    }

    private static byte[] bytes(int length) {
        byte[] file = new byte[length];
        for (int index = 0; index < length; index++) {
            file[index] = (byte) index;
        }
        return file;
    }
}
