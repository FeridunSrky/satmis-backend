package com.satmis.report;

import com.satmis.common.ApiException;
import com.satmis.enums.ReportReason;
import com.satmis.enums.ReportStatus;
import com.satmis.listing.Listing;
import com.satmis.listing.ListingRepository;
import com.satmis.report.dto.ListingReportRequest;
import com.satmis.report.dto.ListingReportResponse;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListingReportService {

    private final ListingReportRepository listingReportRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final ReportMapper reportMapper;

    // A concurrent duplicate slips past the exists-check but hits the partial unique index (409 as well)
    @Transactional
    public ListingReportResponse report(Long reporterId, Long listingId, ListingReportRequest request) {
        Listing listing = findReportableListing(listingId, reporterId);
        String description = blankToNull(request.description());
        if (request.reason() == ReportReason.OTHER && description == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "\"Diğer\" seçildiğinde açıklama zorunludur");
        }
        if (listingReportRepository.existsByListingIdAndReporterIdAndStatus(listingId, reporterId, ReportStatus.OPEN)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu ilanı zaten şikayet ettiniz, inceleniyor");
        }

        ListingReport report = new ListingReport();
        report.setListing(listing);
        report.setReporter(userRepository.getReferenceById(reporterId));
        report.setReason(request.reason());
        report.setDescription(description);
        return reportMapper.toResponse(listingReportRepository.saveAndFlush(report));
    }

    // Hidden listings answer 404 like the detail endpoint, so reporting cannot reveal that they exist
    private Listing findReportableListing(Long listingId, Long reporterId) {
        Listing listing = listingRepository.findById(listingId)
                .filter(Listing::isPubliclyVisible)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı"));
        if (listing.getOwner().getId().equals(reporterId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Kendi ilanınızı şikayet edemezsiniz");
        }
        return listing;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
