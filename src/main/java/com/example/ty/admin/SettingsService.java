package com.example.ty.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 配置读写：数据库覆盖值优先，未覆盖时回落到环境变量/默认值。
 * 空字符串表示删除覆盖，回到环境变量。
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String VISION_BASE_URL = "vision.cloud.base-url";
    public static final String VISION_API_KEY = "vision.cloud.api-key";
    public static final String VISION_MODEL = "vision.cloud.model";
    public static final String VISION_TIMEOUT = "vision.cloud.timeout-seconds";
    public static final String AGENT_BASE_URL = "agent.base-url";
    public static final String AGENT_API_KEY = "agent.api-key";
    public static final String AGENT_MODEL = "agent.model";
    public static final String VISION_PROMPT = "vision.prompt";
    public static final String AGENT_PROMPT = "agent.prompt";
    public static final String VISION_MAX_EDGE = "vision.cloud.max-edge";

    private final AppSettingRepository repo;

    @Value("${VISION_CLOUD_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String defaultVisionBaseUrl;
    @Value("${VISION_CLOUD_API_KEY:}")
    private String defaultVisionApiKey;
    @Value("${VISION_CLOUD_MODEL:qwen-vl-plus}")
    private String defaultVisionModel;
    @Value("${VISION_CLOUD_TIMEOUT_SECONDS:60}")
    private String defaultVisionTimeout;
    @Value("${agent.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String defaultAgentBaseUrl;
    @Value("${agent.api-key:}")
    private String defaultAgentApiKey;
    @Value("${agent.model:qwen-plus}")
    private String defaultAgentModel;
    @Value("${VISION_PROMPT:}")
    private String defaultVisionPrompt;
    @Value("${AGENT_PROMPT:}")
    private String defaultAgentPrompt;
    @Value("${VISION_CLOUD_MAX_EDGE:1280}")
    private String defaultVisionMaxEdge;

    /** 环境变量（或内置默认）值，作为数据库未覆盖时的兜底 */
    public String envDefault(String key) {
        return switch (key) {
            case VISION_BASE_URL -> defaultVisionBaseUrl;
            case VISION_API_KEY -> defaultVisionApiKey;
            case VISION_MODEL -> defaultVisionModel;
            case VISION_TIMEOUT -> defaultVisionTimeout;
            case AGENT_BASE_URL -> defaultAgentBaseUrl;
            case AGENT_API_KEY -> defaultAgentApiKey;
            case AGENT_MODEL -> defaultAgentModel;
            case VISION_PROMPT -> defaultVisionPrompt;
            case AGENT_PROMPT -> defaultAgentPrompt;
            case VISION_MAX_EDGE -> defaultVisionMaxEdge;
            default -> "";
        };
    }

    public String get(String key) {
        return get(key, envDefault(key));
    }

    public String get(String key, String fallback) {
        return repo.findById(key)
                .map(AppSetting::getValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    public boolean isOverridden(String key) {
        return repo.findById(key).map(AppSetting::getValue).filter(v -> v != null && !v.isBlank()).isPresent();
    }

    /** 写入覆盖值；空白值表示删除覆盖，回落到环境变量 */
    public void put(String key, String value) {
        if (value == null || value.isBlank()) {
            repo.findById(key).ifPresent(repo::delete);
            return;
        }
        AppSetting setting = repo.findById(key).orElseGet(() -> new AppSetting(key, null, null));
        setting.setValue(value.trim());
        setting.setUpdatedAt(LocalDateTime.now());
        repo.save(setting);
    }
}
