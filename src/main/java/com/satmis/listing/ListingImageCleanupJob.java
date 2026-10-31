package com.satmis.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Without this, abandoned uploads would fill the storage and deleted files whose removal failed would stay public.
// Safe on several instances at once: storage deletes and the guarded row deletes are idempotent.
@Component
@RequiredArgsConstructor
public class ListingImageCleanupJob {

    private final ListingImageCleanupService listingImageCleanupService;

    @Scheduled(cron = "${app.listing-images.cleanup-cron}", zone = "UTC")
    public void cleanUp() {
        listingImageCleanupService.cleanUp();
    }
}
