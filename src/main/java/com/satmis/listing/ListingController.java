package com.satmis.listing;

import com.satmis.common.PageResponse;
import com.satmis.common.ValidationGroups;
import com.satmis.listing.dto.ChangeStatusRequest;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingPageRequest;
import com.satmis.listing.dto.ListingRequest;
import com.satmis.listing.dto.ListingSearchCriteria;
import com.satmis.listing.dto.ListingSummaryResponse;
import com.satmis.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final ListingQueryService listingQueryService;

    @PostMapping
    public ResponseEntity<ListingDetailResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                        @Valid @RequestBody ListingRequest request) {
        ListingDetailResponse created = listingService.create(principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/listings/" + created.id())).body(created);
    }

    // Public endpoint: principal is null for anonymous visitors
    @GetMapping("/{listingId}")
    public ListingDetailResponse getDetail(@PathVariable Long listingId,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return listingQueryService.getDetail(listingId, principal == null ? null : principal.getId());
    }

    @GetMapping
    public PageResponse<ListingSummaryResponse> search(@Valid @ParameterObject ListingSearchCriteria criteria,
                                                       @Valid @ParameterObject ListingPageRequest pageRequest) {
        return listingQueryService.search(criteria, pageRequest);
    }

    @PutMapping("/{listingId}")
    public ListingDetailResponse update(@PathVariable Long listingId,
                                        @AuthenticationPrincipal CustomUserDetails principal,
                                        @Validated({Default.class, ValidationGroups.OnUpdate.class})
                                        @RequestBody ListingRequest request) {
        return listingService.update(listingId, principal.getId(), request);
    }

    @PatchMapping("/{listingId}/status")
    public ListingDetailResponse changeStatus(@PathVariable Long listingId,
                                              @AuthenticationPrincipal CustomUserDetails principal,
                                              @Valid @RequestBody ChangeStatusRequest request) {
        return listingService.changeStatus(listingId, principal.getId(), request.status());
    }

    @DeleteMapping("/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long listingId, @AuthenticationPrincipal CustomUserDetails principal) {
        listingService.delete(listingId, principal.getId());
    }
}
