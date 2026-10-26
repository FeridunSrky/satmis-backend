package com.satmis.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Long> {

    List<District> findByCityId(Long cityId);
}
