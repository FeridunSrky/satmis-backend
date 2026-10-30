package com.satmis.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Every login and refresh adds a row; without this the table only ever grows.
// Safe on several instances at once: the delete is idempotent.
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${app.jwt.refresh-token-cleanup-cron}", zone = "UTC")
    public void deleteExpiredTokens() {
        int deleted = refreshTokenService.deleteExpired();
        log.info("Refresh token cleanup removed {} expired tokens", deleted);
    }
}
