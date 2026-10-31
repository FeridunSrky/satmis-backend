package com.satmis.report;

import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.RejectListingRequest;
import com.satmis.report.dto.AdminListingReportResponse;
import com.satmis.report.dto.ReportedListingResponse;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportModerationService reportModerationService;

    @GetMapping
    public PageResponse<ReportedListingResponse> getReportedListings(@Valid @ParameterObject PagingRequest pagingRequest) {
        return reportModerationService.getReportedListings(pagingRequest);
    }

    @GetMapping("/listings/{listingId}")
    public PageResponse<AdminListingReportResponse> getReports(@PathVariable Long listingId,
                                                               @Valid @ParameterObject PagingRequest pagingRequest) {
        return reportModerationService.getReports(listingId, pagingRequest);
    }

    @PostMapping("/listings/{listingId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable Long listingId, @AuthenticationPrincipal CustomUserDetails principal) {
        reportModerationService.dismiss(listingId, principal.getId());
    }

    // Same body as a listing rejection: the reviewed version and the reason the owner will see
    @PostMapping("/listings/{listingId}/take-down")
    public ListingDetailResponse takeDown(@PathVariable Long listingId,
                                          @AuthenticationPrincipal CustomUserDetails principal,
                                          @Valid @RequestBody RejectListingRequest request) {
        return reportModerationService.takeDown(listingId, principal.getId(), request.version(), request.reason());
    }
}
