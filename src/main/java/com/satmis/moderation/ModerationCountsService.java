package com.satmis.moderation;

import com.satmis.comment.ListingCommentRepository;
import com.satmis.enums.CommentStatus;
import com.satmis.enums.ListingStatus;
import com.satmis.enums.ReportStatus;
import com.satmis.listing.ListingRepository;
import com.satmis.moderation.dto.ModerationCountsResponse;
import com.satmis.report.ListingReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Same numbers as the totals of the three admin queues, in one call
@Service
@RequiredArgsConstructor
public class ModerationCountsService {

    private final ListingRepository listingRepository;
    private final ListingCommentRepository commentRepository;
    private final ListingReportRepository reportRepository;

    @Transactional(readOnly = true)
    public ModerationCountsResponse getCounts() {
        return new ModerationCountsResponse(
                listingRepository.countByStatus(ListingStatus.PENDING_APPROVAL),
                commentRepository.countByStatus(CommentStatus.PENDING_APPROVAL),
                reportRepository.countListingsWithStatus(ReportStatus.OPEN));
    }
}
