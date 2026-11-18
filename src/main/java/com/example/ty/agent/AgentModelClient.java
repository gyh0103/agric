package com.example.ty.agent;

import com.example.ty.admin.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class AgentModelClient {
    private final ObjectMapper json;
    private final SettingsService settings;
    private final AgentRuntimeSettings runtime;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).version(HttpClient.Version.HTTP_1_1).build();

    public String model() { return settings.get(SettingsService.AGENT_MODEL); }

    public JsonNode call(List<Map<String,Object>> messages,List<Map<String,Object>> tools) throws Exception {
        String base = settings.get(SettingsService.AGENT_BASE_URL);
        String key = settings.get(SettingsService.AGENT_API_KEY);
        String model = model();
        boolean local = base.contains("127.0.0.1") || base.contains("localhost");
        if (key.isBlank() && !local) {
            throw new IllegalStateException("问答模型尚未配置 API Key，请管理员在后台管理 → 系统配置中填写问答模型 Key");
        }
        String endpoint = base.replaceAll("/+$", "") + "/chat/completions";
        var body = new LinkedHashMap<String,Object>();
        body.put("model",model); body.put("messages",messages);
        if (tools != null && !tools.isEmpty()) body.put("tools",tools);
        body.put("stream",false); body.put("temperature",0.1); body.put("max_tokens",runtime.maxTokens());
        var builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(180))
            .header("Content-Type","application/json");
        if (!key.isBlank()) builder.header("Authorization","Bearer "+key);
        var response=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()<200 || response.statusCode()>=300) throw new IllegalStateException("模型请求失败（HTTP "+response.statusCode()+"），请检查接口地址与模型名称");
        var message=json.readTree(response.body()).path("choices").path(0).path("message");
        if (message.isMissingNode()) throw new IllegalStateException("模型返回格式无效");
        return message;
    }
}
