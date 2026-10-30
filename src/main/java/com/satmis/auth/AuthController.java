package com.satmis.auth;

import com.satmis.auth.dto.AuthResponse;
import com.satmis.auth.dto.ForgotPasswordRequest;
import com.satmis.auth.dto.LoginRequest;
import com.satmis.auth.dto.RefreshRequest;
import com.satmis.auth.dto.RegisterRequest;
import com.satmis.auth.dto.ResetPasswordRequest;
import com.satmis.auth.dto.VerifyEmailRequest;
import com.satmis.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final URI CURRENT_USER_LOCATION = URI.create("/api/users/me");

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenCookies refreshTokenCookies;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(ResponseEntity.created(CURRENT_USER_LOCATION), authService.register(request));
    }

    // Called by the frontend page the e-mail link opens
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
    }

    // Always 204, whether or not the e-mail is registered
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(ResponseEntity.ok(), authService.login(request));
    }

    // Token from the body or the httpOnly cookie; the body is optional for cookie clients
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest request,
                                                HttpServletRequest httpRequest) {
        String rawToken = refreshTokenCookies.find(httpRequest, request)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token bulunamadı"));
        return withRefreshCookie(ResponseEntity.ok(), authService.refresh(rawToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                       HttpServletRequest httpRequest) {
        refreshTokenCookies.find(httpRequest, request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(ResponseEntity.BodyBuilder builder, AuthResponse response) {
        return builder.header(HttpHeaders.SET_COOKIE, refreshTokenCookies.issue(response.refreshToken()).toString())
                .body(response);
    }
}
