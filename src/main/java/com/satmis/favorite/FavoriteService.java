package com.satmis.favorite;

import com.satmis.common.ApiException;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.enums.ListingStatus;
import com.satmis.listing.Listing;
import com.satmis.listing.ListingQueryService;
import com.satmis.listing.ListingRepository;
import com.satmis.listing.dto.ListingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ListingRepository listingRepository;
    private final ListingQueryService listingQueryService;

    // Only listings the user can actually see may be favorited; hidden ones answer 404 like the detail endpoint
    @Transactional
    public void add(Long userId, Long listingId) {
        boolean visible = listingRepository.findById(listingId)
                .map(Listing::isPubliclyVisible)
                .orElse(false);
        if (!visible) {
            throw new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı");
        }
        favoriteRepository.insertIfAbsent(userId, listingId);
    }

    @Transactional
    public void remove(Long userId, Long listingId) {
        favoriteRepository.deleteByUserIdAndListingId(userId, listingId);
    }

    // Favorites of hidden listings (passive, owner blocked) stay stored and reappear once visible again
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> getFavorites(Long userId, PagingRequest pagingRequest) {
        return listingQueryService.toSummaryPage(favoriteRepository.findListingIdsByUserIdAndListingStatus(
                userId, ListingStatus.ACTIVE, pagingRequest.toPageable()));
    }
}
