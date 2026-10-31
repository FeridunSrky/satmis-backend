package com.satmis.listing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long>, ListingRepositoryCustom {

    @Query("""
            select l from Listing l
            join fetch l.owner
            join fetch l.vehicleDetail v
            join fetch v.series s join fetch s.model m join fetch m.brand
            join fetch l.district d join fetch d.city
            left join fetch l.images
            where l.id = :id""")
    Optional<Listing> findDetailById(@Param("id") Long id);

    @Query("""
            select l from Listing l
            join fetch l.vehicleDetail v
            join fetch v.series s join fetch s.model m join fetch m.brand
            join fetch l.district d join fetch d.city
            where l.id in :ids""")
    List<Listing> findSummariesByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select new com.satmis.listing.OwnerListingCount(l.owner.id, count(l))
            from Listing l
            where l.owner.id in :ownerIds
            group by l.owner.id""")
    List<OwnerListingCount> countByOwnerIdIn(@Param("ownerIds") Collection<Long> ownerIds);

    // Bulk update bypasses @Version so a view never conflicts with the owner's edit
    @Modifying
    @Query("update Listing l set l.viewCount = l.viewCount + 1 where l.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
