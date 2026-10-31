package com.satmis.common;

import com.satmis.storage.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    // BindException also covers query-parameter objects (search filters, paging), not only request bodies
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(BindException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // Conversion failures (e.g. unknown enum value) carry Spring's English technical message
            String message = fieldError.isBindingFailure() ? "Geçersiz değer" : fieldError.getDefaultMessage();
            errors.putIfAbsent(fieldError.getField(), message);
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ErrorResponse.withErrors(status, "Doğrulama hatası", request.getRequestURI(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "İstek gövdesi okunamadı", request);
    }

    // e.g. /api/catalog/brands/abc: without this the generic handler would answer 500
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Geçersiz parametre: " + ex.getName(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Hesap devre dışı", request);
    }

    // Thrown by @PreAuthorize; without this the generic handler would turn it into a 500
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Kayıt başka bir işlemle güncellendi, sayfayı yenileyip tekrar deneyin",
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Kayıt çakışması: bu veri zaten mevcut", request);
    }

    // Storage is down or timed out; the client may retry, so this is not reported as the client's fault
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex, HttpServletRequest request) {
        log.error("Object storage call failed on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Görsel deposuna şu an ulaşılamıyor, lütfen tekrar deneyin", request);
    }

    // Spring MVC exceptions (404, 405, 415...) carry their own status; anything else is a 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            return build(status, messageFor(status), request);
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata oluştu", request);
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Kaynak bulunamadı";
            case 405 -> "Bu HTTP metodu desteklenmiyor";
            case 415 -> "Desteklenmeyen içerik türü";
            default -> "İstek işlenemedi";
        };
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatusCode status, String message,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
