package com.satmis.report;

import com.satmis.common.ApiException;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.enums.ReportReason;
import com.satmis.enums.ReportStatus;
import com.satmis.listing.ListingModerationService;
import com.satmis.listing.ListingQueryService;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingSummaryResponse;
import com.satmis.report.dto.AdminListingReportResponse;
import com.satmis.report.dto.ReportedListingResponse;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Reports are resolved per listing: one decision closes every open report of that listing
@Service
@RequiredArgsConstructor
public class ReportModerationService {

    private final ListingReportRepository listingReportRepository;
    private final ListingQueryService listingQueryService;
    private final ListingModerationService listingModerationService;
    private final UserRepository userRepository;
    private final ReportMapper reportMapper;

    @Transactional(readOnly = true)
    public PageResponse<ReportedListingResponse> getReportedListings(PagingRequest pagingRequest) {
        Page<ReportedListingStats> stats = listingReportRepository.findListingStats(ReportStatus.OPEN,
                pagingRequest.toPageable());
        List<Long> listingIds = stats.getContent().stream().map(ReportedListingStats::listingId).toList();
        Map<Long, ListingSummaryResponse> summaries = summariesById(stats, listingIds);
        Map<Long, Map<ReportReason, Long>> reasons = reasonsByListing(listingIds);

        List<ReportedListingResponse> content = stats.getContent().stream()
                .filter(stat -> summaries.containsKey(stat.listingId()))
                .map(stat -> new ReportedListingResponse(summaries.get(stat.listingId()), stat.reportCount(),
                        reasons.getOrDefault(stat.listingId(), Map.of()), stat.firstReportedAt(),
                        stat.lastReportedAt()))
                .toList();
        return PageResponse.of(content, stats);
    }

    // Every report of the listing, resolved ones included, newest first
    @Transactional(readOnly = true)
    public PageResponse<AdminListingReportResponse> getReports(Long listingId, PagingRequest pagingRequest) {
        Page<ListingReport> reports = listingReportRepository.findByListingIdWithReporter(listingId,
                pagingRequest.toPageable());
        return PageResponse.of(reports.getContent().stream().map(reportMapper::toAdminResponse).toList(), reports);
    }

    @Transactional
    public void dismiss(Long listingId, Long adminId) {
        if (resolveOpenReports(listingId, ReportStatus.DISMISSED, adminId) == 0) {
            throw noOpenReports();
        }
    }

    // Uses the normal rejection, so the owner sees the reason and an edit sends the listing back to approval
    @Transactional
    public ListingDetailResponse takeDown(Long listingId, Long adminId, Long reviewedVersion, String reason) {
        if (!listingReportRepository.existsByListingIdAndStatus(listingId, ReportStatus.OPEN)) {
            throw noOpenReports();
        }
        ListingDetailResponse detail = listingModerationService.reject(listingId, adminId, reviewedVersion, reason);
        resolveOpenReports(listingId, ReportStatus.ACTION_TAKEN, adminId);
        return detail;
    }

    private int resolveOpenReports(Long listingId, ReportStatus resolution, Long adminId) {
        return listingReportRepository.resolveOpen(listingId, resolution, userRepository.getReferenceById(adminId),
                Instant.now());
    }

    // Reuses the listing summaries; a listing deleted in between simply drops out of the page
    private Map<Long, ListingSummaryResponse> summariesById(Page<ReportedListingStats> stats, List<Long> listingIds) {
        Page<Long> idPage = new PageImpl<>(listingIds, stats.getPageable(), stats.getTotalElements());
        return listingQueryService.toSummaryPage(idPage).content().stream()
                .collect(Collectors.toMap(ListingSummaryResponse::id, Function.identity()));
    }

    private Map<Long, Map<ReportReason, Long>> reasonsByListing(List<Long> listingIds) {
        Map<Long, Map<ReportReason, Long>> reasons = new HashMap<>();
        if (listingIds.isEmpty()) {
            return reasons;
        }
        for (ReasonCount count : listingReportRepository.countReasons(ReportStatus.OPEN, listingIds)) {
            reasons.computeIfAbsent(count.listingId(), id -> new EnumMap<>(ReportReason.class))
                    .put(count.reason(), count.count());
        }
        return reasons;
    }

    private static ApiException noOpenReports() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Bu ilan için açık şikayet yok");
    }
}
