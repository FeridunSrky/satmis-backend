package com.satmis.report.dto;

import com.satmis.enums.ReportReason;
import com.satmis.enums.ReportStatus;

import java.time.Instant;

public record ListingReportResponse(
        Long id,
        Long listingId,
        ReportReason reason,
        String description,
        ReportStatus status,
        Instant createdAt
) {
}
