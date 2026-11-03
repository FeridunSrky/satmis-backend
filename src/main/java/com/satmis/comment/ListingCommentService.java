package com.satmis.comment;

import com.satmis.comment.dto.CommentRequest;
import com.satmis.comment.dto.MyCommentResponse;
import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import com.satmis.enums.CommentStatus;
import com.satmis.listing.Listing;
import com.satmis.listing.ListingRepository;
import com.satmis.storage.ObjectStorage;
import com.satmis.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// Write side of comments; every comment and reply waits for an admin before anyone else sees it
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ListingCommentProperties.class)
public class ListingCommentService {

    static final Set<CommentStatus> ACTIVE_REPLY = EnumSet.of(CommentStatus.PENDING_APPROVAL, CommentStatus.APPROVED);
    private static final int MIN_BODY_LENGTH = 20;

    private final ListingCommentRepository commentRepository;
    private final ListingRepository listingRepository;
    private final CommentAttachmentService attachmentService;
    private final CommentAuthorPolicy commentAuthorPolicy;
    private final ListingCommentProperties properties;
    private final CommentMapper commentMapper;
    private final ObjectStorage objectStorage;

    // The slot is taken first; any later failure rolls the whole request back, the slot included
    @Transactional
    public MyCommentResponse create(Long userId, Long listingId, CommentRequest request) {
        Listing listing = listingRepository.findById(listingId)
                .filter(CommentVisibility::acceptsComments)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı"));
        if (listing.getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Kendi ilanınıza yorum yapamazsınız; gelen yorumlara cevap verebilirsiniz");
        }
        User author = commentAuthorPolicy.requireAllowedToComment(userId);
        String body = normalizedBody(request.body());
        validateAttachmentCount(request.attachmentIdsOrEmpty());
        if (commentRepository.consumeQuota(listingId, userId, properties.maxCommentsPerListing()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.COMMENT_LIMIT_REACHED,
                    "Bu ilana en fazla " + properties.maxCommentsPerListing() + " yorum yapabilirsiniz");
        }
        return save(newComment(listing, author, null, body), request.attachmentIdsOrEmpty());
    }

    /**
     * The owner answers a published comment. A concurrent second reply fails on the comment's version or on the
     * partial unique index (409 either way).
     */
    @Transactional
    public MyCommentResponse reply(Long userId, Long commentId, CommentRequest request) {
        ListingComment parent = commentRepository.findWithContextById(commentId)
                .filter(ListingCommentService::isPublished)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Yorum bulunamadı"));
        Listing listing = parent.getListing();
        if (!listing.getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Yorumlara sadece ilan sahibi cevap verebilir");
        }
        if (commentRepository.existsByParentIdAndStatusIn(commentId, ACTIVE_REPLY)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu yoruma zaten cevap verdiniz");
        }
        if (parent.getReplyAttempts() >= properties.maxReplyAttempts()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REPLY_LIMIT_REACHED,
                    "Bu yoruma cevap hakkınız kalmadı");
        }
        User owner = commentAuthorPolicy.requireAllowedToComment(userId);
        String body = normalizedBody(request.body());
        validateAttachmentCount(request.attachmentIdsOrEmpty());
        parent.recordReplyAttempt();
        return save(newComment(listing, owner, parent, body), request.attachmentIdsOrEmpty());
    }

    // Any status; deleting a comment also deletes its reply. Used slots and reply attempts are not given back.
    @Transactional
    public void delete(Long userId, Long commentId) {
        ListingComment comment = commentRepository.findById(commentId)
                .filter(found -> found.getAuthor().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Yorum bulunamadı"));
        List<Long> replyIds = comment.isReply() ? List.of() : commentRepository
                .findRepliesByParentIdIn(List.of(commentId), EnumSet.allOf(CommentStatus.class)).stream()
                .map(ListingComment::getId)
                .toList();
        attachmentService.releaseForComments(Stream.concat(Stream.of(commentId), replyIds.stream()).toList());
        commentRepository.delete(comment);
    }

    private MyCommentResponse save(ListingComment comment, List<Long> attachmentIds) {
        commentRepository.save(comment);
        attachmentService.attach(comment, attachmentIds);
        commentRepository.flush();
        return commentMapper.toMine(comment, commentMapper.toAttachments(comment.getAttachments(), objectStorage));
    }

    private static ListingComment newComment(Listing listing, User author, ListingComment parent, String body) {
        ListingComment comment = new ListingComment();
        comment.setListing(listing);
        comment.setAuthor(author);
        comment.setParent(parent);
        comment.setBody(body);
        return comment;
    }

    // Replies need a comment everyone can see, on a listing whose comments are still shown
    private static boolean isPublished(ListingComment comment) {
        return !comment.isReply()
                && comment.getStatus() == CommentStatus.APPROVED
                && comment.getAuthor().isEnabled()
                && CommentVisibility.isShownOn(comment.getListing());
    }

    // Bean Validation counts surrounding spaces, so the length is checked again on the stripped text
    private static String normalizedBody(String body) {
        String stripped = body.strip();
        if (stripped.length() < MIN_BODY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Yorum 20 ile 2000 karakter arasında olmalıdır");
        }
        return stripped;
    }

    private void validateAttachmentCount(List<Long> attachmentIds) {
        if (attachmentIds.size() > properties.maxAttachmentsPerComment()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Bir yoruma en fazla " + properties.maxAttachmentsPerComment() + " dosya eklenebilir");
        }
    }
}
