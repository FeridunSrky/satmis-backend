package com.satmis.auth.dto;

import com.satmis.common.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "E-posta zorunludur")
        @Email(message = "Geçerli bir e-posta adresi giriniz")
        @Size(max = 100, message = "E-posta en fazla 100 karakter olabilir")
        String email,

        @NotBlank(message = "Şifre zorunludur")
        @Size(min = 8, max = 64, message = "Şifre 8 ile 64 karakter arasında olmalıdır")
        String password,

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
