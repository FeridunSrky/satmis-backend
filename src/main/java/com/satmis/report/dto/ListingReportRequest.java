package com.satmis.report.dto;

import com.satmis.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// A description is required only for OTHER; ListingReportService checks that
public record ListingReportRequest(
        @NotNull(message = "Şikayet nedeni zorunludur")
        ReportReason reason,

        @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir")
        String description
) {
}
