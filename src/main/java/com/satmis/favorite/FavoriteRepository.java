package com.satmis.favorite;

import com.satmis.enums.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // ON CONFLICT keeps "add" idempotent even when two requests race past an exists-check
    @Modifying
    @Query(value = """
            insert into favorites (user_id, listing_id, created_at, updated_at)
            values (:userId, :listingId, now(), now())
            on conflict (user_id, listing_id) do nothing""", nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId, @Param("listingId") Long listingId);

    @Modifying
    @Query("delete from Favorite f where f.user.id = :userId and f.listing.id = :listingId")
    void deleteByUserIdAndListingId(@Param("userId") Long userId, @Param("listingId") Long listingId);

    long countByListingId(Long listingId);

    boolean existsByUserIdAndListingId(Long userId, Long listingId);

    @Query(value = """
            select f.listing.id from Favorite f
            where f.user.id = :userId and f.listing.status = :status
            order by f.createdAt desc, f.id desc""",
            countQuery = """
            select count(f) from Favorite f
            where f.user.id = :userId and f.listing.status = :status""")
    Page<Long> findListingIdsByUserIdAndListingStatus(@Param("userId") Long userId,
                                                      @Param("status") ListingStatus status,
                                                      Pageable pageable);
}
