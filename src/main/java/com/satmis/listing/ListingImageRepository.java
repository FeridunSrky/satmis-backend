package com.satmis.listing;

import com.satmis.enums.ListingImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {

    @Query("""
            select new com.satmis.listing.CoverImageView(i.listing.id, i.objectKey)
            from ListingImage i
            where i.listing.id in :listingIds and i.cover = true""")
    List<CoverImageView> findCoverImages(@Param("listingIds") Collection<Long> listingIds);

    Optional<ListingImage> findByIdAndOwnerId(Long id, Long ownerId);

    List<ListingImage> findAllByIdInAndOwnerId(Collection<Long> ids, Long ownerId);

    long countByOwnerIdAndStatusIn(Long ownerId, Collection<ListingImageStatus> statuses);
}
