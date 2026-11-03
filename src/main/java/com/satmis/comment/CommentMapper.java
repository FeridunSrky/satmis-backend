package com.satmis.comment;

import com.satmis.comment.dto.AdminCommentResponse;
import com.satmis.comment.dto.AttachmentResponse;
import com.satmis.comment.dto.CommentReplyResponse;
import com.satmis.comment.dto.CommentResponse;
import com.satmis.comment.dto.MyCommentResponse;
import com.satmis.storage.ObjectStorage;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

// Attachments are passed in rather than read from the entity, so lists load them in one query
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, imports = AuthorNames.class)
public interface CommentMapper {

    // Only the storage key is persisted; the public address comes from configuration
    @Mapping(target = "type", source = "fileType")
    @Mapping(target = "url", expression = "java(objectStorage.publicUrl(attachment.getObjectKey()))")
    AttachmentResponse toAttachment(CommentAttachment attachment, @Context ObjectStorage objectStorage);

    List<AttachmentResponse> toAttachments(List<CommentAttachment> attachments, @Context ObjectStorage objectStorage);

    @Mapping(target = "id", source = "comment.id")
    @Mapping(target = "authorName", expression = "java(AuthorNames.mask(comment.getAuthor()))")
    @Mapping(target = "body", source = "comment.body")
    @Mapping(target = "createdAt", source = "comment.createdAt")
    @Mapping(target = "attachments", source = "attachmentResponses")
    @Mapping(target = "reply", source = "replyResponse")
    @Mapping(target = "canReply", source = "canReply")
    CommentResponse toPublished(ListingComment comment, List<AttachmentResponse> attachmentResponses,
                                CommentReplyResponse replyResponse, boolean canReply);

    @Mapping(target = "id", source = "reply.id")
    @Mapping(target = "body", source = "reply.body")
    @Mapping(target = "createdAt", source = "reply.createdAt")
    @Mapping(target = "attachments", source = "attachmentResponses")
    CommentReplyResponse toReply(ListingComment reply, List<AttachmentResponse> attachmentResponses);

    @Mapping(target = "id", source = "comment.id")
    @Mapping(target = "listingId", source = "comment.listing.id")
    @Mapping(target = "listingTitle", source = "comment.listing.title")
    @Mapping(target = "parentId", source = "comment.parent.id")
    @Mapping(target = "body", source = "comment.body")
    @Mapping(target = "status", source = "comment.status")
    @Mapping(target = "rejectionReason", source = "comment.rejectionReason")
    @Mapping(target = "createdAt", source = "comment.createdAt")
    @Mapping(target = "attachments", source = "attachmentResponses")
    MyCommentResponse toMine(ListingComment comment, List<AttachmentResponse> attachmentResponses);

    @Mapping(target = "id", source = "comment.id")
    @Mapping(target = "version", source = "comment.version")
    @Mapping(target = "listingId", source = "comment.listing.id")
    @Mapping(target = "listingTitle", source = "comment.listing.title")
    @Mapping(target = "listingStatus", source = "comment.listing.status")
    @Mapping(target = "author", source = "comment.author")
    @Mapping(target = "parent", source = "comment.parent")
    @Mapping(target = "body", source = "comment.body")
    @Mapping(target = "status", source = "comment.status")
    @Mapping(target = "rejectionReason", source = "comment.rejectionReason")
    @Mapping(target = "createdAt", source = "comment.createdAt")
    @Mapping(target = "reviewedAt", source = "comment.reviewedAt")
    @Mapping(target = "attachments", source = "attachmentResponses")
    AdminCommentResponse toAdmin(ListingComment comment, List<AttachmentResponse> attachmentResponses);

    @Mapping(target = "authorName", expression = "java(AuthorNames.mask(parent.getAuthor()))")
    AdminCommentResponse.ParentComment toParent(ListingComment parent);
}
