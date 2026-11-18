package com.example.ty.sensor;

import com.example.ty.auth.exception.BizException;
import com.example.ty.dashboard.entity.EnvironmentMetric;
import com.example.ty.dashboard.entity.FarmDevice;
import com.example.ty.dashboard.entity.Observation;
import com.example.ty.dashboard.repository.EnvironmentMetricRepository;
import com.example.ty.dashboard.repository.FarmDeviceRepository;
import com.example.ty.dashboard.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 通用传感器数据接入层。
 * 接收外部网关（MQTT Bridge / ThingsBoard / 阿里云 IoT / 自定义采集器）转成的 JSON，
 * 更新 EnvironmentMetric / 追加 Observation / 刷新 FarmDevice 心跳，
 * 使整条仪表盘→告警→灌溉联动链路直接生效，前端零改动。
 *
 * 请求体支持两种：
 * 1) 单条 BatchReadings（带 secret + readings），方便网关一次发多条；
 * 2) 裸数组 List<Reading>，每条独立包含 deviceId/entityId。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SensorIngestService {

    private final EnvironmentMetricRepository metrics;
    private final FarmDeviceRepository devices;
    private final ObservationRepository observations;
    private final com.example.ty.admin.SettingsService settings;

    /** 网关发来的 metric 名 → 系统 metricKey 的别名映射。可通过后台扩展，但最常见的先写死。 */
    private static final Map<String, String> METRIC_ALIASES = Map.ofEntries(
            // 温度
            Map.entry("temperature", "temperature"),
            Map.entry("temp", "temperature"),
            Map.entry("temperature_2m", "temperature"),
            Map.entry("air_temperature", "temperature"),
            Map.entry("airtemp", "temperature"),
            // 空气湿度
            Map.entry("airHumidity", "airHumidity"),
            Map.entry("humidity", "airHumidity"),
            Map.entry("relative_humidity", "airHumidity"),
            Map.entry("rh", "airHumidity"),
            // 土壤湿度
            Map.entry("soilMoisture", "soilMoisture"),
            Map.entry("soil_moisture", "soilMoisture"),
            Map.entry("soil_moisture_pct", "soilMoisture"),
            Map.entry("sm", "soilMoisture"),
            // 光照
            Map.entry("light", "light"),
            Map.entry("light_intensity", "light"),
            Map.entry("lux", "light"),
            Map.entry("shortwave_radiation", "light"),
            // CO₂
            Map.entry("co2", "co2"),
            Map.entry("co2_ppm", "co2"),
            Map.entry("co2_concentration", "co2")
    );

    public record Reading(String deviceId, String entityId, String metricKey, double value, String unit, String measuredAt) {}
    public record BatchReadings(String secret, List<Reading> readings) {}

    /** 接收网关一次发的一批 */
    @Transactional
    public int ingest(BatchReadings batch) {
        verifySecret(batch.secret());
        return processReadings(batch.readings());
    }

    /** 接收裸数组 */
    @Transactional
    public int ingest(List<Reading> readings) {
        return processReadings(readings);
    }

    private void verifySecret(String secret) {
        String configured = settings.get(com.example.ty.admin.SettingsService.SENSOR_INGEST_SECRET);
        if (configured == null || configured.isBlank()) return;
        if (secret == null || !secret.equals(configured)) {
            throw new BizException("SENSOR_INGEST_FORBIDDEN", "传感器上报密钥不正确", 403);
        }
    }

    private int processReadings(List<Reading> readings) {
        if (readings == null || readings.isEmpty()) return 0;
        LocalDateTime now = LocalDateTime.now();
        int applied = 0;
        String currentDevice = null;
        for (Reading r : readings) {
            if (r.metricKey() == null || r.metricKey().isBlank()) continue;
            String resolvedKey = resolveMetricKey(r.metricKey());
            if (resolvedKey == null) {
                log.warn("跳过未注册 metricKey={}, 当前 device={}", r.metricKey(), r.deviceId());
                continue;
            }
            String farmId = resolveFarmId(r);
            if (farmId == null) {
                log.warn("无法从 deviceId/entityId={} 解析农场，跳过", r.deviceId());
                continue;
            }
            LocalDateTime measured = parseTime(r.measuredAt(), now);
            // 找 EnvironmentMetric（按 farmId + metricKey 唯一）
            List<EnvironmentMetric> all = metrics.findByFarmIdOrderById(farmId);
            EnvironmentMetric metric = all.stream().filter(m -> m.getMetricKey().equals(resolvedKey)).findFirst().orElse(null);
            if (metric == null) {
                log.info("农场 {} 未预注册指标 {}，自动创建", farmId, resolvedKey);
                metric = EnvironmentMetric.builder()
                        .farmId(farmId).metricKey(resolvedKey).label(resolvedKey)
                        .unit(r.unit() == null || r.unit().isBlank() ? defaultUnit(resolvedKey) : r.unit())
                        .value(r.value()).previousValue(r.value()).tone("info").measuredAt(measured).build();
            } else {
                metric.setPreviousValue(metric.getValue());
                metric.setValue(r.value());
                metric.setUnit(r.unit() == null || r.unit().isBlank() ? metric.getUnit() : r.unit());
                metric.setMeasuredAt(measured);
                metric.setTone(toneFor(resolvedKey, r.value()));
            }
            metrics.save(metric);
            observations.save(Observation.builder()
                    .farmId(farmId).metricKey(resolvedKey).label(metric.getLabel())
                    .value(r.value()).unit(metric.getUnit()).source("sensor_ingest")
                    .measuredAt(measured).build());
            // 刷新设备心跳
            FarmDevice device = resolveDevice(r);
            if (device != null) {
                device.setLastSeenAt(measured);
                device.setOnline(true);
                devices.save(device);
                currentDevice = device.getName();
            }
            applied++;
        }
        log.info("传感器上报已应用 {} 条 device={} farm={}", applied, currentDevice, readings.stream().map(this::resolveFarmId).filter(Objects::nonNull).findFirst().orElse("?"));
        return applied;
    }

    private String resolveMetricKey(String key) {
        if (key == null) return null;
        String k = key.trim();
        if (METRIC_ALIASES.containsKey(k)) return METRIC_ALIASES.get(k);
        // 允许上游直接用系统 key
        return METRIC_ALIASES.containsValue(k) ? k : null;
    }

    private String resolveFarmId(Reading r) {
        // 策略：用 entityId 或 deviceId 去 FarmDevice 表查 farmId；没命中则 fallback 到默认 zhangye-01
        List<FarmDevice> all = devices.findAll();
        String hint = r.entityId() != null && !r.entityId().isBlank() ? r.entityId() : r.deviceId();
        if (hint != null) {
            Optional<FarmDevice> match = all.stream().filter(d -> hint.equals(d.getEntityId()) || hint.equals(d.getId())).findFirst();
            if (match.isPresent()) return match.get().getFarmId();
        }
        return "zhangye-01";
    }

    private FarmDevice resolveDevice(Reading r) {
        List<FarmDevice> all = devices.findAll();
        String hint = r.entityId() != null && !r.entityId().isBlank() ? r.entityId() : r.deviceId();
        if (hint == null) return null;
        return all.stream().filter(d -> hint.equals(d.getEntityId()) || hint.equals(d.getId())).findFirst().orElse(null);
    }

    private static LocalDateTime parseTime(String iso, LocalDateTime fallback) {
        if (iso == null || iso.isBlank()) return fallback;
        try {
            return LocalDateTime.parse(iso.replace("Z", ""));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String defaultUnit(String key) {
        return switch (key) {
            case "temperature" -> "°C";
            case "airHumidity", "soilMoisture" -> "%";
            case "light" -> "lx";
            case "co2" -> "ppm";
            default -> "";
        };
    }

    private static String toneFor(String key, double value) {
        return switch (key) {
            case "temperature" -> value > 33 || value < 5 ? "alert" : value > 30 ? "warn" : "good";
            case "soilMoisture" -> value < 35 ? "alert" : value < 45 ? "warn" : "good";
            case "co2" -> value > 1000 ? "warn" : "good";
            default -> "good";
        };
    }
}
