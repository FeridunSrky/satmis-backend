package com.satmis.comment;

import com.satmis.enums.CommentAttachmentStatus;
import com.satmis.storage.StoredFilePurger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.EnumSet;

// Without the scheduled run, abandoned uploads would fill the storage and files whose removal failed would stay
// public. Safe on several instances at once: storage deletes and the guarded row deletes are idempotent.
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentAttachmentCleanupService {

    private final CommentAttachmentRepository attachmentRepository;
    private final StoredFilePurger storedFilePurger;
    private final CommentAttachmentProperties properties;

    // After commit: a rolled-back request never loses a file. Async: storage latency never reaches the response.
    // Whatever fails here stays DELETED and is retried by cleanUp.
    @Async
    @TransactionalEventListener
    public void onAttachmentsDeleted(CommentAttachmentsDeletedEvent event) {
        storedFilePurger.purge(event.attachments(), attachmentRepository::deleteDeletedByIdIn);
    }

    // Abandoned uploads (never sent in a comment) become DELETED first, then every DELETED attachment is purged
    @Scheduled(cron = "${app.comment-attachments.cleanup-cron}", zone = "UTC")
    public void cleanUp() {
        Instant createdBefore = Instant.now().minus(properties.abandonedAfter());
        int abandoned = attachmentRepository.markDeleted(
                EnumSet.of(CommentAttachmentStatus.PENDING, CommentAttachmentStatus.UPLOADED), createdBefore);
        int removed = storedFilePurger.purgeAll(attachmentRepository::findDeleted,
                attachmentRepository::deleteDeletedByIdIn);
        log.info("Comment attachment cleanup: {} abandoned uploads marked deleted, {} attachments removed",
                abandoned, removed);
    }
}
