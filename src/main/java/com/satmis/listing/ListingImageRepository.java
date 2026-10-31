package com.satmis.listing;

import com.satmis.enums.ListingImageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    // Bulk updates skip Hibernate's versioning, so the version is bumped by hand: a listing save that loaded
    // one of these images earlier then fails instead of attaching an image whose file is about to be removed
    @Transactional
    @Modifying
    @Query("""
            update ListingImage i
            set i.status = com.satmis.enums.ListingImageStatus.DELETED, i.version = i.version + 1
            where i.status in :statuses and i.createdAt < :createdBefore""")
    int markDeleted(@Param("statuses") Collection<ListingImageStatus> statuses,
                    @Param("createdBefore") Instant createdBefore);

    @Query("""
            select new com.satmis.listing.DeletedImageView(i.id, i.objectKey)
            from ListingImage i
            where i.status = com.satmis.enums.ListingImageStatus.DELETED
            order by i.id""")
    List<DeletedImageView> findDeleted(Pageable pageable);

    @Transactional
    @Modifying
    @Query("delete from ListingImage i where i.id in :ids and i.status = com.satmis.enums.ListingImageStatus.DELETED")
    int deleteDeletedByIdIn(@Param("ids") Collection<Long> ids);
}
