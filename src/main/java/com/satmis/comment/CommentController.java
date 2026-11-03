package com.satmis.comment;

import com.satmis.comment.dto.CommentRequest;
import com.satmis.comment.dto.MyCommentResponse;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final ListingCommentService commentService;

    // Only the listing owner, once per published comment
    @PostMapping("/{commentId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public MyCommentResponse reply(@PathVariable Long commentId,
                                   @AuthenticationPrincipal CustomUserDetails principal,
                                   @Valid @RequestBody CommentRequest request) {
        return commentService.reply(principal.getId(), commentId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails principal) {
        commentService.delete(principal.getId(), commentId);
    }
}
