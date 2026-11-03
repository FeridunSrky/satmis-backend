package com.satmis.comment;

import com.satmis.comment.dto.CommentQuotaResponse;
import com.satmis.comment.dto.MyCommentResponse;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/comments")
@RequiredArgsConstructor
public class MyCommentController {

    private final ListingCommentQueryService commentQueryService;

    @GetMapping
    public PageResponse<MyCommentResponse> getMine(@AuthenticationPrincipal CustomUserDetails principal,
                                                   @Valid @ParameterObject PagingRequest pagingRequest) {
        return commentQueryService.getMine(principal.getId(), pagingRequest);
    }

    // How many comments the user may still send on this listing
    @GetMapping("/quota")
    public CommentQuotaResponse getQuota(@AuthenticationPrincipal CustomUserDetails principal,
                                         @RequestParam Long listingId) {
        return commentQueryService.getQuota(principal.getId(), listingId);
    }
}
