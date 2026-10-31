package com.satmis.listing;

import com.satmis.listing.dto.ImageUploadRequest;
import com.satmis.listing.dto.ImageUploadResponse;
import com.satmis.listing.dto.UploadedImageResponse;
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
@RequestMapping("/api/listing-images")
@RequiredArgsConstructor
public class ListingImageController {

    private final ListingImageService listingImageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse requestUpload(@AuthenticationPrincipal CustomUserDetails principal,
                                             @Valid @RequestBody ImageUploadRequest request) {
        return listingImageService.requestUpload(principal.getId(), request);
    }

    @PostMapping("/{imageId}/complete")
    public UploadedImageResponse completeUpload(@PathVariable Long imageId,
                                                @AuthenticationPrincipal CustomUserDetails principal) {
        return listingImageService.completeUpload(principal.getId(), imageId);
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUpload(@PathVariable Long imageId, @AuthenticationPrincipal CustomUserDetails principal) {
        listingImageService.deleteUpload(principal.getId(), imageId);
    }
}
