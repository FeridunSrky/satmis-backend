package com.satmis.report.dto;

import com.satmis.enums.ReportReason;
import com.satmis.enums.ReportStatus;

import java.time.Instant;

public record AdminListingReportResponse(
        Long id,
        ReportReason reason,
        String description,
        ReportStatus status,
        Reporter reporter,
        Instant createdAt,
        Instant resolvedAt
) {

    // Admins may contact the reporter, so the e-mail is included here only
    public record Reporter(Long id, String firstName, String lastName, String email) {
    }
}
