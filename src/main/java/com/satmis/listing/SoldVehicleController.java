package com.satmis.listing;

import com.satmis.common.PageResponse;
import com.satmis.listing.dto.ListingSearchCriteria;
import com.satmis.listing.dto.SalePriceSummaryResponse;
import com.satmis.listing.dto.SoldVehicleDetailResponse;
import com.satmis.listing.dto.SoldVehiclePageRequest;
import com.satmis.listing.dto.SoldVehicleSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sold-vehicles")
@RequiredArgsConstructor
public class SoldVehicleController {

    private final SoldVehicleQueryService soldVehicleQueryService;

    @GetMapping
    public PageResponse<SoldVehicleSummaryResponse> search(@Valid @ParameterObject ListingSearchCriteria criteria,
                                                           @Valid @ParameterObject SoldVehiclePageRequest pageRequest) {
        return soldVehicleQueryService.search(criteria, pageRequest);
    }

    @GetMapping("/summary")
    public SalePriceSummaryResponse summarize(@Valid @ParameterObject ListingSearchCriteria criteria) {
        return soldVehicleQueryService.summarize(criteria);
    }

    @GetMapping("/{listingId}")
    public SoldVehicleDetailResponse getDetail(@PathVariable Long listingId) {
        return soldVehicleQueryService.getDetail(listingId);
    }
}
