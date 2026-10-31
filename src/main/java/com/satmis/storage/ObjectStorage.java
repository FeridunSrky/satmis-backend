package com.satmis.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Adapter around the S3 API (R2 in prod, SeaweedFS in dev); callers never see SDK types or exceptions.
@Service
@RequiredArgsConstructor
public class ObjectStorage {

    // Keys are never reused, so browsers and CDNs may keep an object forever
    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    // The browser adds these itself and refuses to let scripts set them
    private static final Set<String> BROWSER_SET_HEADERS = Set.of("host", "content-length");

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    /**
     * Signs a one-off PUT for exactly this key, type and size. The storage rejects an upload whose body or
     * headers differ from what was signed, so the client cannot upload a larger file or another type.
     */
    public PresignedUpload presignUpload(String key, String contentType, long contentLength, Duration validFor) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presign -> presign
                .signatureDuration(validFor)
                .putObjectRequest(put -> put
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .cacheControl(CACHE_CONTROL)));
        Map<String, String> headers = presigned.signedHeaders().entrySet().stream()
                .filter(header -> !BROWSER_SET_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toMap(Map.Entry::getKey, header -> header.getValue().getFirst()));
        return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
    }

    public Optional<StoredObject> find(String key) {
        try {
            HeadObjectResponse head = s3Client.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (SdkException ex) {
            throw new StorageException("Reading metadata of " + key + " failed", ex);
        }
    }

    // Returns fewer bytes when the object is shorter than `count`
    public byte[] readFirstBytes(String key, int count) {
        try {
            return s3Client.getObjectAsBytes(request -> request
                    .bucket(properties.bucket())
                    .key(key)
                    .range("bytes=0-" + (count - 1))).asByteArray();
        } catch (SdkException ex) {
            throw new StorageException("Reading the start of " + key + " failed", ex);
        }
    }

    // Deleting a missing key succeeds, so a failed cleanup can simply be repeated
    public void delete(String key) {
        try {
            s3Client.deleteObject(request -> request.bucket(properties.bucket()).key(key));
        } catch (SdkException ex) {
            throw new StorageException("Deleting " + key + " failed", ex);
        }
    }

    public String publicUrl(String key) {
        return properties.publicBaseUrl() + "/" + key;
    }
}
