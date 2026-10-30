package com.satmis.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Sıfırlama kodu zorunludur")
        String token,

        @NotBlank(message = "Yeni şifre zorunludur")
        @Size(min = 8, max = 64, message = "Yeni şifre 8 ile 64 karakter arasında olmalıdır")
        String newPassword
) {
}
