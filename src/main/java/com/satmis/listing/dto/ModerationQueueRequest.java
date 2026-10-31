package com.satmis.listing.dto;

import com.satmis.enums.ListingStatus;

// Defaults to the approval queue; any status can be listed so admins can also review live or rejected listings
public record ModerationQueueRequest(ListingStatus status) {

    public ListingStatus statusOrDefault() {
        return status == null ? ListingStatus.PENDING_APPROVAL : status;
    }
}
