package com.satmis.comment;

import com.satmis.enums.CommentAttachmentStatus;
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

public interface CommentAttachmentRepository extends JpaRepository<CommentAttachment, Long> {

    Optional<CommentAttachment> findByIdAndOwnerId(Long id, Long ownerId);

    List<CommentAttachment> findAllByIdInAndOwnerId(Collection<Long> ids, Long ownerId);

    long countByOwnerIdAndStatusIn(Long ownerId, Collection<CommentAttachmentStatus> statuses);

    @Query("select a from CommentAttachment a where a.comment.id in :commentIds order by a.displayOrder")
    List<CommentAttachment> findByCommentIdIn(@Param("commentIds") Collection<Long> commentIds);

    @Query("select a from CommentAttachment a where a.comment.listing.id = :listingId")
    List<CommentAttachment> findByListingId(@Param("listingId") Long listingId);

    // Bulk updates skip Hibernate's versioning, so the version is bumped by hand: a comment save that loaded
    // one of these attachments earlier then fails instead of attaching a file that is about to be removed
    @Transactional
    @Modifying
    @Query("""
            update CommentAttachment a
            set a.status = com.satmis.enums.CommentAttachmentStatus.DELETED, a.version = a.version + 1
            where a.status in :statuses and a.createdAt < :createdBefore""")
    int markDeleted(@Param("statuses") Collection<CommentAttachmentStatus> statuses,
                    @Param("createdBefore") Instant createdBefore);

    @Query("""
            select new com.satmis.comment.DeletedAttachmentView(a.id, a.objectKey)
            from CommentAttachment a
            where a.status = com.satmis.enums.CommentAttachmentStatus.DELETED
            order by a.id""")
    List<DeletedAttachmentView> findDeleted(Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            delete from CommentAttachment a
            where a.id in :ids and a.status = com.satmis.enums.CommentAttachmentStatus.DELETED""")
    int deleteDeletedByIdIn(@Param("ids") Collection<Long> ids);
}
