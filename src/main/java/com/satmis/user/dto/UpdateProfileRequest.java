package com.satmis.user.dto;

import com.satmis.common.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Ad zorunludur")
        @Size(max = 50, message = "Ad en fazla 50 karakter olabilir")
        String firstName,

        @NotBlank(message = "Soyad zorunludur")
        @Size(max = 50, message = "Soyad en fazla 50 karakter olabilir")
        String lastName,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Telefon numarası 10-15 haneli olmalıdır")
        String phone
) {
}
