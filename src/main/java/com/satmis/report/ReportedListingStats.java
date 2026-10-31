package com.satmis.report;

import java.time.Instant;

public record ReportedListingStats(Long listingId, long reportCount, Instant firstReportedAt, Instant lastReportedAt) {
}
