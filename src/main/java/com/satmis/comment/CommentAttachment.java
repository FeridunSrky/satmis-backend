package com.satmis.comment;

import com.satmis.common.AuditableEntity;
import com.satmis.enums.AttachmentType;
import com.satmis.enums.CommentAttachmentStatus;
import com.satmis.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Evidence for a comment (photo or PDF report), uploaded to object storage before the comment is sent
@Entity
@Table(name = "comment_attachments", indexes = {
        @Index(name = "idx_comment_attachments_comment", columnList = "comment_id"),
        @Index(name = "idx_comment_attachments_owner_status", columnList = "owner_id, status"),
        @Index(name = "idx_comment_attachments_status_created_at", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class CommentAttachment extends AuditableEntity {

    @Version
    private Long version;

    // Name of the file in object storage; the public URL is derived from it so the domain can change
    @Column(nullable = false, unique = true, length = 200)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttachmentType fileType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentAttachmentStatus status;

    // Only set while the attachment is part of a comment
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private ListingComment comment;
}
