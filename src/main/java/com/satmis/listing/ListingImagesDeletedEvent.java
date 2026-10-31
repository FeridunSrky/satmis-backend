package com.satmis.listing;

import java.util.List;

// Published when images become DELETED; their stored files are removed once the transaction commits
public record ListingImagesDeletedEvent(List<DeletedImageView> images) {
}
