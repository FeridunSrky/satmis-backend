package com.satmis.comment;

import java.util.List;

// Published when attachments become DELETED; their stored files are removed once the transaction commits
public record CommentAttachmentsDeletedEvent(List<DeletedAttachmentView> attachments) {
}
