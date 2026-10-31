package com.satmis.listing;

import com.satmis.enums.ListingImageStatus;
import com.satmis.storage.ObjectStorage;
import com.satmis.storage.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Removes the stored files of DELETED images, then their rows. The database stays the source of truth: a row is
 * only dropped after its file is gone, so a failed storage call is simply retried by the next cleanup run.
 * Runs without a transaction so no database connection is held during storage calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingImageCleanupService {

    private static final int BATCH_SIZE = 100;
    // Bounds one run; anything left over is picked up an hour later
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final ListingImageRepository listingImageRepository;
    private final ObjectStorage objectStorage;
    private final ListingImageProperties properties;

    /** Returns how many images were removed; stops at the first storage failure. */
    public int purge(List<DeletedImageView> images) {
        List<Long> removedIds = new ArrayList<>();
        try {
            for (DeletedImageView image : images) {
                objectStorage.delete(image.objectKey());
                removedIds.add(image.id());
            }
        } catch (StorageException ex) {
            log.warn("Removing stored listing images failed, {} left for the next cleanup run: {}",
                    images.size() - removedIds.size(), ex.getMessage());
        }
        if (!removedIds.isEmpty()) {
            listingImageRepository.deleteDeletedByIdIn(removedIds);
        }
        return removedIds.size();
    }

    // Abandoned uploads (never saved in a listing) become DELETED first, then every DELETED image is purged
    public void cleanUp() {
        Instant createdBefore = Instant.now().minus(properties.abandonedAfter());
        int abandoned = listingImageRepository.markDeleted(
                EnumSet.of(ListingImageStatus.PENDING, ListingImageStatus.UPLOADED), createdBefore);
        int removed = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<DeletedImageView> images = listingImageRepository.findDeleted(PageRequest.of(0, BATCH_SIZE));
            int removedInBatch = images.isEmpty() ? 0 : purge(images);
            removed += removedInBatch;
            if (removedInBatch < BATCH_SIZE) {
                break;
            }
        }
        log.info("Listing image cleanup: {} abandoned uploads marked deleted, {} images removed", abandoned, removed);
    }
}
