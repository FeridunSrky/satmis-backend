package com.satmis.favorite;

import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.listing.dto.ListingSummaryResponse;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// PUT/DELETE on a sub-resource keep add and remove idempotent: repeating either is harmless
@RestController
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PutMapping("/api/listings/{listingId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable Long listingId, @AuthenticationPrincipal CustomUserDetails principal) {
        favoriteService.add(principal.getId(), listingId);
    }

    @DeleteMapping("/api/listings/{listingId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long listingId, @AuthenticationPrincipal CustomUserDetails principal) {
        favoriteService.remove(principal.getId(), listingId);
    }

    @GetMapping("/api/users/me/favorites")
    public PageResponse<ListingSummaryResponse> getMyFavorites(@AuthenticationPrincipal CustomUserDetails principal,
                                                               @Valid @ParameterObject PagingRequest pagingRequest) {
        return favoriteService.getFavorites(principal.getId(), pagingRequest);
    }
}
