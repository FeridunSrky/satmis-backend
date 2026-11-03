package com.satmis.comment;

import com.satmis.comment.dto.AttachmentResponse;
import com.satmis.listing.dto.ImageUploadRequest;
import com.satmis.listing.dto.ImageUploadResponse;
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

// The file itself never passes through the API: the browser uploads it straight to storage
@RestController
@RequestMapping("/api/comment-attachments")
@RequiredArgsConstructor
public class CommentAttachmentController {

    private final CommentAttachmentService attachmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse requestUpload(@AuthenticationPrincipal CustomUserDetails principal,
                                             @Valid @RequestBody ImageUploadRequest request) {
        return attachmentService.requestUpload(principal.getId(), request);
    }

    @PostMapping("/{attachmentId}/complete")
    public AttachmentResponse completeUpload(@PathVariable Long attachmentId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return attachmentService.completeUpload(principal.getId(), attachmentId);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUpload(@PathVariable Long attachmentId, @AuthenticationPrincipal CustomUserDetails principal) {
        attachmentService.deleteUpload(principal.getId(), attachmentId);
    }
}
