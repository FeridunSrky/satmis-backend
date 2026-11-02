package com.satmis.listing;

import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sold-vehicles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSoldVehicleController {

    private final ListingModerationService listingModerationService;

    @PostMapping("/{listingId}/hide")
    public ListingDetailResponse hide(@PathVariable Long listingId,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return listingModerationService.hideSale(listingId, principal.getId());
    }
}
