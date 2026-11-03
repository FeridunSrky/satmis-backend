package com.satmis.listing;

import com.satmis.common.ApiException;
import com.satmis.common.PageResponse;
import com.satmis.listing.dto.ListingSearchCriteria;
import com.satmis.listing.dto.SalePriceSummaryResponse;
import com.satmis.listing.dto.SoldVehicleDetailResponse;
import com.satmis.listing.dto.SoldVehiclePageRequest;
import com.satmis.listing.dto.SoldVehicleSummaryResponse;
import com.satmis.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The public sold vehicles page: real sale prices, searchable with the same filters as live listings
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SoldVehicleQueryService {

    private final ListingRepository listingRepository;
    private final ListingQueryService listingQueryService;
    private final ListingMapper listingMapper;
    private final ObjectStorage objectStorage;

    public PageResponse<SoldVehicleSummaryResponse> search(ListingSearchCriteria criteria,
                                                           SoldVehiclePageRequest pageRequest) {
        Page<Long> idPage = listingRepository.findIds(
                ListingSpecifications.publiclySoldMatching(criteria), pageRequest.toPageable());
        return listingQueryService.toPage(idPage, listingMapper::toSoldSummary);
    }

    public SalePriceSummaryResponse summarize(ListingSearchCriteria criteria) {
        return new SalePriceSummaryResponse(
                listingRepository.summarizeSalePrices(ListingSpecifications.publiclySoldMatching(criteria)));
    }

    public SoldVehicleDetailResponse getDetail(Long listingId) {
        Listing listing = listingRepository.findDetailById(listingId)
                .filter(Listing::isPubliclySold)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Satılmış araç bulunamadı"));
        return listingMapper.toSoldDetail(listing, objectStorage);
    }
}
