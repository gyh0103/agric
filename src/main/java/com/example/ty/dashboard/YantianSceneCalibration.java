package com.example.ty.dashboard;

import com.example.ty.dashboard.repository.FarmAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 河西绿洲基地 · 展示校准。
 * 负责基础展示修正，与全景图坐标无强耦合。
 */
@Component @Order(210) @RequiredArgsConstructor
public class YantianSceneCalibration implements ApplicationRunner {
    private final FarmAssetRepository assets;

    @Override @Transactional public void run(ApplicationArguments args) {
        // 修正摄像头描述文案（兼容老播种）
        assets.findByFarmIdAndId(YantianDemoInitializer.FARM_ID,"yt-camera")
            .filter(a->"蜜瓜品质识别 · 演示".equals(a.getMetric()) || "鸭梨视觉模型未接入".equals(a.getMetric()) || "鸭梨视觉模型未接入".equals(a.getMetric()))
            .ifPresent(a->{a.setMetric("蜜瓜品质识别 · 演示"); assets.save(a);});
    }
}
