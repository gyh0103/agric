package com.example.ty.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CropProfileRepository extends JpaRepository<CropProfile, String> {
    List<CropProfile> findByEnabledTrueOrderBySortOrderAscNameAsc();
    List<CropProfile> findAllByOrderBySortOrderAscNameAsc();
}
