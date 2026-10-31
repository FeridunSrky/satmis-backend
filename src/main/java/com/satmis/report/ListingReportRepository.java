package com.satmis.report;

import com.satmis.enums.ReportStatus;
import com.satmis.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    boolean existsByListingIdAndReporterIdAndStatus(Long listingId, Long reporterId, ReportStatus status);

    boolean existsByListingIdAndStatus(Long listingId, ReportStatus status);

    // Most reported first, then most recently reported; id keeps paging stable
    @Query(value = """
            select new com.satmis.report.ReportedListingStats(r.listing.id, count(r), min(r.createdAt), max(r.createdAt))
            from ListingReport r
            where r.status = :status
            group by r.listing.id
            order by count(r) desc, max(r.createdAt) desc, r.listing.id desc""",
            countQuery = "select count(distinct r.listing.id) from ListingReport r where r.status = :status")
    Page<ReportedListingStats> findListingStats(@Param("status") ReportStatus status, Pageable pageable);

    @Query("""
            select new com.satmis.report.ReasonCount(r.listing.id, r.reason, count(r))
            from ListingReport r
            where r.status = :status and r.listing.id in :listingIds
            group by r.listing.id, r.reason""")
    List<ReasonCount> countReasons(@Param("status") ReportStatus status,
                                   @Param("listingIds") Collection<Long> listingIds);

    @Query(value = """
            select r from ListingReport r
            join fetch r.reporter
            where r.listing.id = :listingId
            order by r.createdAt desc, r.id desc""",
            countQuery = "select count(r) from ListingReport r where r.listing.id = :listingId")
    Page<ListingReport> findByListingIdWithReporter(@Param("listingId") Long listingId, Pageable pageable);

    // Bulk update skips auditing, so updatedAt is set here
    @Modifying
    @Query("""
            update ListingReport r
            set r.status = :resolution, r.resolvedBy = :admin, r.resolvedAt = :now, r.updatedAt = :now
            where r.listing.id = :listingId and r.status = com.satmis.enums.ReportStatus.OPEN""")
    int resolveOpen(@Param("listingId") Long listingId, @Param("resolution") ReportStatus resolution,
                    @Param("admin") User admin, @Param("now") Instant now);
}
