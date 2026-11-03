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
import java.util.regex.Pattern;

// Limits the auth, mail-sending, upload-URL, listing-report and comment endpoints per client IP against password
// guessing, sign-up and mail spam, filling the storage, mass-reporting and flooding the comment queue.
// Buckets live in memory, so each app instance counts on its own.
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MIN_RETRY_AFTER_SECONDS = 1;
    // One bucket for all listings: the id varies, the client should still be counted once
    private static final String LISTING_REPORT_PATH = "/api/listings/{id}/reports";
    // Comments and the owner's replies share one bucket: both end up in the same moderation queue
    private static final String LISTING_COMMENT_PATH = "/api/listings/{id}/comments";
    private static final Map<String, Pattern> PATTERN_PATHS = Map.of(
            LISTING_REPORT_PATH, Pattern.compile("/api/listings/[0-9]+/reports"),
            LISTING_COMMENT_PATH, Pattern.compile("/api/listings/[0-9]+/comments|/api/comments/[0-9]+/reply"));

    private final boolean enabled;
    private final Map<String, RateLimitProperties.Rule> rulesByPath;
    private final ConcurrentLruCache<BucketKey, Bucket> buckets;
    private final RestSecurityHandler errorWriter;

    public RateLimitFilter(RateLimitProperties properties, RestSecurityHandler errorWriter) {
        this.enabled = properties.enabled();
        this.rulesByPath = Map.ofEntries(
                Map.entry("/api/auth/login", properties.login()),
                Map.entry("/api/auth/register", properties.register()),
                Map.entry("/api/auth/refresh", properties.refresh()),
                Map.entry("/api/users/me/email-verification", properties.resendVerification()),
                Map.entry("/api/auth/forgot-password", properties.forgotPassword()),
                Map.entry("/api/auth/reset-password", properties.resetPassword()),
                Map.entry("/api/listing-images", properties.imageUpload()),
                Map.entry("/api/comment-attachments", properties.commentAttachmentUpload()),
                Map.entry(LISTING_REPORT_PATH, properties.listingReport()),
                Map.entry(LISTING_COMMENT_PATH, properties.listingComment()));
        this.buckets = new ConcurrentLruCache<>(properties.maxTrackedClients(),
                key -> newBucket(rulesByPath.get(key.path())));
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || ruleKeyOf(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        BucketKey key = new BucketKey(ruleKeyOf(request.getRequestURI()), request.getRemoteAddr());
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

    // The configured path for this URI, or null when it is not limited
    private String ruleKeyOf(String uri) {
        if (rulesByPath.containsKey(uri)) {
            return uri;
        }
        return PATTERN_PATHS.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(uri).matches())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static Bucket newBucket(RateLimitProperties.Rule rule) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()).build())
                .build();
    }

    private record BucketKey(String path, String clientIp) {
    }
}
