package com.satmis.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        ErrorCode code,
        String message,
        String path,
        Map<String, String> errors
) {

    public static ErrorResponse of(HttpStatusCode status, String message, String path) {
        return of(status, null, message, path);
    }

    public static ErrorResponse of(HttpStatusCode status, ErrorCode code, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), code, message, path, null);
    }

    public static ErrorResponse withErrors(HttpStatusCode status, String message, String path,
                                           Map<String, String> errors) {
        return new ErrorResponse(Instant.now(), status.value(), null, message, path, errors);
    }
}
