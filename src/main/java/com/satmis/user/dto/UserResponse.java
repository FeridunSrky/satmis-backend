package com.satmis.user.dto;

import com.satmis.enums.Role;

public record UserResponse(
        Long id,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName,
        String phone,
        Role role
) {
}
