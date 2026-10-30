package com.satmis.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "Doğrulama kodu zorunludur")
        String token
) {
}
