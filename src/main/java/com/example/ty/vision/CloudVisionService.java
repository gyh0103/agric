package com.example.ty.vision;

import com.example.ty.admin.SettingsService;
import com.example.ty.auth.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 云端多模态病害识别。
 * 兼容 OpenAI Chat Completions 协议（阿里云百炼 / 火山方舟 / 智谱 / 任意自建网关）。
 * 配置优先读取后台写入的数据库值，未配置时回落到环境变量；
 * 两者都为空时接口返回 503，不影响其余功能。
 */
@Slf4j
@Service
public class CloudVisionService {

    /** 单张图片送入模型前的最长边，压缩后通常只有几百 KB，可显著降低往返耗时。 */
    private static final int MAX_EDGE = 1280;
    /** 长图压缩阈值：小于该体积且尺寸合规的图片直接原样上传。 */
    private static final int KEEP_ORIGINAL_BELOW = 400 * 1024;
    /** 最多尝试次数；中转网关偶发连接超时与 502，重试能明显提高成功率。 */
    private static final int MAX_ATTEMPTS = 3;

    private final ObjectMapper json;
    private final SettingsService settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).version(HttpClient.Version.HTTP_1_1).build();

    public CloudVisionService(ObjectMapper json, SettingsService settings) {
        this.json = json;
        this.settings = settings;
    }

    private String baseUrl() {
        return settings.get(SettingsService.VISION_BASE_URL);
    }

    private String apiKey() {
        return settings.get(SettingsService.VISION_API_KEY);
    }

    private String model() {
        return settings.get(SettingsService.VISION_MODEL);
    }

    private int timeout() {
        try {
            int seconds = Integer.parseInt(settings.get(SettingsService.VISION_TIMEOUT).trim());
            return Math.min(Math.max(seconds, 10), 300);
        } catch (Exception e) {
            return 60;
        }
    }

    public boolean configured() {
        String key = apiKey();
        return key != null && !key.isBlank();
    }

    public Map<String, Object> status() {
        return Map.of("enabled", configured(), "model", model(),
                "message", configured() ? "云端识别已启用" : "云端识别未配置：请在后台管理填写 API Key");
    }

    public Map<String, Object> analyze(String crop, String task, MultipartFile upload) {
        if (!configured()) throw new BizException("VISION_CLOUD_NOT_CONFIGURED", "云端识别尚未配置，请管理员在后台管理中填写 API Key", 503);
        if (upload == null || upload.isEmpty()) throw BizException.badRequest("请上传一张作物照片");
        if (upload.getSize() > 8 * 1024 * 1024) throw BizException.badRequest("图片不能超过 8 MB");
        String mime = Optional.ofNullable(upload.getContentType()).orElse("image/jpeg").toLowerCase(Locale.ROOT);
        if (!mime.startsWith("image/")) throw BizException.badRequest("仅支持 JPG、PNG、WEBP 图片");
        String model = model();
        String endpoint = baseUrl().replaceAll("/+$", "") + "/chat/completions";
        try {
            byte[] bytes = upload.getBytes();
            String dataUrl = dataUrl(bytes, mime);
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", prompt(crop, task)),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                            )
                    ))
            ));
            log.info("云端识别请求 endpoint={} model={} 原图{}KB 请求体{}KB", endpoint, model, bytes.length / 1024, body.length() / 1024);
            long deadline = System.currentTimeMillis() + timeout() * 1000L;
            Exception last = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining < 15_000) break;
                long started = System.currentTimeMillis();
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                            .timeout(Duration.ofMillis(remaining))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey())
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    long elapsed = System.currentTimeMillis() - started;
                    int status = response.statusCode();
                    log.info("云端识别第{}次尝试 状态={} 耗时={}ms", attempt, status, elapsed);
                    if (status / 100 == 2) return parse(model, crop, task, contentOf(json.readTree(response.body())));
                    log.warn("云端识别返回非 2xx：{} {}", status, abbreviate(response.body()));
                    last = new IllegalStateException("HTTP " + status);
                    if (status < 500 && status != 429)
                        throw new BizException("VISION_CLOUD_FAILED", "云端识别服务返回 " + status + "，请稍后重试", 502);
                } catch (BizException e) {
                    throw e;
                } catch (java.net.http.HttpTimeoutException e) {
                    last = e;
                    log.warn("云端识别第{}次尝试超时（{}ms）：{}", attempt, System.currentTimeMillis() - started, e.toString());
                } catch (Exception e) {
                    last = e;
                    log.warn("云端识别第{}次尝试失败（{}ms）：{}", attempt, System.currentTimeMillis() - started, e.toString());
                }
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(1500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.warn("云端识别最终失败 endpoint={} 最后异常={}", endpoint, last == null ? "时间预算不足" : last.toString());
            if (last instanceof java.net.http.HttpTimeoutException)
                throw new BizException("VISION_CLOUD_TIMEOUT", "云端识别超时（已重试），请换一张更小的图片或稍后再试", 504);
            throw new BizException("VISION_CLOUD_ERROR", "云端识别服务暂时不可用（已重试），请稍后再试", 502);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("云端识别调用异常", e);
            throw new BizException("VISION_CLOUD_ERROR", "云端识别调用失败：" + e.getClass().getSimpleName(), 502);
        }
    }

    /**
     * 把上传图片压到适合模型处理的大小：长边不超过 {@link #MAX_EDGE}，
     * 体积超过阈值时重新编码为 JPEG。ImageIO 不支持 webp 时回退原图。
     */
    private String dataUrl(byte[] bytes, String mime) {
        byte[] out = bytes;
        String outMime = mime;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source != null) {
                int width = source.getWidth(), height = source.getHeight();
                double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));
                boolean needScale = scale < 1.0;
                if (needScale || bytes.length > KEEP_ORIGINAL_BELOW) {
                    int targetWidth = Math.max(1, (int) Math.round(width * scale));
                    int targetHeight = Math.max(1, (int) Math.round(height * scale));
                    BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = canvas.createGraphics();
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, targetWidth, targetHeight);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
                    graphics.dispose();
                    byte[] jpeg = jpeg(canvas);
                    if (jpeg.length > 0 && jpeg.length < bytes.length) {
                        out = jpeg;
                        outMime = "image/jpeg";
                    }
                }
            }
        } catch (Exception e) {
            log.warn("云端识别图片压缩跳过：{}", e.toString());
        }
        log.info("云端识别图片：{}KB -> {}KB ({})", bytes.length / 1024, out.length / 1024, outMime);
        return "data:" + outMime + ";base64," + Base64.getEncoder().encodeToString(out);
    }

    private static byte[] jpeg(BufferedImage image) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    private static String contentOf(JsonNode body) {
        JsonNode content = body.path("choices").path(0).path("message").path("content");
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            content.forEach(part -> text.append(part.path("text").asString("")));
            return text.toString();
        }
        return content.asString("");
    }

    private Map<String, Object> parse(String model, String crop, String task, String content) {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engine", model);
        result.put("crop", crop);
        result.put("task", task);
        if (start >= 0 && end > start) {
            try {
                JsonNode node = json.readTree(text.substring(start, end + 1));
                result.put("label", node.path("disease").asString("未能确定"));
                result.put("code", node.path("code").asString(""));
                result.put("confidence", node.path("confidence").asDouble(0));
                result.put("severity", node.path("severity").asString("未知"));
                result.put("symptoms", strings(node.path("symptoms")));
                result.put("advice", strings(node.path("advice")));
                result.put("note", node.path("note").asString(""));
                return result;
            } catch (Exception ignored) {
                // 模型偶尔会返回非 JSON，保留原文交给前端展示
            }
        }
        result.put("label", "识别结果需人工复核");
        result.put("code", "");
        result.put("confidence", 0);
        result.put("severity", "未知");
        result.put("symptoms", List.of());
        result.put("advice", List.of());
        result.put("note", text.length() > 600 ? text.substring(0, 600) : text);
        return result;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) array.forEach(item -> {
            String value = item.asString("");
            if (!value.isBlank()) values.add(value);
        });
        return values;
    }

    private static String prompt(String crop, String task) {
        return "你是甘肃河西走廊（张掖、武威、酒泉、金昌）的农业植保专家，熟悉当地主栽作物：制种玉米、马铃薯、酿酒葡萄、枸杞、蜜瓜、红枣、高原夏菜、小麦。"
                + "请判断这张照片中的" + (crop == null || crop.isBlank() ? "作物" : crop) + "的" + (task == null || task.isBlank() ? "长势与病害" : task) + "情况。"
                + "只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释文字。字段："
                + "{\"disease\":\"最可能的病害或生理问题名称（中文，无法判断写“未见明显异常”）\","
                + "\"code\":\"英文小写代号，如 leaf_rust，无法判断写 unknown\","
                + "\"confidence\":0到1之间的小数,"
                + "\"severity\":\"无/轻度/中度/重度之一\","
                + "\"symptoms\":[\"照片中可见的症状，最多4条\"],"
                + "\"advice\":[\"结合河西走廊气候与水肥条件的处置建议，最多4条\"],"
                + "\"note\":\"需要补充哪些观察才能确诊，一句话\"}。"
                + "如果照片不是作物或无法辨认，disease 写“无法识别”，confidence 写 0。";
    }
}
