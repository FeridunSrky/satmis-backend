package com.satmis.auth;

import com.satmis.auth.dto.RefreshRequest;
import com.satmis.common.ApiException;
import com.satmis.security.CorsProperties;
import com.satmis.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Optional;

// The refresh token lives in an httpOnly cookie so scripts (and XSS) can never read it.
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RefreshCookieProperties.class)
public class RefreshTokenCookies {

    // Only the auth endpoints need the cookie; no other request carries it
    private static final String COOKIE_PATH = "/api/auth";

    private final RefreshCookieProperties properties;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;

    public ResponseCookie issue(String rawToken) {
        return cookie(rawToken, Duration.ofMillis(jwtProperties.refreshExpirationMs()));
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    // A token in the body wins so API clients without cookies keep working.
    // Browsers attach the cookie to forged cross-site requests too, so a cookie only counts with a trusted Origin.
    public Optional<String> find(HttpServletRequest request, RefreshRequest body) {
        if (body != null && StringUtils.hasText(body.refreshToken())) {
            return Optional.of(body.refreshToken());
        }
        Cookie cookie = WebUtils.getCookie(request, properties.name());
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return Optional.empty();
        }
        if (!corsProperties.allowedOrigins().contains(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "İstek kaynağı doğrulanamadı");
        }
        return Optional.of(cookie.getValue());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
