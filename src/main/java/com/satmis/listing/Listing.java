package com.satmis.listing;

import com.satmis.catalog.District;
import com.satmis.common.AuditableEntity;
import com.satmis.enums.CurrencyType;
import com.satmis.enums.ListingStatus;
import com.satmis.user.User;
import com.satmis.vehicle.VehicleDetail;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "listings", indexes = {
        @Index(name = "idx_listings_status_created_at", columnList = "status, created_at"),
        @Index(name = "idx_listings_price", columnList = "price"),
        @Index(name = "idx_listings_user", columnList = "user_id"),
        @Index(name = "idx_listings_district", columnList = "district_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Listing extends AuditableEntity {

    @Version
    private Long version;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyType currency = CurrencyType.TRY;

    // Switch default to PENDING_APPROVAL once the admin approval flow exists
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status = ListingStatus.ACTIVE;

    @Column(nullable = false)
    private int viewCount = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id")
    private District district;

    @OneToOne(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY, optional = false)
    private VehicleDetail vehicleDetail;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 20)
    private List<ListingImage> images = new ArrayList<>();

    public void setVehicleDetail(VehicleDetail vehicleDetail) {
        this.vehicleDetail = vehicleDetail;
        if (vehicleDetail != null) {
            vehicleDetail.setListing(this);
        }
    }

    public void addImage(ListingImage image) {
        images.add(image);
        image.setListing(this);
    }

    public void removeImage(ListingImage image) {
        images.remove(image);
        image.setListing(null);
    }
}
