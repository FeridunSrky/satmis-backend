package com.satmis.listing;

import com.satmis.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "listing_images", indexes = @Index(name = "idx_listing_images_listing", columnList = "listing_id"))
@Getter
@Setter
@NoArgsConstructor
public class ListingImage extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private int displayOrder;

    // Named "cover" rather than "isCover" to keep Lombok and Jackson property names aligned
    @Column(nullable = false)
    private boolean cover;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;
}
