package com.satmis.comment;

import com.satmis.comment.dto.AdminCommentResponse;
import com.satmis.comment.dto.AttachmentResponse;
import com.satmis.common.ApiException;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.enums.CommentStatus;
import com.satmis.listing.Listing;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentModerationService {

    // Rejecting a published comment takes it down; approving needs a comment that is actually waiting
    private static final Set<CommentStatus> REJECTABLE = EnumSet.of(CommentStatus.PENDING_APPROVAL, CommentStatus.APPROVED);

    private final ListingCommentRepository commentRepository;
    private final ListingCommentQueryService commentQueryService;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<AdminCommentResponse> getQueue(CommentStatus status, Long listingId,
                                                       PagingRequest pagingRequest) {
        Page<Long> idPage = commentRepository.findIdsForModeration(status, listingId, pagingRequest.toPageable());
        if (idPage.isEmpty()) {
            return PageResponse.of(List.of(), idPage);
        }
        Map<Long, ListingComment> commentsById = commentRepository.findForModerationByIdIn(idPage.getContent())
                .stream().collect(Collectors.toMap(ListingComment::getId, Function.identity()));
        Map<Long, List<AttachmentResponse>> attachments = commentQueryService.attachmentsByComment(idPage.getContent());
        List<AdminCommentResponse> content = idPage.getContent().stream()
                .map(commentsById::get)
                .filter(Objects::nonNull)
                .map(comment -> commentMapper.toAdmin(comment, attachments.getOrDefault(comment.getId(), List.of())))
                .toList();
        return PageResponse.of(content, idPage);
    }

    /** A reply is only published under a comment that is itself still published. */
    @Transactional
    public AdminCommentResponse approve(Long commentId, Long adminId, Long reviewedVersion) {
        ListingComment comment = findReviewedComment(commentId, reviewedVersion);
        if (comment.getStatus() != CommentStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Sadece onay bekleyen yorumlar onaylanabilir");
        }
        if (comment.isReply() && comment.getParent().getStatus() != CommentStatus.APPROVED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Cevaplanan yorum yayında değil");
        }
        comment.approve(userRepository.getReferenceById(adminId), Instant.now());
        if (!comment.isReply()) {
            eventPublisher.publishEvent(commentApprovedEvent(comment.getListing()));
        }
        return flushAndMap(comment);
    }

    @Transactional
    public AdminCommentResponse reject(Long commentId, Long adminId, Long reviewedVersion, String reason) {
        ListingComment comment = findReviewedComment(commentId, reviewedVersion);
        if (!REJECTABLE.contains(comment.getStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Sadece onay bekleyen veya yayındaki yorumlar reddedilebilir");
        }
        comment.reject(userRepository.getReferenceById(adminId), reason.strip(), Instant.now());
        return flushAndMap(comment);
    }

    // The version the admin reviewed: comments are not edited, but a reply attempt changes the comment's version
    private ListingComment findReviewedComment(Long commentId, Long reviewedVersion) {
        ListingComment comment = commentRepository.findWithContextById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Yorum bulunamadı"));
        if (!comment.getVersion().equals(reviewedVersion)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Yorum siz incelerken değişti, güncel halini inceleyip tekrar deneyin");
        }
        return comment;
    }

    // Flush so the response carries the new version
    private AdminCommentResponse flushAndMap(ListingComment comment) {
        commentRepository.flush();
        return commentMapper.toAdmin(comment,
                commentQueryService.attachmentsByComment(List.of(comment.getId())).getOrDefault(comment.getId(), List.of()));
    }

    private static CommentApprovedEvent commentApprovedEvent(Listing listing) {
        return new CommentApprovedEvent(listing.getId(), listing.getTitle(), listing.getOwner().getEmail(),
                listing.getOwner().getFirstName());
    }
}
