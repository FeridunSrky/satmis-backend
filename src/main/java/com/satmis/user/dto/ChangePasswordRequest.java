package com.satmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut şifre zorunludur")
        String currentPassword,

        @NotBlank(message = "Yeni şifre zorunludur")
        @Size(min = 8, max = 64, message = "Yeni şifre 8 ile 64 karakter arasında olmalıdır")
        String newPassword
) {
}
