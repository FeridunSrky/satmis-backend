package com.satmis.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogNameRequest(
        @NotBlank(message = "İsim zorunludur")
        @Size(max = 50, message = "İsim en fazla 50 karakter olabilir")
        String name
) {
}
