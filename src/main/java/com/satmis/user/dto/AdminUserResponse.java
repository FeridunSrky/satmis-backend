package com.satmis.user.dto;

import com.satmis.enums.Role;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Role role,
        boolean enabled,
        boolean emailVerified,
        Instant createdAt,
        long listingCount
) {
}
