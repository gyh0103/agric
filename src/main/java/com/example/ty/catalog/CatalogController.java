package com.example.ty.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作物与病害字典，供识别面板、问答和后台共同读取。
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CropProfileRepository crops;
    private final DiseaseProfileRepository diseases;

    @GetMapping("/crops")
    public List<CropProfile> crops() {
        return crops.findByEnabledTrueOrderBySortOrderAscNameAsc();
    }

    @GetMapping("/diseases")
    public List<DiseaseProfile> diseases(@RequestParam(defaultValue = "") String crop) {
        if (crop.isBlank()) return diseases.findByEnabledTrueOrderByCropAscSortOrderAsc();
        String keyword = crop.trim();
        return diseases.findByEnabledTrueOrderByCropAscSortOrderAsc().stream()
                .filter(item -> item.getCrop().contains(keyword) || keyword.contains(item.getCrop()))
                .toList();
    }
}
