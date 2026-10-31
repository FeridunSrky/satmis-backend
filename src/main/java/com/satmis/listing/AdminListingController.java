package com.satmis.listing;

import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.listing.dto.ApproveListingRequest;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingSummaryResponse;
import com.satmis.listing.dto.ModerationQueueRequest;
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

@RestController
@RequestMapping("/api/admin/listings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminListingController {

    private final ListingModerationService listingModerationService;

    @GetMapping
    public PageResponse<ListingSummaryResponse> getQueue(@ParameterObject ModerationQueueRequest request,
                                                         @Valid @ParameterObject PagingRequest pagingRequest) {
        return listingModerationService.getQueue(request.statusOrDefault(), pagingRequest);
    }

    @GetMapping("/{listingId}")
    public ListingDetailResponse getDetail(@PathVariable Long listingId,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return listingModerationService.getDetail(listingId, principal.getId());
    }

    @PostMapping("/{listingId}/approve")
    public ListingDetailResponse approve(@PathVariable Long listingId,
                                         @AuthenticationPrincipal CustomUserDetails principal,
                                         @Valid @RequestBody ApproveListingRequest request) {
        return listingModerationService.approve(listingId, principal.getId(), request.version());
    }

    @PostMapping("/{listingId}/reject")
    public ListingDetailResponse reject(@PathVariable Long listingId,
                                        @AuthenticationPrincipal CustomUserDetails principal,
                                        @Valid @RequestBody RejectListingRequest request) {
        return listingModerationService.reject(listingId, principal.getId(), request.version(), request.reason());
    }
}
