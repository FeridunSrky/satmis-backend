package com.satmis.report.dto;

import com.satmis.enums.ReportReason;
import com.satmis.listing.dto.ListingSummaryResponse;

import java.time.Instant;
import java.util.Map;

// Open reports only; reasons maps each reason to how many open reports gave it
public record ReportedListingResponse(
        ListingSummaryResponse listing,
        long openReportCount,
        Map<ReportReason, Long> reasons,
        Instant firstReportedAt,
        Instant lastReportedAt
) {
}
