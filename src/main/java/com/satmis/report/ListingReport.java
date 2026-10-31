package com.satmis.report;

import com.satmis.common.AuditableEntity;
import com.satmis.enums.ReportReason;
import com.satmis.enums.ReportStatus;
import com.satmis.listing.Listing;
import com.satmis.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

// One open report per reporter and listing is enforced by a partial unique index (V10)
@Entity
@Table(name = "listing_reports",
        indexes = @Index(name = "idx_listing_reports_status_listing", columnList = "status, listing_id"))
@Getter
@Setter
@NoArgsConstructor
public class ListingReport extends AuditableEntity {

    // Deleting a listing removes its reports at the database level
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    private Instant resolvedAt;
}
