package com.satmis.comment;

import com.satmis.common.AuditableEntity;
import com.satmis.enums.CommentStatus;
import com.satmis.listing.Listing;
import com.satmis.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// A comment under a listing, or the owner's reply to one (parent set). One waiting or published reply per comment
// is enforced by a partial unique index (V12).
@Entity
@Table(name = "listing_comments", indexes = {
        @Index(name = "idx_listing_comments_parent", columnList = "parent_id"),
        @Index(name = "idx_listing_comments_listing_status", columnList = "listing_id, status, created_at"),
        @Index(name = "idx_listing_comments_author_created_at", columnList = "author_id, created_at"),
        @Index(name = "idx_listing_comments_status_created_at", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ListingComment extends AuditableEntity {

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    // Deleting a comment removes its reply at the database level
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ListingComment parent;

    @Column(nullable = false, length = 2000)
    private String body;

    // approve/reject keep status, reason and reviewer consistent (ck_listing_comments_*)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    private CommentStatus status = CommentStatus.PENDING_APPROVAL;

    // Set only while REJECTED; shown to the author
    @Column(length = 500)
    @Setter(AccessLevel.NONE)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    @Setter(AccessLevel.NONE)
    private User reviewedBy;

    @Setter(AccessLevel.NONE)
    private Instant reviewedAt;

    // Replies the owner has sent to this comment; rejected and deleted ones keep counting
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private int replyAttempts;

    // No cascade: attachments are released before a comment is deleted, so their stored files can be removed
    @OneToMany(mappedBy = "comment")
    @OrderBy("displayOrder")
    private List<CommentAttachment> attachments = new ArrayList<>();

    public boolean isReply() {
        return parent != null;
    }

    public void approve(User admin, Instant now) {
        status = CommentStatus.APPROVED;
        rejectionReason = null;
        reviewedBy = admin;
        reviewedAt = now;
    }

    public void reject(User admin, String reason, Instant now) {
        status = CommentStatus.REJECTED;
        rejectionReason = reason;
        reviewedBy = admin;
        reviewedAt = now;
    }

    // Changes the version too, so two replies sent at once cannot both pass the attempt limit
    public void recordReplyAttempt() {
        replyAttempts++;
    }
}
