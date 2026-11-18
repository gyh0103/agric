package com.example.ty.weather;

import com.example.ty.admin.SettingsService;
import com.example.ty.auth.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class WeatherService {
    private final ObjectMapper objectMapper;
    private final SettingsService settings;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 每次调用动态读 SettingsService —— 后台改完立即生效，不用重启 */
    private double latitude() {
        try { return Double.parseDouble(settings.get(SettingsService.WEATHER_LATITUDE).trim()); }
        catch (Exception e) { return 38.9262; }
    }
    private double longitude() {
        try { return Double.parseDouble(settings.get(SettingsService.WEATHER_LONGITUDE).trim()); }
        catch (Exception e) { return 100.4484; }
    }
    public String cityLabel() {
        return settings.get(SettingsService.WEATHER_CITY, "张掖");
    }

    public WeatherCurrentResponse current() {
        double lat = latitude(), lon = longitude();
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,shortwave_radiation,weather_code"
                + "&wind_speed_unit=ms&timezone=auto";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode current = root.path("current");
            return new WeatherCurrentResponse("Open-Meteo",
                    root.path("latitude").asDouble(lat),
                    root.path("longitude").asDouble(lon),
                    current.path("time").asText(),
                    current.path("temperature_2m").asDouble(),
                    current.path("relative_humidity_2m").asDouble(),
                    current.path("wind_speed_10m").asDouble(),
                    current.path("shortwave_radiation").asDouble(),
                    current.path("weather_code").asInt(),
                    cityLabel());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("WEATHER_UNAVAILABLE", "实时天气服务暂时不可用", 503);
        } catch (Exception error) {
            throw new BizException("WEATHER_UNAVAILABLE", "实时天气服务暂时不可用：" + error.getMessage(), 503);
        }
    }
}
