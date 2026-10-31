package com.satmis.user.dto;

import jakarta.validation.constraints.Size;

// q matches any part of the e-mail address; blank lists every user
public record AdminUserSearchRequest(
        @Size(max = 100) String q
) {
}
