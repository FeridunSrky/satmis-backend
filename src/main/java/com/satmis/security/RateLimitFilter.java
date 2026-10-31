package com.satmis.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

// Limits the auth, mail-sending and upload-URL endpoints per client IP against password guessing, sign-up and
// mail spam, and against filling the image storage.
// Buckets live in memory, so each app instance counts on its own.
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MIN_RETRY_AFTER_SECONDS = 1;

    private final boolean enabled;
    private final Map<String, RateLimitProperties.Rule> rulesByPath;
    private final ConcurrentLruCache<BucketKey, Bucket> buckets;
    private final RestSecurityHandler errorWriter;

    public RateLimitFilter(RateLimitProperties properties, RestSecurityHandler errorWriter) {
        this.enabled = properties.enabled();
        this.rulesByPath = Map.of(
                "/api/auth/login", properties.login(),
                "/api/auth/register", properties.register(),
                "/api/auth/refresh", properties.refresh(),
                "/api/users/me/email-verification", properties.resendVerification(),
                "/api/auth/forgot-password", properties.forgotPassword(),
                "/api/auth/reset-password", properties.resetPassword(),
                "/api/listing-images", properties.imageUpload());
        this.buckets = new ConcurrentLruCache<>(properties.maxTrackedClients(),
                key -> newBucket(rulesByPath.get(key.path())));
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || !rulesByPath.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        BucketKey key = new BucketKey(request.getRequestURI(), request.getRemoteAddr());
        ConsumptionProbe probe = buckets.get(key).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(MIN_RETRY_AFTER_SECONDS,
                Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        // The client IP is personal data, so only the endpoint is logged
        log.warn("Rate limit exceeded on {}", key.path());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        errorWriter.writeError(response, request, HttpStatus.TOO_MANY_REQUESTS,
                "Çok fazla deneme yaptınız, lütfen " + retryAfterSeconds + " saniye sonra tekrar deneyin");
    }

    private static Bucket newBucket(RateLimitProperties.Rule rule) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()).build())
                .build();
    }

    private record BucketKey(String path, String clientIp) {
    }
}
