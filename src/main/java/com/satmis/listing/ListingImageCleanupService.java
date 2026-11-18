package com.satmis.listing;

import com.satmis.enums.ListingImageStatus;
import com.satmis.storage.StoredFilePurger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.EnumSet;

// Without the scheduled run, abandoned uploads would fill the storage and deleted files whose removal failed would
// stay public. Safe on several instances at once: storage deletes and the guarded row deletes are idempotent.
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingImageCleanupService {

    private final ListingImageRepository listingImageRepository;
    private final StoredFilePurger storedFilePurger;
    private final ListingImageProperties properties;

    // After commit: a rolled-back request never loses a file. Async: storage latency never reaches the response.
    // Whatever fails here stays DELETED and is retried by cleanUp.
    @Async
    @TransactionalEventListener
    public void onImagesDeleted(ListingImagesDeletedEvent event) {
        storedFilePurger.purge(event.images(), listingImageRepository::deleteDeletedByIdIn);
    }

    // Abandoned uploads (never saved in a listing) become DELETED first, then every DELETED image is purged
    @Scheduled(cron = "${app.listing-images.cleanup-cron}", zone = "UTC")
    public void cleanUp() {
        Instant createdBefore = Instant.now().minus(properties.abandonedAfter());
        int abandoned = listingImageRepository.markDeleted(
                EnumSet.of(ListingImageStatus.PENDING, ListingImageStatus.UPLOADED), createdBefore);
        int removed = storedFilePurger.purgeAll(listingImageRepository::findDeleted,
                listingImageRepository::deleteDeletedByIdIn);
        log.info("Listing image cleanup: {} abandoned uploads marked deleted, {} images removed", abandoned, removed);
    }
}
