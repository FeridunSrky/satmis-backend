package com.satmis.listing;

import com.satmis.common.ApiException;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.enums.ListingStatus;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListingModerationService {

    // Rejecting a live listing takes it down; approving needs a listing that is actually waiting
    private static final Set<ListingStatus> REJECTABLE = EnumSet.of(ListingStatus.PENDING_APPROVAL, ListingStatus.ACTIVE);

    private final ListingRepository listingRepository;
    private final ListingQueryService listingQueryService;
    private final ListingDetailAssembler listingDetailAssembler;

    // Oldest first, so listings do not wait longer than necessary
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> getQueue(ListingStatus status, PagingRequest pagingRequest) {
        Pageable paging = pagingRequest.toPageable();
        Pageable pageable = PageRequest.of(paging.getPageNumber(), paging.getPageSize(), ListingSort.OLDEST.toSort());
        Page<Long> idPage = listingRepository.findIds(ListingSpecifications.withStatus(status), pageable);
        return listingQueryService.toSummaryPage(idPage);
    }

    // Any status, without counting a view; includes the seller's phone like any signed-in viewer gets
    @Transactional(readOnly = true)
    public ListingDetailResponse getDetail(Long listingId, Long adminId) {
        return listingDetailAssembler.assemble(findListing(listingId), adminId);
    }

    @Transactional
    public ListingDetailResponse approve(Long listingId, Long adminId, Long reviewedVersion) {
        Listing listing = findReviewedListing(listingId, reviewedVersion);
        if (listing.getStatus() != ListingStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Sadece onay bekleyen ilanlar onaylanabilir");
        }
        listing.approve();
        return flushAndAssemble(listing, adminId);
    }

    @Transactional
    public ListingDetailResponse reject(Long listingId, Long adminId, Long reviewedVersion, String reason) {
        Listing listing = findReviewedListing(listingId, reviewedVersion);
        if (!REJECTABLE.contains(listing.getStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Sadece onay bekleyen veya yayındaki ilanlar reddedilebilir");
        }
        listing.reject(reason.strip());
        return flushAndAssemble(listing, adminId);
    }

    private Listing findReviewedListing(Long listingId, Long reviewedVersion) {
        Listing listing = findListing(listingId);
        if (!listing.getVersion().equals(reviewedVersion)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "İlan siz incelerken değişti, güncel halini inceleyip tekrar deneyin");
        }
        return listing;
    }

    // Flush so the response carries the new version
    private ListingDetailResponse flushAndAssemble(Listing listing, Long adminId) {
        listingRepository.flush();
        return listingDetailAssembler.assemble(listing, adminId);
    }

    private Listing findListing(Long listingId) {
        return listingRepository.findDetailById(listingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı"));
    }
}
