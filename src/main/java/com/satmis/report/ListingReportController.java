package com.satmis.report;

import com.satmis.report.dto.ListingReportRequest;
import com.satmis.report.dto.ListingReportResponse;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// No Location header: reporters have no endpoint to read a report back
@RestController
@RequiredArgsConstructor
public class ListingReportController {

    private final ListingReportService listingReportService;

    @PostMapping("/api/listings/{listingId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingReportResponse report(@PathVariable Long listingId,
                                        @AuthenticationPrincipal CustomUserDetails principal,
                                        @Valid @RequestBody ListingReportRequest request) {
        return listingReportService.report(principal.getId(), listingId, request);
    }
}
