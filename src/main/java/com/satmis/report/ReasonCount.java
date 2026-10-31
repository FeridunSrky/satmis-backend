package com.satmis.report;

import com.satmis.enums.ReportReason;

public record ReasonCount(Long listingId, ReportReason reason, long count) {
}
