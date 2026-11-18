package com.example.ty.admin;

import com.example.ty.assistant.knowledge.KnowledgeDocumentRepository;
import com.example.ty.auth.entity.User;
import com.example.ty.auth.exception.BizException;
import com.example.ty.auth.repository.UserRepository;
import com.example.ty.auth.security.AuthInterceptor;
import com.example.ty.catalog.CropProfile;
import com.example.ty.catalog.CropProfileRepository;
import com.example.ty.catalog.DiseaseProfile;
import com.example.ty.catalog.DiseaseProfileRepository;
import com.example.ty.operations.OperationRepository;
import com.example.ty.vision.CloudVisionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.security.SecureRandom;
import java.util.*;

/**
 * 后台管理接口。所有方法先校验当前登录用户角色为 ADMIN。
 * 提供：总览、用户管理、运行期配置（云端识别 Key 等）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Set<String> EDITABLE_KEYS = Set.of(
            SettingsService.VISION_BASE_URL, SettingsService.VISION_API_KEY,
            SettingsService.VISION_MODEL, SettingsService.VISION_TIMEOUT,
            SettingsService.AGENT_BASE_URL, SettingsService.AGENT_API_KEY, SettingsService.AGENT_MODEL,
            SettingsService.VISION_PROMPT, SettingsService.AGENT_PROMPT, SettingsService.VISION_MAX_EDGE,
            SettingsService.SENSOR_SIMULATED, SettingsService.SENSOR_INGEST_SECRET);

    private final UserRepository users;
    private final OperationRepository operations;
    private final KnowledgeDocumentRepository knowledge;
    private final SettingsService settings;
    private final CloudVisionService vision;
    private final PasswordEncoder passwordEncoder;
    private final CropProfileRepository crops;
    private final DiseaseProfileRepository diseases;
    private final com.example.ty.dashboard.repository.FarmAssetRepository plots;

    private User requireAdmin(HttpServletRequest request) {
        User current = (User) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTR);
        if (current == null || !"ADMIN".equalsIgnoreCase(current.getRole())) {
            throw new BizException("FORBIDDEN", "需要管理员权限", 403);
        }
        return current;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpServletRequest request) {
        requireAdmin(request);
        Runtime rt = Runtime.getRuntime();
        File root = new File("/");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("users", users.count());
        body.put("admins", users.countByRole("ADMIN"));
        body.put("operations", operations.count());
        body.put("knowledge", knowledge.count());
        body.put("visionConfigured", vision.configured());
        body.put("visionModel", vision.status().get("model"));
        body.put("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        body.put("memoryMaxMb", rt.maxMemory() / 1024 / 1024);
        body.put("uptimeMinutes", ManagementFactory.getRuntimeMXBean().getUptime() / 60000);
        body.put("diskFreeMb", root.getUsableSpace() / 1024 / 1024);
        body.put("diskTotalMb", root.getTotalSpace() / 1024 / 1024);
        return body;
    }

    @GetMapping("/users")
    public Map<String, Object> listUsers(@RequestParam(defaultValue = "") String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         HttpServletRequest request) {
        requireAdmin(request);
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        List<User> matched = users.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(u -> kw.isEmpty()
                        || u.getName().toLowerCase(Locale.ROOT).contains(kw)
                        || u.getEmail().toLowerCase(Locale.ROOT).contains(kw))
                .toList();
        int pageSize = Math.min(Math.max(size, 1), 100);
        int from = Math.max(0, (Math.max(page, 1) - 1) * pageSize);
        List<User> slice = from >= matched.size() ? List.of() : matched.subList(from, Math.min(matched.size(), from + pageSize));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", matched.size());
        body.put("page", Math.max(page, 1));
        body.put("size", pageSize);
        body.put("items", slice.stream().map(this::view).toList());
        return body;
    }

    @PatchMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body,
                                          HttpServletRequest request) {
        User current = requireAdmin(request);
        User target = users.findById(id).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (body.containsKey("role")) {
            String role = String.valueOf(body.get("role")).trim().toUpperCase(Locale.ROOT);
            if (!role.equals("ADMIN") && !role.equals("USER")) {
                throw BizException.badRequest("角色只能是 ADMIN 或 USER");
            }
            if (id.equals(current.getId()) && !role.equals("ADMIN")) {
                throw BizException.badRequest("不能取消自己的管理员权限");
            }
            if (role.equals("USER") && isAdmin(target) && users.countByRole("ADMIN") <= 1) {
                throw BizException.badRequest("至少需要保留一名管理员");
            }
            target.setRole(role);
        }
        if (body.containsKey("enabled")) {
            boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            if (id.equals(current.getId()) && !enabled) {
                throw BizException.badRequest("不能禁用自己的账号");
            }
            target.setEnabled(enabled);
        }
        users.save(target);
        return view(target);
    }

    @PostMapping("/users/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id,
                                             @RequestBody(required = false) Map<String, String> body,
                                             HttpServletRequest request) {
        requireAdmin(request);
        User target = users.findById(id).orElseThrow(() -> BizException.notFound("用户不存在"));
        String password = body == null ? null : body.get("password");
        if (password == null || password.isBlank()) {
            password = randomPassword();
        }
        if (password.length() < 8) {
            throw BizException.badRequest("密码至少 8 位");
        }
        target.setPasswordHash(passwordEncoder.encode(password));
        users.save(target);
        Map<String, Object> body2 = new LinkedHashMap<>();
        body2.put("email", target.getEmail());
        body2.put("password", password);
        return body2;
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        User current = requireAdmin(request);
        User target = users.findById(id).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (id.equals(current.getId())) {
            throw BizException.badRequest("不能删除自己的账号");
        }
        if (isAdmin(target) && users.countByRole("ADMIN") <= 1) {
            throw BizException.badRequest("至少需要保留一名管理员");
        }
        users.delete(target);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", id);
        return result;
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings(HttpServletRequest request) {
        requireAdmin(request);
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(settingItem(SettingsService.VISION_BASE_URL, "接口地址", false, "OpenAI 兼容协议的 baseURL，例如 https://dashscope.aliyuncs.com/compatible-mode/v1"));
        items.add(settingItem(SettingsService.VISION_API_KEY, "API Key", true, "留空表示不修改；填写后立即生效"));
        items.add(settingItem(SettingsService.VISION_MODEL, "模型名称", false, "如 qwen-vl-plus / glm-4v-flash / 火山 Endpoint ID"));
        items.add(settingItem(SettingsService.VISION_TIMEOUT, "超时（秒）", false, "10 ~ 300"));
        items.add(settingItem(SettingsService.AGENT_BASE_URL, "问答模型接口地址", false, "用于智能问农，OpenAI 兼容 baseURL；留空回落到默认值"));
        items.add(settingItem(SettingsService.AGENT_API_KEY, "问答模型 API Key", true, "留空表示不修改；填写后立即生效"));
        items.add(settingItem(SettingsService.AGENT_MODEL, "问答模型名称", false, "需支持工具调用，如 qwen-plus / qwen-max / deepseek-chat"));
        items.add(settingItem(SettingsService.VISION_PROMPT, "视觉识别提示词", false, "留空使用内置提示词；可用 {{crop}}、{{task}} 占位。JSON 输出格式会自动追加"));
        items.add(settingItem(SettingsService.AGENT_PROMPT, "智能问农提示词", false, "留空使用内置提示词；用于设定助手角色、语气和回答边界"));
        items.add(settingItem(SettingsService.VISION_MAX_EDGE, "图片最长边（像素）", false, "上传图片压缩后的最长边，640 ~ 2048，默认 1280"));
        items.add(settingItem(SettingsService.SENSOR_SIMULATED, "开启模拟传感器", false, "true = 每 30 秒自动生成模拟读数（默认）；false = 等待真实传感器 /api/sensor/ingest 上报。立即生效，无需重启"));
        items.add(settingItem(SettingsService.SENSOR_INGEST_SECRET, "传感器上报密钥", true, "留空表示不校验；填写后，/api/sensor/ingest 请求必须在 JSON 里带上 \"secret\" 字段。立即生效"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("visionConfigured", vision.configured());
        return body;
    }

    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Map<String, String> body, HttpServletRequest request) {
        requireAdmin(request);
        body.forEach((key, value) -> {
            if (EDITABLE_KEYS.contains(key)) {
                settings.put(key, value);
            }
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("visionConfigured", vision.configured());
        return result;
    }

    // ---------- 地块（种植区 / 温室） ----------

    @GetMapping("/plots")
    public List<com.example.ty.dashboard.entity.FarmAsset> listPlots(@RequestParam(defaultValue = "zhangye-01") String farmId,
                                                                    HttpServletRequest request) {
        requireAdmin(request);
        return plots.findByFarmIdOrderById(farmId).stream()
                .filter(item -> Set.of("field", "greenhouse").contains(item.getType()))
                .toList();
    }

    @PutMapping("/plots/{id}")
    public com.example.ty.dashboard.entity.FarmAsset updatePlot(@PathVariable String id,
                                                                @RequestBody Map<String, Object> body,
                                                                HttpServletRequest request) {
        requireAdmin(request);
        com.example.ty.dashboard.entity.FarmAsset plot = plots.findById(id)
                .orElseThrow(() -> BizException.notFound("地块不存在"));
        if (!Set.of("field", "greenhouse").contains(plot.getType()))
            throw BizException.badRequest("该资产不是地块");
        if (body.get("name") != null) plot.setName(String.valueOf(body.get("name")).trim());
        if (body.get("crop") != null) plot.setCrop(String.valueOf(body.get("crop")).trim());
        if (body.get("area") != null) plot.setArea(String.valueOf(body.get("area")).trim());
        if (body.get("growthStage") != null) plot.setGrowthStage(String.valueOf(body.get("growthStage")).trim());
        if (body.get("environmentSummary") != null)
            plot.setEnvironmentSummary(String.valueOf(body.get("environmentSummary")).trim());
        if (body.get("health") instanceof Number number)
            plot.setHealth(Math.max(0, Math.min(100, number.intValue())));
        return plots.save(plot);
    }

    // ---------- 作物档案 ----------

    @GetMapping("/crops")
    public List<CropProfile> listCrops(HttpServletRequest request) {
        requireAdmin(request);
        return crops.findAllByOrderBySortOrderAscNameAsc();
    }

    @PostMapping("/crops")
    public CropProfile createCrop(@RequestBody CropProfile body, HttpServletRequest request) {
        requireAdmin(request);
        if (body.getId() == null || body.getId().isBlank())
            throw BizException.badRequest("请填写作物编号");
        if (crops.existsById(body.getId())) throw BizException.badRequest("作物编号已存在");
        return crops.save(normalize(body));
    }

    @PutMapping("/crops/{id}")
    public CropProfile updateCrop(@PathVariable String id, @RequestBody CropProfile body, HttpServletRequest request) {
        requireAdmin(request);
        if (!crops.existsById(id)) throw BizException.notFound("作物不存在");
        body.setId(id);
        return crops.save(normalize(body));
    }

    @DeleteMapping("/crops/{id}")
    public Map<String, Object> deleteCrop(@PathVariable String id, HttpServletRequest request) {
        requireAdmin(request);
        crops.deleteById(id);
        return Map.of("deleted", id);
    }

    private static CropProfile normalize(CropProfile body) {
        if (body.getName() == null || body.getName().isBlank()) throw BizException.badRequest("请填写作物名称");
        if (body.getEnabled() == null) body.setEnabled(true);
        if (body.getSortOrder() == null) body.setSortOrder(0);
        return body;
    }

    // ---------- 病害知识库 ----------

    @GetMapping("/diseases")
    public List<DiseaseProfile> listDiseases(HttpServletRequest request) {
        requireAdmin(request);
        return diseases.findAllByOrderByCropAscSortOrderAsc();
    }

    @PostMapping("/diseases")
    public DiseaseProfile createDisease(@RequestBody DiseaseProfile body, HttpServletRequest request) {
        requireAdmin(request);
        body.setId(null);
        return diseases.save(normalize(body));
    }

    @PutMapping("/diseases/{id}")
    public DiseaseProfile updateDisease(@PathVariable Long id, @RequestBody DiseaseProfile body, HttpServletRequest request) {
        requireAdmin(request);
        if (!diseases.existsById(id)) throw BizException.notFound("病害条目不存在");
        body.setId(id);
        return diseases.save(normalize(body));
    }

    @DeleteMapping("/diseases/{id}")
    public Map<String, Object> deleteDisease(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        diseases.deleteById(id);
        return Map.of("deleted", id);
    }

    private static DiseaseProfile normalize(DiseaseProfile body) {
        if (body.getCrop() == null || body.getCrop().isBlank()) throw BizException.badRequest("请填写作物");
        if (body.getName() == null || body.getName().isBlank()) throw BizException.badRequest("请填写病害名称");
        if (body.getEnabled() == null) body.setEnabled(true);
        if (body.getSortOrder() == null) body.setSortOrder(0);
        return body;
    }

    private Map<String, Object> settingItem(String key, String label, boolean secret, String hint) {
        String effective = settings.get(key);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("secret", secret);
        item.put("value", secret ? mask(effective) : effective);
        item.put("configured", effective != null && !effective.isBlank());
        item.put("overridden", settings.isOverridden(key));
        item.put("hint", hint);
        return item;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 8) return "********";
        return value.substring(0, 3) + "********" + value.substring(value.length() - 4);
    }

    private static boolean isAdmin(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private static String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return sb.toString();
    }

    private Map<String, Object> view(User u) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", u.getId());
        item.put("name", u.getName());
        item.put("email", u.getEmail());
        item.put("phone", u.getPhone());
        item.put("role", u.getRole());
        item.put("enabled", !Boolean.FALSE.equals(u.getEnabled()));
        item.put("faceBound", u.getFaceToken() != null);
        item.put("createdAt", u.getCreatedAt());
        return item;
    }
}
