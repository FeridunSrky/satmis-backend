package com.satmis.comment;

import com.satmis.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingCommentRepository extends JpaRepository<ListingComment, Long> {

    /**
     * Takes one of the user's comment slots for the listing. Returns 0 when all slots are used; the conditional
     * upsert is atomic, so concurrent requests cannot exceed the limit.
     */
    @Modifying
    @Query(value = """
            insert into listing_comment_quotas (listing_id, user_id, used) values (:listingId, :userId, 1)
            on conflict (listing_id, user_id) do update set used = listing_comment_quotas.used + 1
            where listing_comment_quotas.used < :maxComments""", nativeQuery = true)
    int consumeQuota(@Param("listingId") Long listingId, @Param("userId") Long userId,
                     @Param("maxComments") int maxComments);

    @Query(value = "select used from listing_comment_quotas where listing_id = :listingId and user_id = :userId",
            nativeQuery = true)
    Optional<Integer> findUsedQuota(@Param("listingId") Long listingId, @Param("userId") Long userId);

    // Published comments of a listing, newest first; comments of blocked users are hidden like their listings
    @Query(value = """
            select c.id from ListingComment c
            where c.listing.id = :listingId and c.parent is null
              and c.status = com.satmis.enums.CommentStatus.APPROVED and c.author.enabled = true
            order by c.createdAt desc, c.id desc""",
            countQuery = """
            select count(c) from ListingComment c
            where c.listing.id = :listingId and c.parent is null
              and c.status = com.satmis.enums.CommentStatus.APPROVED and c.author.enabled = true""")
    Page<Long> findPublishedIds(@Param("listingId") Long listingId, Pageable pageable);

    @Query("select c from ListingComment c join fetch c.author where c.id in :ids")
    List<ListingComment> findWithAuthorByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select r from ListingComment r where r.parent.id in :parentIds and r.status in :statuses")
    List<ListingComment> findRepliesByParentIdIn(@Param("parentIds") Collection<Long> parentIds,
                                                 @Param("statuses") Collection<CommentStatus> statuses);

    boolean existsByParentIdAndStatusIn(Long parentId, Collection<CommentStatus> statuses);

    @Query(value = """
            select c from ListingComment c join fetch c.listing
            where c.author.id = :authorId
            order by c.createdAt desc, c.id desc""",
            countQuery = "select count(c) from ListingComment c where c.author.id = :authorId")
    Page<ListingComment> findByAuthorWithListing(@Param("authorId") Long authorId, Pageable pageable);

    // Moderation queue, oldest first so nothing waits longer than necessary
    @Query(value = """
            select c.id from ListingComment c
            where c.status = :status and (:listingId is null or c.listing.id = :listingId)
            order by c.createdAt, c.id""",
            countQuery = """
            select count(c) from ListingComment c
            where c.status = :status and (:listingId is null or c.listing.id = :listingId)""")
    Page<Long> findIdsForModeration(@Param("status") CommentStatus status, @Param("listingId") Long listingId,
                                    Pageable pageable);

    @Query("""
            select c from ListingComment c
            join fetch c.listing
            join fetch c.author
            left join fetch c.parent p
            left join fetch p.author
            where c.id in :ids""")
    List<ListingComment> findForModerationByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select c from ListingComment c
            join fetch c.listing l
            join fetch l.owner
            join fetch c.author
            left join fetch c.parent
            where c.id = :id""")
    Optional<ListingComment> findWithContextById(@Param("id") Long id);

    long countByStatus(CommentStatus status);
}
