package com.satmis.auth.dto;

import com.satmis.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {
}
