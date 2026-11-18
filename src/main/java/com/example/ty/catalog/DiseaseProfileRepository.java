package com.example.ty.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiseaseProfileRepository extends JpaRepository<DiseaseProfile, Long> {
    List<DiseaseProfile> findByEnabledTrueOrderByCropAscSortOrderAsc();
    List<DiseaseProfile> findAllByOrderByCropAscSortOrderAsc();
    List<DiseaseProfile> findByCropOrderBySortOrderAsc(String crop);
}
