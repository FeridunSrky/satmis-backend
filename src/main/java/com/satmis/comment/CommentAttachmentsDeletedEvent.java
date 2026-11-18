package com.satmis.comment;

import com.satmis.storage.DeletedFile;

import java.util.List;

// Published when attachments become DELETED; their stored files are removed once the transaction commits
public record CommentAttachmentsDeletedEvent(List<DeletedFile> attachments) {
}
