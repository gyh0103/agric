package com.example.ty.dashboard;

import com.example.ty.dashboard.entity.*;
import com.example.ty.dashboard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 张掖绿洲制种基地 · 演示种子数据。
 * 覆盖设施农业（日光温室）与制种大田，对应河西走廊中段绿洲农业。
 */
@Component
@org.springframework.core.annotation.Order(100)
@RequiredArgsConstructor
public class DashboardDataInitializer implements ApplicationRunner {
    public static final String FARM_ID = "zhangye-01";
    private final FarmAssetRepository assets;
    private final FarmDeviceRepository devices;
    private final EnvironmentMetricRepository metrics;
    private final IrrigationUnitRepository irrigation;
    private final FarmAlertRepository alerts;
    private final MetricThresholdRepository thresholds;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (assets.findByFarmIdOrderById(FARM_ID).isEmpty()) seedAssets();
        if (devices.findByFarmIdOrderById(FARM_ID).isEmpty()) seedDevices();
        if (metrics.findByFarmIdOrderById(FARM_ID).isEmpty()) seedMetrics();
        if (irrigation.findByFarmIdOrderById(FARM_ID).isEmpty()) seedIrrigation();
        if (alerts.findByFarmId(FARM_ID).isEmpty()) seedAlerts();
        ensureDemoAlerts();
        if (thresholds.findByFarmIdOrderByMetricKey(FARM_ID).isEmpty()) thresholds.save(MetricThreshold.builder().farmId(FARM_ID).metricKey("soilMoisture")
                .label("土壤湿度下限").minimumValue(40).enabled(true).updatedAt(LocalDateTime.now()).build());
    }

    private void seedAssets() {
        assets.saveAll(List.of(
            // —— 日光温室群（6 座，沿绿洲主干道排列）——
            asset("gh-01","1号番茄日光温室","greenhouse","normal","健康度 95",25,52,95,-16,0,3,"zone-gh-01","[[238,483],[302,458],[492,522],[503,579],[468,611],[416,620],[238,549]]","硬粉番茄","1,800 ㎡","结果盛期","26.8°C · 63%"),
            asset("gh-02","2号黄瓜日光温室","greenhouse","attention","湿度 72%",32,48,87,-8,0,1,"zone-gh-02","[[350,447],[410,429],[624,478],[634,526],[596,553],[557,563],[350,507]]","津优黄瓜","1,600 ㎡","结瓜期","27.2°C · 72%"),
            asset("gh-03","3号辣椒日光温室","greenhouse","normal","健康度 91",39,44,91,0,0,-2,"zone-gh-03","[[471,412],[521,395],[715,438],[725,478],[694,506],[660,516],[470,462]]","陇椒五号","1,500 ㎡","开花坐果期","26.1°C · 58%"),
            asset("gh-04","4号蔬菜育苗温室","greenhouse","normal","健康度 96",47,41,96,8,0,-5,"zone-gh-04","[[594,385],[649,369],[826,409],[838,446],[810,472],[780,480],[594,428]]","蔬菜集约化育苗","1,200 ㎡","幼苗期","25.5°C · 68%"),
            asset("gh-05","5号草莓温室","greenhouse","normal","健康度 93",58,54,93,7,0,4,"zone-gh-05","[[722,509],[788,483],[991,522],[1009,558],[991,593],[920,620],[721,561]]","红颜草莓","1,400 ㎡","膨果期","25.9°C · 66%"),
            asset("gh-06","6号水培叶菜温室","greenhouse","normal","健康度 90",54,40,90,16,0,-8,"zone-gh-06","[[698,359],[759,341],[965,388],[981,423],[959,457],[926,470],[697,412]]","水培油麦菜","1,300 ㎡","快速生长期","25.6°C · 65%"),
            // —— 制种大田 ——
            asset("field-04","4号玉米制种田","field","warning","土壤湿度 35%",43,62,78,4,0,12,"zone-field-04","[[468,580],[707,535],[866,614],[816,657],[665,707],[457,651]]","郑单 958 制种","280 亩","拔节期","土壤湿度 35%"),
            asset("field-05","5号油菜制种田","field","normal","长势良好",73,60,92,19,0,8,"zone-field-05","[[1000,570],[1273,514],[1339,650],[1212,690],[1071,726],[948,662]]","陇油 16 油菜","150 亩","蕾薹期","土壤湿度 46%"),
            // —— 基础设施 ——
            asset("weather-01","田间气象站01","station","normal","24.6°C · 西风 3.2m/s",13,40,null,-28,0,-7,null,null,null,null,null,null),
            asset("water-01","祁连山雪水蓄水池","water","normal","水位 78%",17,70,null,-20,0,18,null,null,null,null,null,null),
            asset("fertilizer-01","水肥一体机01","device","normal","EC 1.42mS/cm",67,44,null,15,0,-2,null,null,null,null,null,null),
            asset("pump-02","绿洲灌溉泵站02","device","offline","通信中断 · 8分钟",53,73,null,2,0,9,null,null,null,null,null,null),
            asset("valve-02","玉米田滴灌阀门组","device","normal","流量 14.2m³/h",36,79,null,-10,0,20,null,null,null,null,null,null),
            asset("camera-03","制种田 AI 摄像头","camera","normal","高清识别在线",69,77,null,15,0,18,null,null,null,null,null,null),
            asset("robot-01","制种区巡检机器人","robot","normal","巡检中 · 电量 76%",60,73,null,24,0,12,null,null,null,null,null,null)
        ));
    }

    private FarmAsset asset(String id,String name,String type,String status,String metric,double x,double y,Integer health,
                            double px,double py,double pz,String zoneId,String polygon,String crop,String area,String stage,String env) {
        return FarmAsset.builder().id(id).farmId(FARM_ID).name(name).type(type).status(status).metric(metric).mapX(x).mapY(y)
                .health(health).positionX(px).positionY(py).positionZ(pz).zoneId(zoneId).polygonJson(polygon).crop(crop)
                .area(area).growthStage(stage).environmentSummary(env).build();
    }

    private void seedDevices() {
        LocalDateTime now = LocalDateTime.now();
        devices.saveAll(List.of(
            device("dev-weather","weather-01","田间气象站 01","sensor","西侧制种田旁",true,true,"24.6°C · 西风 3.2m/s",now),
            device("dev-camera","camera-03","AI 摄像头 03","camera","玉米制种田东侧",true,true,"1080P · AI 在线",now.minusSeconds(3)),
            device("dev-fertilizer","fertilizer-01","水肥一体机 01","actuator","温室群主管网",true,true,"EC 1.42mS/cm",now.minusSeconds(12)),
            device("dev-pump","pump-02","绿洲灌溉泵站 02","actuator","南侧干渠",false,false,"通信中断",now.minusMinutes(8)),
            device("dev-valve","valve-02","滴灌阀门组 02","actuator","玉米制种田",true,false,"关闭 · 0m³/h",now.minusSeconds(8)),
            device("dev-robot","robot-01","巡检机器人 01","robot","玉米制种田与油菜田",true,true,"巡检中 · 电量76%",now)
        ));
    }
    private FarmDevice device(String id,String entityId,String name,String category,String location,boolean online,boolean enabled,String value,LocalDateTime seen) {
        return FarmDevice.builder().id(id).farmId(FARM_ID).entityId(entityId).name(name).category(category).location(location).online(online).enabled(enabled).currentValue(value).lastSeenAt(seen).build();
    }
    private void seedMetrics() {
        LocalDateTime now=LocalDateTime.now();
        metrics.saveAll(List.of(metric("temperature","空气温度",24.6,"°C",25.1,"info",now),metric("airHumidity","空气湿度",58,"%",60,"info",now),metric("soilMoisture","土壤湿度",46,"%",42.5,"warning",now),metric("light","光照强度",820,"lx",780,"info",now),metric("co2","CO₂",540,"ppm",530,"success",now)));
    }
    private EnvironmentMetric metric(String key,String label,double value,String unit,double previous,String tone,LocalDateTime at) { return EnvironmentMetric.builder().farmId(FARM_ID).metricKey(key).label(label).value(value).unit(unit).previousValue(previous).tone(tone).measuredAt(at).build(); }
    private void seedIrrigation() {
        LocalDateTime now=LocalDateTime.now();
        irrigation.saveAll(List.of(unit("water-source","water-01","祁雪蓄水池总控","全场供水","source",20,69,true,0,18,now),unit("fertigation","fertilizer-01","水肥一体机","温室群主管网","fertigation",61,48,true,14.2,18,now),unit("valve-south","valve-02","南区滴灌阀组","4号玉米制种田","valve",35,78,false,0,18,now),unit("zone-field-04","field-04","玉米田灌溉单元","郑单958制种 · 280亩","zone",43,62,false,0,18,now),unit("zone-field-05","field-05","油菜田灌溉单元","陇油16 · 150亩","zone",73,59,true,9.6,20,now)));
    }
    private IrrigationUnitEntity unit(String id,String entityId,String name,String target,String kind,double x,double y,boolean enabled,double flow,int duration,LocalDateTime at) { return IrrigationUnitEntity.builder().id(id).farmId(FARM_ID).entityId(entityId).name(name).targetName(target).kind(kind).mapX(x).mapY(y).enabled(enabled).flowRate(flow).durationMinutes(duration).updatedAt(at).build(); }
    private void seedAlerts() {
        LocalDateTime now=LocalDateTime.now();
        alerts.saveAll(List.of(FarmAlert.builder().farmId(FARM_ID).entityId("field-04").title("4号玉米制种田土壤湿度低于 40% 阈值").level("预警").status("未处理").occurredAt(now.minusMinutes(2)).build(),FarmAlert.builder().farmId(FARM_ID).entityId("gh-02").title("2号黄瓜温室棚内湿度持续偏高").level("关注").status("处理中").occurredAt(now.minusMinutes(37)).build(),FarmAlert.builder().farmId(FARM_ID).entityId("fertilizer-01").title("水肥一体机 EC 值短时波动").level("提醒").status("已恢复").occurredAt(now.minusHours(2)).build()));
    }

    private void ensureDemoAlerts() {
        addDemoAlertIfMissing("field-05", "5号油菜制种田土壤湿度偏低，建议滴灌补水", "预警", LocalDateTime.now().minusMinutes(8));
        addDemoAlertIfMissing("gh-03", "3号辣椒温室正午气温短时超过 35°C", "预警", LocalDateTime.now().minusMinutes(15));
    }

    private void addDemoAlertIfMissing(String entityId, String title, String level, LocalDateTime occurredAt) {
        if (alerts.existsByFarmIdAndTitle(FARM_ID, title)) return;
        alerts.save(FarmAlert.builder().farmId(FARM_ID).entityId(entityId).title(title).level(level)
                .status("未处理").occurredAt(occurredAt).build());
    }
}
