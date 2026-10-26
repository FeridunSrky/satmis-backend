package com.satmis.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface ListingRepositoryCustom {

    // Ids only: loading full rows here would trigger one query per listing for its vehicle detail
    Page<Long> findIds(Specification<Listing> specification, Pageable pageable);

    // Detail and image changes do not dirty the listing row itself, so its version must be bumped explicitly
    void forceVersionIncrement(Listing listing);
}
