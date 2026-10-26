package com.satmis.listing;

import com.satmis.favorite.FavoriteRepository;
import com.satmis.listing.dto.ListingDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Detail responses need favorite data that is not part of the Listing aggregate; built in one place for all services
@Component
@RequiredArgsConstructor
class ListingDetailAssembler {

    private final ListingMapper listingMapper;
    private final FavoriteRepository favoriteRepository;

    ListingDetailResponse assemble(Listing listing, Long viewerId) {
        long favoriteCount = favoriteRepository.countByListingId(listing.getId());
        boolean favoritedByMe = viewerId != null
                && favoriteRepository.existsByUserIdAndListingId(viewerId, listing.getId());
        boolean viewerAuthenticated = viewerId != null;
        return listingMapper.toDetail(listing, favoriteCount, favoritedByMe, viewerAuthenticated);
    }
}
