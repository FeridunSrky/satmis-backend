package com.satmis.vehicle;

import com.satmis.catalog.VehicleSeries;
import com.satmis.enums.BodyType;
import com.satmis.enums.FromWho;
import com.satmis.enums.FuelType;
import com.satmis.enums.GearType;
import com.satmis.enums.Traction;
import com.satmis.enums.VehicleCondition;
import com.satmis.listing.Listing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Shares its primary key with listings: exactly one detail row per listing
@Entity
@Table(name = "vehicle_details", indexes = {
        @Index(name = "idx_vehicle_details_series", columnList = "series_id"),
        @Index(name = "idx_vehicle_details_model_year", columnList = "model_year"),
        @Index(name = "idx_vehicle_details_kilometer", columnList = "kilometer"),
        @Index(name = "idx_vehicle_details_fuel_type", columnList = "fuel_type")
})
@Getter
@Setter
@NoArgsConstructor
public class VehicleDetail {

    @Id
    private Long listingId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id")
    private VehicleSeries series;

    @Column(nullable = false)
    private Integer modelYear;

    @Column(nullable = false)
    private Integer kilometer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GearType gearType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BodyType bodyType;

    @Column(length = 30)
    private String color;

    // Horsepower
    private Integer enginePower;

    // Cubic centimetres
    private Integer engineCapacity;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Traction traction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VehicleCondition vehicleCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FromWho fromWho;

    @Column(nullable = false)
    private boolean hasDamageRecord;

    @Column(nullable = false)
    private boolean exchangeable;
}
