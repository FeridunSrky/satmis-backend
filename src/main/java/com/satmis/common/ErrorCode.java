package com.satmis.common;

// Machine-readable reason for errors the client must handle specifically, where the status code alone is ambiguous
public enum ErrorCode {
    PHONE_REQUIRED,
    EMAIL_NOT_VERIFIED,
    REFRESH_TOKEN_ROTATED
}
