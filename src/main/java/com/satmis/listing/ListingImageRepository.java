package com.satmis.listing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {

    @Query("""
            select new com.satmis.listing.CoverImageView(i.listing.id, i.url)
            from ListingImage i
            where i.listing.id in :listingIds and i.cover = true""")
    List<CoverImageView> findCoverImages(@Param("listingIds") Collection<Long> listingIds);
}
