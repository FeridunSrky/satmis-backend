package com.satmis.listing;

import com.satmis.storage.DeletedFile;

import java.util.List;

// Published when images become DELETED; their stored files are removed once the transaction commits
public record ListingImagesDeletedEvent(List<DeletedFile> images) {
}
