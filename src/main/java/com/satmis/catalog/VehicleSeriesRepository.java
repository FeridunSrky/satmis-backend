package com.satmis.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleSeriesRepository extends JpaRepository<VehicleSeries, Long> {

    List<VehicleSeries> findByModelId(Long modelId);
}
