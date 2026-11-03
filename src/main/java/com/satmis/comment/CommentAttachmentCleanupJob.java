package com.satmis.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Without this, abandoned uploads would fill the storage and files whose removal failed would stay public.
// Safe on several instances at once: storage deletes and the guarded row deletes are idempotent.
@Component
@RequiredArgsConstructor
public class CommentAttachmentCleanupJob {

    private final CommentAttachmentCleanupService cleanupService;

    @Scheduled(cron = "${app.comment-attachments.cleanup-cron}", zone = "UTC")
    public void cleanUp() {
        cleanupService.cleanUp();
    }
}
