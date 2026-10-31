package com.satmis.listing.dto;

import java.time.Instant;
import java.util.Map;

// The client PUTs the file to uploadUrl with exactly uploadHeaders, then calls complete
public record ImageUploadResponse(Long id, String uploadUrl, Map<String, String> uploadHeaders, Instant expiresAt) {
}
