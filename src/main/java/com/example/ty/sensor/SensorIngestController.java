package com.example.ty.sensor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 传感器数据接入端点。网关转过来的 JSON 直接 POST 即可。
 * 请求格式（两种任选一种）：
 *
 * A) 带密钥的批量（推荐）：
 *    {
 *      "secret": "在后台系统配置 → 传感器上报密钥",
 *      "readings": [
 *        { "entityId": "weather-01", "metricKey": "temperature", "value": 24.6, "unit": "°C" },
 *        { "entityId": "weather-01", "metricKey": "airHumidity", "value": 58, "unit": "%" }
 *      ]
 *    }
 *
 * B) 裸数组（后台未配置密钥时可用）：
 *    [ { "entityId": "weather-01", "metricKey": "temperature", "value": 24.6 } ]
 *
 * metricKey 支持别名：temp→temperature, humidity→airHumidity, soil_moisture→soilMoisture, lux→light, co2_ppm→co2。
 */
@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorIngestController {

    private final SensorIngestService ingest;
    private final ObjectMapper json;

    @PostMapping(value = "/ingest", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ingest(@RequestBody JsonNode body) {
        int count;
        try {
            if (body.isArray()) {
                List<SensorIngestService.Reading> readings = json.readValue(json.treeAsTokens(body),
                        json.getTypeFactory().constructCollectionType(List.class, SensorIngestService.Reading.class));
                count = ingest.ingest(readings);
            } else {
                SensorIngestService.BatchReadings batch = json.treeToValue(body, SensorIngestService.BatchReadings.class);
                count = ingest.ingest(batch);
            }
        } catch (com.example.ty.auth.exception.BizException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("传感器上报解析失败：" + e.getMessage(), e);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("applied", count);
        resp.put("timestamp", java.time.LocalDateTime.now().toString());
        return resp;
    }
}
