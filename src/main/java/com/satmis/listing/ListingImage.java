package com.satmis.listing;

import com.satmis.common.AuditableEntity;
import com.satmis.enums.ImageType;
import com.satmis.enums.ListingImageStatus;
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

@Entity
@Table(name = "listing_images", indexes = {
        @Index(name = "idx_listing_images_listing", columnList = "listing_id"),
        @Index(name = "idx_listing_images_owner_status", columnList = "owner_id, status"),
        @Index(name = "idx_listing_images_status_created_at", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ListingImage extends AuditableEntity {

    @Version
    private Long version;

    // Name of the file in object storage; the public URL is derived from it so the domain can change
    @Column(nullable = false, unique = true, length = 200)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ImageType imageType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingImageStatus status;

    // Only set while the image is part of a listing
    private Integer displayOrder;

    // Named "cover" rather than "isCover" to keep Lombok and Jackson property names aligned
    @Column(nullable = false)
    private boolean cover;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id")
    private Listing listing;
}
