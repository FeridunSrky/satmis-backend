package com.satmis.comment;

import com.satmis.enums.CommentAttachmentStatus;
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
 * Removes the stored files of DELETED attachments, then their rows. A row is only dropped after its file is gone,
 * so a failed storage call is retried by the next run. Runs without a transaction so no database connection is
 * held during storage calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentAttachmentCleanupService {

    private static final int BATCH_SIZE = 100;
    // Bounds one run; anything left over is picked up an hour later
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final CommentAttachmentRepository attachmentRepository;
    private final ObjectStorage objectStorage;
    private final CommentAttachmentProperties properties;

    /** Returns how many attachments were removed; stops at the first storage failure. */
    public int purge(List<DeletedAttachmentView> attachments) {
        List<Long> removedIds = new ArrayList<>();
        try {
            for (DeletedAttachmentView attachment : attachments) {
                objectStorage.delete(attachment.objectKey());
                removedIds.add(attachment.id());
            }
        } catch (StorageException ex) {
            log.warn("Removing stored comment attachments failed, {} left for the next cleanup run: {}",
                    attachments.size() - removedIds.size(), ex.getMessage());
        }
        if (!removedIds.isEmpty()) {
            attachmentRepository.deleteDeletedByIdIn(removedIds);
        }
        return removedIds.size();
    }

    // Abandoned uploads (never sent in a comment) become DELETED first, then every DELETED attachment is purged
    public void cleanUp() {
        Instant createdBefore = Instant.now().minus(properties.abandonedAfter());
        int abandoned = attachmentRepository.markDeleted(
                EnumSet.of(CommentAttachmentStatus.PENDING, CommentAttachmentStatus.UPLOADED), createdBefore);
        int removed = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<DeletedAttachmentView> attachments = attachmentRepository.findDeleted(PageRequest.of(0, BATCH_SIZE));
            int removedInBatch = attachments.isEmpty() ? 0 : purge(attachments);
            removed += removedInBatch;
            if (removedInBatch < BATCH_SIZE) {
                break;
            }
        }
        log.info("Comment attachment cleanup: {} abandoned uploads marked deleted, {} attachments removed",
                abandoned, removed);
    }
}
