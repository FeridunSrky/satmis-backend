package com.satmis.comment;

import com.satmis.comment.dto.CommentRequest;
import com.satmis.comment.dto.CommentResponse;
import com.satmis.comment.dto.MyCommentResponse;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings/{listingId}/comments")
@RequiredArgsConstructor
public class ListingCommentController {

    private final ListingCommentService commentService;
    private final ListingCommentQueryService commentQueryService;

    // Public endpoint: principal is null for anonymous visitors
    @GetMapping
    public PageResponse<CommentResponse> getPublished(@PathVariable Long listingId,
                                                      @AuthenticationPrincipal CustomUserDetails principal,
                                                      @Valid @ParameterObject PagingRequest pagingRequest) {
        return commentQueryService.getPublished(listingId, principal == null ? null : principal.getId(),
                pagingRequest);
    }

    // Waits for approval; the author follows it under GET /api/users/me/comments
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MyCommentResponse create(@PathVariable Long listingId,
                                    @AuthenticationPrincipal CustomUserDetails principal,
                                    @Valid @RequestBody CommentRequest request) {
        return commentService.create(principal.getId(), listingId, request);
    }
}
