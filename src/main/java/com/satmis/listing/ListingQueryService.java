package com.satmis.listing;

import com.satmis.common.ApiException;
import com.satmis.common.PageResponse;
import com.satmis.enums.ListingStatus;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingPageRequest;
import com.satmis.listing.dto.ListingSearchCriteria;
import com.satmis.listing.dto.ListingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingQueryService {

    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingMapper listingMapper;
    private final ListingDetailAssembler listingDetailAssembler;

    // Not read-only: it also bumps the view counter. The response shows the count before this view.
    @Transactional
    public ListingDetailResponse getDetail(Long listingId, Long viewerId) {
        Listing listing = listingRepository.findDetailById(listingId).orElseThrow(ListingQueryService::notFound);
        boolean viewerIsOwner = listing.getOwner().getId().equals(viewerId);
        // 404 rather than 403 so others cannot tell a hidden listing exists
        if (listing.getStatus() != ListingStatus.ACTIVE && !viewerIsOwner) {
            throw notFound();
        }
        if (!viewerIsOwner) {
            listingRepository.incrementViewCount(listingId);
        }
        return listingDetailAssembler.assemble(listing, viewerId);
    }

    public PageResponse<ListingSummaryResponse> search(ListingSearchCriteria criteria, ListingPageRequest pageRequest) {
        Page<Long> idPage = listingRepository.findIds(
                ListingSpecifications.activeMatching(criteria), pageRequest.toPageable());
        return toSummaryPage(idPage);
    }

    public PageResponse<ListingSummaryResponse> getOwnListings(Long ownerId, ListingPageRequest pageRequest) {
        Page<Long> idPage = listingRepository.findIds(ListingSpecifications.ownedBy(ownerId), pageRequest.toPageable());
        return toSummaryPage(idPage);
    }

    // Fixed number of queries per page: ids + count, listings with their catalog data, cover images.
    // Public so other features (favorites) can page listing ids their own way and reuse the summary loading.
    public PageResponse<ListingSummaryResponse> toSummaryPage(Page<Long> idPage) {
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return PageResponse.of(List.of(), idPage);
        }
        Map<Long, Listing> listingsById = listingRepository.findSummariesByIdIn(ids).stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        Map<Long, String> coverUrlsById = listingImageRepository.findCoverImages(ids).stream()
                .collect(Collectors.toMap(CoverImageView::listingId, CoverImageView::url, (first, second) -> first));

        // Keeps the id query's order; skips rows deleted between the two queries
        List<ListingSummaryResponse> content = ids.stream()
                .map(listingsById::get)
                .filter(Objects::nonNull)
                .map(listing -> listingMapper.toSummary(listing, coverUrlsById.get(listing.getId())))
                .toList();
        return PageResponse.of(content, idPage);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı");
    }
}
