package com.example.ty.admin;

import com.example.ty.assistant.knowledge.KnowledgeDocumentRepository;
import com.example.ty.auth.entity.User;
import com.example.ty.auth.exception.BizException;
import com.example.ty.auth.repository.UserRepository;
import com.example.ty.auth.security.AuthInterceptor;
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
            SettingsService.VISION_MODEL, SettingsService.VISION_TIMEOUT);

    private final UserRepository users;
    private final OperationRepository operations;
    private final KnowledgeDocumentRepository knowledge;
    private final SettingsService settings;
    private final CloudVisionService vision;
    private final PasswordEncoder passwordEncoder;

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
