package com.satmis.comment;

import com.satmis.comment.dto.AttachmentResponse;
import com.satmis.comment.dto.CommentQuotaResponse;
import com.satmis.comment.dto.CommentReplyResponse;
import com.satmis.comment.dto.CommentResponse;
import com.satmis.comment.dto.MyCommentResponse;
import com.satmis.common.ApiException;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.enums.CommentStatus;
import com.satmis.listing.Listing;
import com.satmis.listing.ListingRepository;
import com.satmis.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Lists load a page of ids first, then comments, replies and attachments in one query each
@Service
@RequiredArgsConstructor
public class ListingCommentQueryService {

    private final ListingCommentRepository commentRepository;
    private final CommentAttachmentRepository attachmentRepository;
    private final ListingRepository listingRepository;
    private final ListingCommentProperties properties;
    private final CommentMapper commentMapper;
    private final ObjectStorage objectStorage;

    // viewerId may be null (anonymous); it only decides canReply
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getPublished(Long listingId, Long viewerId, PagingRequest pagingRequest) {
        Listing listing = listingRepository.findById(listingId)
                .filter(CommentVisibility::isShownOn)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı"));
        Page<Long> idPage = commentRepository.findPublishedIds(listingId, pagingRequest.toPageable());
        if (idPage.isEmpty()) {
            return PageResponse.of(List.of(), idPage);
        }
        Map<Long, ListingComment> commentsById = byId(commentRepository.findWithAuthorByIdIn(idPage.getContent()));
        Map<Long, ListingComment> activeRepliesByParent = commentRepository
                .findRepliesByParentIdIn(idPage.getContent(), ListingCommentService.ACTIVE_REPLY).stream()
                .collect(Collectors.toMap(reply -> reply.getParent().getId(), Function.identity()));
        List<Long> shownReplyIds = activeRepliesByParent.values().stream()
                .filter(reply -> reply.getStatus() == CommentStatus.APPROVED)
                .map(ListingComment::getId)
                .toList();
        Map<Long, List<AttachmentResponse>> attachments =
                attachmentsByComment(Stream.concat(idPage.getContent().stream(), shownReplyIds.stream()).toList());
        boolean viewerIsOwner = listing.getOwner().getId().equals(viewerId);

        List<CommentResponse> content = idPage.getContent().stream()
                .map(commentsById::get)
                .filter(Objects::nonNull)
                .map(comment -> toPublished(comment, activeRepliesByParent.get(comment.getId()), attachments,
                        viewerIsOwner))
                .toList();
        return PageResponse.of(content, idPage);
    }

    // The author's own comments and replies in every status, newest first
    @Transactional(readOnly = true)
    public PageResponse<MyCommentResponse> getMine(Long userId, PagingRequest pagingRequest) {
        Page<ListingComment> comments = commentRepository.findByAuthorWithListing(userId, pagingRequest.toPageable());
        Map<Long, List<AttachmentResponse>> attachments =
                attachmentsByComment(comments.getContent().stream().map(ListingComment::getId).toList());
        List<MyCommentResponse> content = comments.getContent().stream()
                .map(comment -> commentMapper.toMine(comment, attachments.getOrDefault(comment.getId(), List.of())))
                .toList();
        return PageResponse.of(content, comments);
    }

    @Transactional(readOnly = true)
    public CommentQuotaResponse getQuota(Long userId, Long listingId) {
        if (!listingRepository.existsById(listingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı");
        }
        int max = properties.maxCommentsPerListing();
        int used = commentRepository.findUsedQuota(listingId, userId).orElse(0);
        return new CommentQuotaResponse(listingId, max, used, Math.max(0, max - used));
    }

    private CommentResponse toPublished(ListingComment comment, ListingComment activeReply,
                                        Map<Long, List<AttachmentResponse>> attachments, boolean viewerIsOwner) {
        CommentReplyResponse reply = activeReply != null && activeReply.getStatus() == CommentStatus.APPROVED
                ? commentMapper.toReply(activeReply, attachments.getOrDefault(activeReply.getId(), List.of()))
                : null;
        boolean canReply = viewerIsOwner && activeReply == null
                && comment.getReplyAttempts() < properties.maxReplyAttempts();
        return commentMapper.toPublished(comment, attachments.getOrDefault(comment.getId(), List.of()), reply, canReply);
    }

    Map<Long, List<AttachmentResponse>> attachmentsByComment(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getComment().getId(),
                        Collectors.mapping(attachment -> commentMapper.toAttachment(attachment, objectStorage),
                                Collectors.toList())));
    }

    private static Map<Long, ListingComment> byId(List<ListingComment> comments) {
        return comments.stream().collect(Collectors.toMap(ListingComment::getId, Function.identity()));
    }
}
