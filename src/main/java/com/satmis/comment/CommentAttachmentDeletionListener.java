package com.satmis.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// After commit: a rolled-back request never loses a file. Async: storage latency never reaches the response.
// Whatever fails here stays DELETED and is retried by CommentAttachmentCleanupJob.
@Component
@RequiredArgsConstructor
public class CommentAttachmentDeletionListener {

    private final CommentAttachmentCleanupService cleanupService;

    @Async
    @TransactionalEventListener
    public void onAttachmentsDeleted(CommentAttachmentsDeletedEvent event) {
        cleanupService.purge(event.attachments());
    }
}
