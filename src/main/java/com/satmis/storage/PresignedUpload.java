package com.satmis.storage;

import java.time.Instant;
import java.util.Map;

// The client must send exactly these headers with its PUT, otherwise the signature does not match
public record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
}
