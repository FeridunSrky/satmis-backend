package com.satmis.comment;

import com.satmis.comment.dto.AdminCommentResponse;
import com.satmis.comment.dto.CommentModerationQueueRequest;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.listing.dto.ApproveListingRequest;
import com.satmis.listing.dto.RejectListingRequest;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Same request bodies as listing moderation: the reviewed version, and for a rejection the reason the author sees
@RestController
@RequestMapping("/api/admin/comments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentModerationService moderationService;

    @GetMapping
    public PageResponse<AdminCommentResponse> getQueue(@ParameterObject CommentModerationQueueRequest request,
                                                       @Valid @ParameterObject PagingRequest pagingRequest) {
        return moderationService.getQueue(request.statusOrDefault(), request.listingId(), pagingRequest);
    }

    @PostMapping("/{commentId}/approve")
    public AdminCommentResponse approve(@PathVariable Long commentId,
                                        @AuthenticationPrincipal CustomUserDetails principal,
                                        @Valid @RequestBody ApproveListingRequest request) {
        return moderationService.approve(commentId, principal.getId(), request.version());
    }

    @PostMapping("/{commentId}/reject")
    public AdminCommentResponse reject(@PathVariable Long commentId,
                                       @AuthenticationPrincipal CustomUserDetails principal,
                                       @Valid @RequestBody RejectListingRequest request) {
        return moderationService.reject(commentId, principal.getId(), request.version(), request.reason());
    }
}
