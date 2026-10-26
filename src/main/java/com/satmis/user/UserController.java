package com.satmis.user;

import com.satmis.common.PageResponse;
import com.satmis.listing.ListingQueryService;
import com.satmis.listing.dto.ListingPageRequest;
import com.satmis.listing.dto.ListingSummaryResponse;
import com.satmis.security.CustomUserDetails;
import com.satmis.user.dto.ChangePasswordRequest;
import com.satmis.user.dto.UpdateProfileRequest;
import com.satmis.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// User id always comes from the token, never from the URL, so users can only touch their own record
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ListingQueryService listingQueryService;

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal CustomUserDetails principal) {
        return userService.getById(principal.getId());
    }

    // Includes passive and sold listings, unlike the public search
    @GetMapping("/me/listings")
    public PageResponse<ListingSummaryResponse> getMyListings(@AuthenticationPrincipal CustomUserDetails principal,
                                                              @Valid @ParameterObject ListingPageRequest pageRequest) {
        return listingQueryService.getOwnListings(principal.getId(), pageRequest);
    }

    @PutMapping("/me")
    public UserResponse updateCurrentUser(@AuthenticationPrincipal CustomUserDetails principal,
                                          @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(principal.getId(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
    }
}
