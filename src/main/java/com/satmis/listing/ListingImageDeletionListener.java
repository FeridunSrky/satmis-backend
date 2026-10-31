package com.satmis.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// After commit: a rolled-back request never loses a file. Async: storage latency never reaches the response.
// Whatever fails here stays DELETED and is retried by ListingImageCleanupJob.
@Component
@RequiredArgsConstructor
public class ListingImageDeletionListener {

    private final ListingImageCleanupService listingImageCleanupService;

    @Async
    @TransactionalEventListener
    public void onImagesDeleted(ListingImagesDeletedEvent event) {
        listingImageCleanupService.purge(event.images());
    }
}
