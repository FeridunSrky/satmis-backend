package com.satmis.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Removes the stored files of DELETED rows, then the rows. The database stays the source of truth: a row is only
 * dropped after its file is gone, so a failed storage call is simply retried by the next cleanup run.
 * Runs without a transaction so no database connection is held during storage calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoredFilePurger {

    private static final int BATCH_SIZE = 100;
    // Bounds one run; anything left over is picked up by the next one
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final ObjectStorage objectStorage;

    /** Returns how many files were removed; stops at the first storage failure. */
    public int purge(List<DeletedFile> files, Consumer<List<Long>> deleteRows) {
        List<Long> removedIds = new ArrayList<>();
        try {
            for (DeletedFile file : files) {
                objectStorage.delete(file.objectKey());
                removedIds.add(file.id());
            }
        } catch (StorageException ex) {
            log.warn("Removing stored files failed, {} left for the next cleanup run: {}",
                    files.size() - removedIds.size(), ex.getMessage());
        }
        if (!removedIds.isEmpty()) {
            deleteRows.accept(removedIds);
        }
        return removedIds.size();
    }

    /** Purges batch after batch until a batch is not removed completely or the run limit is reached. */
    public int purgeAll(Function<Pageable, List<DeletedFile>> findDeleted, Consumer<List<Long>> deleteRows) {
        int removed = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<DeletedFile> files = findDeleted.apply(PageRequest.of(0, BATCH_SIZE));
            int removedInBatch = files.isEmpty() ? 0 : purge(files, deleteRows);
            removed += removedInBatch;
            if (removedInBatch < BATCH_SIZE) {
                break;
            }
        }
        return removed;
    }
}
