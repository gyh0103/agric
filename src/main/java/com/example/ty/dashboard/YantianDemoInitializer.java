package com.example.ty.dashboard;

import com.example.ty.dashboard.entity.*;
import com.example.ty.dashboard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.Order;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 武威民勤蜜瓜 + 张掖临泽小枣 + 敦煌葡萄 基地 · 演示种子数据。
 * 覆盖河西走廊特色瓜果：民勤金红宝蜜瓜、银蒂蜜瓜、临泽小枣、敦煌无核白
 * 以及啤酒花田（嘉峪关）与玉米制种示范区。
 */
@Component @Order(200) @RequiredArgsConstructor
public class YantianDemoInitializer implements ApplicationRunner {
    public static final String FARM_ID="hexizao-01";
    private final FarmAssetRepository assets;
    private final FarmDeviceRepository devices;
    private final EnvironmentMetricRepository metrics;
    private final IrrigationUnitRepository irrigation;
    private final FarmAlertRepository alerts;
    private final MetricThresholdRepository thresholds;
    @Override @Transactional public void run(ApplicationArguments args) {
        if(!assets.findByFarmIdOrderById(FARM_ID).isEmpty()) {
            // 基地已播种；保证"番茄大棚" → "敦煌葡萄大棚"的重命名逻辑安全
            assets.findByFarmIdAndId(FARM_ID,"yt-melon-02").filter(a -> ("番茄大棚".equals(a.getName()) || "温室大棚".equals(a.getName()))).ifPresent(a -> {
                a.setName("敦煌葡萄大棚"); a.setCrop("敦煌无核白"); a.setMetric("葡萄 · 演示档案"); a.setType("greenhouse"); a.setGrowthStage("结果期 · 演示"); assets.save(a);
            });
            return;
        }
        var now=LocalDateTime.now();
        assets.saveAll(List.of(
            // —— 民勤蜜瓜基地（武威市民勤县）——
            plot("yt-melon-01","民勤金红宝蜜瓜田","金红宝蜜瓜",24,38,-16,-8,"膨大期 · 演示","120 亩","[[20,257],[319,228],[571,310],[224,400]]"),
            plot("yt-melon-02","敦煌葡萄大棚","敦煌无核白",45,43,0,-8,"结果期 · 演示","50 亩","[[366,285],[790,293],[1050,398],[638,487]]"),
            // —— 临泽小枣基地（张掖市临泽县）——
            plot("yt-jujube-01","临泽小枣示范园","临泽小枣",34,60,-16,9,"果实膨大期 · 演示","80 亩","[[65,404],[636,402],[981,561],[330,780]]"),
            // —— 玉米制种示范区（张掖高台）——
            plot("yt-seed-01","高台玉米制种核心区","郑单 958 制种",32,83,-16,25,"拔节期 · 演示","320 亩","[[18,836],[626,743],[1150,1020],[0,1020]]"),
            plot("yt-seed-02","杂交玉米亲本繁殖田","玉米自交系 A6",67,68,2,25,"拔节期 · 演示","180 亩","[[669,689],[1220,543],[1372,707],[1071,908]]"),
            // —— 啤酒花田（嘉峪关）——
            plot("yt-hops-01","嘉峪关啤酒花田","青岛大花",79,48,18,9,"开花期 · 演示","65 亩","[[1034,407],[1355,395],[1490,473],[1302,634]]"),
            // —— 设施 ——
            facility("yt-sorting","蜜瓜分选包装中心","device",76,30,18,-12,"采后分选 · 一瓜一码"),
            facility("yt-coldstore","低温冷藏库","device",94,33,29,-12,"蜜瓜/葡萄冷链暂存"),
            facility("yt-weather","民勤田间气象站","station",55,23,0,-25,"西风带 · 演示观测"),
            facility("yt-soil","土壤墒情监测站","station",61,29,-12,-25,"沙壤土 EC/pH · 演示"),
            facility("yt-water","膜下滴灌泵站","water",91,85,29,25,"沙漠绿洲 · 节水灌溉"),
            facility("yt-valve","瓜田滴灌阀组","device",22,70,-27,9,"智能滴灌回执 · 模拟"),
            facility("yt-camera","蜜瓜影像采集点","camera",47,56,4,9,"蜜瓜品质识别 · 演示")
        ));
        metrics.saveAll(List.of(metric("temperature","空气温度",26.8,"°C",now),metric("airHumidity","空气湿度",42,"%",now),metric("soilMoisture","土壤湿度",38,"%",now),metric("soilEC","土壤 EC（演示）",0.9,"mS/cm",now),metric("soilPH","土壤 pH（演示）",8.3,"",now),metric("light","光照强度",980,"lx",now)));
        thresholds.save(MetricThreshold.builder().farmId(FARM_ID).metricKey("soilMoisture").label("沙壤土演示墒情下限").minimumValue(35).enabled(true).updatedAt(now).build());
        for(var row:List.of(new String[]{"yt-weather","民勤田间气象站","sensor","民勤瓜田北缘"},new String[]{"yt-soil","土壤墒情监测站","sensor","沙壤土核心监测点"},new String[]{"yt-valve","瓜田滴灌阀组","actuator","金红宝蜜瓜田"},new String[]{"yt-water","膜下滴灌泵站","actuator","基地干渠入口"},new String[]{"yt-camera","蜜瓜影像采集点","camera","葡萄大棚入口"}))devices.save(FarmDevice.builder().id("dev-"+row[0]).farmId(FARM_ID).entityId(row[0]).name(row[1]).category(row[2]).location(row[3]).online(true).enabled(false).currentValue("演示设备 · 未接真实硬件").lastSeenAt(now).build());
        for(var row:List.of(new String[]{"yt-jujube-01","临泽小枣示范园","34","60"},new String[]{"yt-seed-01","高台玉米制种核心区","32","83"}))irrigation.save(IrrigationUnitEntity.builder().id("irrigation-"+row[0]).farmId(FARM_ID).entityId(row[0]).name(row[1]+"灌溉单元").targetName(row[1]).kind("zone").mapX(Double.parseDouble(row[2])).mapY(Double.parseDouble(row[3])).enabled(false).flowRate(0).durationMinutes(15).updatedAt(now).build());
        alerts.save(FarmAlert.builder().farmId(FARM_ID).entityId("yt-jujube-01").title("临泽小枣园：果实膨大期土壤湿度偏低，建议滴灌补水").level("预警").status("未处理").occurredAt(now).build());
        alerts.save(FarmAlert.builder().farmId(FARM_ID).entityId("yt-camera").title("蜜瓜品质识别模型需更新视觉权重").level("关注").status("未处理").occurredAt(now.minusMinutes(10)).build());
    }
    private FarmAsset plot(String id,String name,String crop,double x,double y,double px,double pz,String stage,String area,String polygon) {
        return FarmAsset.builder().id(id).farmId(FARM_ID).name(name).type(id.equals("yt-melon-02") ? "greenhouse" : "field").status("normal").metric(crop+" · 演示档案").mapX(x).mapY(y).positionX(px).positionY(0d).positionZ(pz).zoneId("zone-"+id).polygonJson(polygon).crop(crop).area(area).growthStage(stage).environmentSummary("沙漠绿洲演示场景，专项评估需真实观测").build();
    }
    private FarmAsset facility(String id,String name,String type,double x,double y,double px,double pz,String metric) { return FarmAsset.builder().id(id).farmId(FARM_ID).name(name).type(type).status("normal").metric(metric).mapX(x).mapY(y).positionX(px).positionY(0d).positionZ(pz).build(); }
    private EnvironmentMetric metric(String key,String label,double value,String unit,LocalDateTime at) {return EnvironmentMetric.builder().farmId(FARM_ID).metricKey(key).label(label).value(value).previousValue(value).unit(unit).tone("info").measuredAt(at).build();}
}
