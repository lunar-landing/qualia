package com.lunarlanding.qualia.code.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lunarlanding.qualia.code.CodeAgentConfig;
import com.lunarlanding.qualia.code.CodeAgentModelConfig;
import com.lunarlanding.qualia.code.CodeAgentMcpServerConfig;
import com.lunarlanding.qualia.code.WebApplication;
import com.lunarlanding.qualia.code.service.ChatService;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.skill.SkillScript;
import com.lunarlanding.qualia.core.skill.loader.DirectorySkillLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理 API
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Path GLOBAL_CONFIG_DIR = Path.of(System.getProperty("user.home"), ".qualia");
    private static final Path GLOBAL_CONFIG_FILE = GLOBAL_CONFIG_DIR.resolve("qualia-code.json");

    /**
     * 获取当前配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        try {
            Path workspacePath = WebApplication.getCurrentWorkspace();
            CodeAgentConfig config = CodeAgentConfig.load(workspacePath);

            Map<String, Object> result = new HashMap<>();
            result.put("workspace", workspacePath.toAbsolutePath().toString());
            result.put("defaultModel", config.getDefaultModel());
            // apiKey 掩码后下发，避免明文密钥暴露给前端
            result.put("models", config.getModels().stream().map(m -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", m.getName());
                item.put("provider", m.getProvider());
                item.put("type", m.getType());
                item.put("model", m.getModel());
                item.put("baseUrl", m.getBaseUrl());
                item.put("apiKey", maskApiKey(m.getApiKey()));
                return item;
            }).collect(Collectors.toList()));
            result.put("mcpServers", config.getMcpServers());
            result.put("disabledSkills", config.getDisabledSkills());
            result.put("disabledTools", config.getDisabledTools());
            result.put("configFile", GLOBAL_CONFIG_FILE.toAbsolutePath().toString());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 更新配置
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> configData) {
        try {
            // 读取现有配置
            JSONObject config = new JSONObject();
            if (Files.exists(GLOBAL_CONFIG_FILE)) {
                String content = Files.readString(GLOBAL_CONFIG_FILE, StandardCharsets.UTF_8);
                config = JSON.parseObject(content);
            }

            // 更新字段
            if (configData.containsKey("defaultModel")) {
                config.put("defaultModel", configData.get("defaultModel"));
            }

            if (configData.containsKey("models")) {
                JSONArray incoming = JSON.parseArray(JSON.toJSONString(configData.get("models")));
                config.put("models", mergeModelApiKeys(incoming, config.getJSONArray("models")));
            }

            if (configData.containsKey("mcpServers")) {
                config.put("mcpServers", configData.get("mcpServers"));
            }

            if (configData.containsKey("disabledSkills")) {
                config.put("disabledSkills", configData.get("disabledSkills"));
            }

            if (configData.containsKey("disabledTools")) {
                config.put("disabledTools", configData.get("disabledTools"));
            }

            // 保存配置
            Files.createDirectories(GLOBAL_CONFIG_DIR);
            Files.writeString(GLOBAL_CONFIG_FILE, JSON.toJSONString(config, true), StandardCharsets.UTF_8);

            // 热生效：置空旧 Agent，下次对话时按新配置重建
            ChatService.getInstance(WebApplication.getCurrentWorkspace()).reloadConfig();

            return ResponseEntity.ok(Map.of("success", true, "message", "配置已保存，新对话将使用新配置"));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "保存配置失败: " + e.getMessage()));
        }
    }

    /**
     * 合并模型 apiKey：前端传来的 apiKey 为空或掩码值时，保留原始配置文件中的值
     * （原始值可能是 ${ENV_VAR} 写法，不能被解析后的真实密钥或掩码覆盖）
     */
    private JSONArray mergeModelApiKeys(JSONArray incoming, JSONArray existing) {
        if (incoming == null) {
            return new JSONArray();
        }
        Map<String, String> existingKeys = new HashMap<>();
        if (existing != null) {
            for (int i = 0; i < existing.size(); i++) {
                JSONObject old = existing.getJSONObject(i);
                existingKeys.put(old.getString("name"), old.getString("apiKey"));
            }
        }
        for (int i = 0; i < incoming.size(); i++) {
            JSONObject model = incoming.getJSONObject(i);
            String apiKey = model.getString("apiKey");
            if (apiKey == null || apiKey.isBlank() || apiKey.contains("****")) {
                model.put("apiKey", existingKeys.get(model.getString("name")));
            }
        }
        return incoming;
    }

    /**
     * 获取当前工作区信息
     */
    @GetMapping("/workspace")
    public ResponseEntity<Map<String, Object>> getWorkspace() {
        Path workspacePath = WebApplication.getCurrentWorkspace();
        Map<String, Object> result = new HashMap<>();
        result.put("path", workspacePath.toAbsolutePath().toString());
        result.put("name", workspacePath.getFileName().toString());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前模型配置
     */
    @GetMapping("/model")
    public ResponseEntity<Map<String, Object>> getCurrentModel() {
        try {
            Path workspacePath = WebApplication.getCurrentWorkspace();
            CodeAgentConfig config = CodeAgentConfig.load(workspacePath);
            CodeAgentModelConfig model = config.getCurrentModel();

            if (model == null) {
                return ResponseEntity.ok(Map.of("configured", false, "message", "未配置模型"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("configured", true);
            result.put("name", model.getName());
            result.put("provider", model.getProvider());
            result.put("model", model.getModel());
            result.put("baseUrl", model.getBaseUrl());
            result.put("apiKey", maskApiKey(model.getApiKey()));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取 MCP 服务器列表
     */
    @GetMapping("/mcp")
    public ResponseEntity<List<CodeAgentMcpServerConfig>> getMcpServers() {
        try {
            Path workspacePath = WebApplication.getCurrentWorkspace();
            CodeAgentConfig config = CodeAgentConfig.load(workspacePath);
            return ResponseEntity.ok(config.getMcpServers());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取可用工具列表（内置工具定义，不依赖 Agent 初始化）
     */
    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, Object>>> getAvailableTools() {
        // 内置工具清单（与 HarnessAgent.initializeAgent 一致）
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 文件操作
        tools.add(toolInfo("read", "读取文件", "file"));
        tools.add(toolInfo("grep", "搜索文件内容", "file"));
        tools.add(toolInfo("glob", "文件匹配", "file"));
        tools.add(toolInfo("replace", "替换文件内容", "file"));
        tools.add(toolInfo("write", "写入文件", "file"));
        tools.add(toolInfo("delete_file", "删除文件", "file"));
        tools.add(toolInfo("bash", "执行命令", "file"));
        
        // 网络操作
        tools.add(toolInfo("web_fetch", "网页抓取", "network"));
        tools.add(toolInfo("baidu_search", "百度搜索", "network"));
        tools.add(toolInfo("http", "HTTP请求", "network"));
        
        return ResponseEntity.ok(tools);
    }

    private Map<String, Object> toolInfo(String name, String description, String category) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", name);
        info.put("description", description);
        info.put("category", category);
        return info;
    }

    /**
     * 获取 workspace 文件列表
     */
    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> getWorkspaceFiles(@RequestParam(defaultValue = "") String path) {
        try {
            Path workspacePath = WebApplication.getCurrentWorkspace();
            Path targetPath = path.isEmpty() ? workspacePath : workspacePath.resolve(path);
            
            // 安全检查：确保路径在 workspace 内
            if (!targetPath.normalize().startsWith(workspacePath.normalize())) {
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "路径越界")));
            }
            
            if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "目录不存在")));
            }
            
            List<Map<String, Object>> files = new ArrayList<>();
            try (var stream = Files.list(targetPath)) {
                stream.sorted((a, b) -> {
                    // 目录优先，然后按名称排序
                    boolean aDir = Files.isDirectory(a);
                    boolean bDir = Files.isDirectory(b);
                    if (aDir != bDir) return aDir ? -1 : 1;
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                }).forEach(file -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", file.getFileName().toString());
                    info.put("path", workspacePath.relativize(file).toString().replace('\\', '/'));
                    info.put("isDirectory", Files.isDirectory(file));
                    if (Files.isRegularFile(file)) {
                        try {
                            info.put("size", Files.size(file));
                        } catch (IOException ignored) {}
                    }
                    files.add(info);
                });
            }
            
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(List.of(Map.of("error", e.getMessage())));
        }
    }

    // ===== 文件预览 =====

    /** 文本预览读取上限：512KB，超出部分截断并标记 truncated */
    private static final long TEXT_PREVIEW_LIMIT = 512 * 1024;
    /** 原始字节下发上限（图片预览）：20MB */
    private static final long RAW_PREVIEW_LIMIT = 20L * 1024 * 1024;
    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp");

    /**
     * 获取 workspace 文件内容（文本预览）
     * 返回 type：text（含 content/truncated）/ image（前端走 raw 接口取图）/ binary（不支持预览）
     */
    @GetMapping("/file")
    public ResponseEntity<Map<String, Object>> getWorkspaceFile(@RequestParam String path) {
        try {
            Path workspacePath = WebApplication.getCurrentWorkspace();
            Path target = workspacePath.resolve(path).normalize();

            // 安全检查：确保路径在 workspace 内
            if (!target.startsWith(workspacePath.normalize())) {
                return ResponseEntity.badRequest().body(Map.of("error", "路径越界"));
            }
            if (!Files.isRegularFile(target)) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件不存在"));
            }

            long size = Files.size(target);
            Map<String, Object> result = new HashMap<>();
            result.put("name", target.getFileName().toString());
            result.put("path", workspacePath.relativize(target).toString().replace('\\', '/'));
            result.put("size", size);

            if (IMAGE_EXTS.contains(fileExt(target))) {
                result.put("type", "image");
                return ResponseEntity.ok(result);
            }

            byte[] bytes;
            try (var in = Files.newInputStream(target)) {
                bytes = in.readNBytes((int) Math.min(size, TEXT_PREVIEW_LIMIT));
            }
            // 二进制探测：头部出现 NUL 字节视为二进制
            int scan = Math.min(bytes.length, 8000);
            for (int i = 0; i < scan; i++) {
                if (bytes[i] == 0) {
                    result.put("type", "binary");
                    return ResponseEntity.ok(result);
                }
            }

            result.put("type", "text");
            result.put("content", new String(bytes, StandardCharsets.UTF_8));
            result.put("truncated", size > TEXT_PREVIEW_LIMIT);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取 workspace 文件原始字节（图片预览用）
     */
    @GetMapping("/file/raw")
    public ResponseEntity<byte[]> getWorkspaceFileRaw(@RequestParam String path) {
        try {
            Path workspacePath = WebApplication.getCurrentWorkspace();
            Path target = workspacePath.resolve(path).normalize();

            if (!target.startsWith(workspacePath.normalize())
                    || !Files.isRegularFile(target)
                    || Files.size(target) > RAW_PREVIEW_LIMIT) {
                return ResponseEntity.badRequest().build();
            }

            String contentType = Files.probeContentType(target);
            return ResponseEntity.ok()
                    .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
                    .body(Files.readAllBytes(target));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取全局技能列表（直接读 ~/.qualia/skills，不依赖 agent，未配置模型时也可展示）
     */
    @GetMapping("/skills")
    public ResponseEntity<List<Map<String, Object>>> getGlobalSkills() {
        return ResponseEntity.ok(loadGlobalSkillsList());
    }

    /**
     * 删除全局技能（物理删除技能目录，并清理禁用列表）
     */
    @DeleteMapping("/skills/{name}")
    public ResponseEntity<Map<String, Object>> deleteGlobalSkill(@PathVariable String name) {
        try {
            Path skillDir = GLOBAL_CONFIG_DIR.resolve("skills").resolve(name);
            if (!Files.exists(skillDir)) {
                return ResponseEntity.badRequest().body(Map.of("error", "技能不存在: " + name));
            }

            // 安全检查：确保删除的是技能目录内的内容
            Path globalSkillsDir = GLOBAL_CONFIG_DIR.resolve("skills");
            if (!skillDir.normalize().startsWith(globalSkillsDir.normalize())) {
                return ResponseEntity.badRequest().body(Map.of("error", "路径越界"));
            }

            // 删除技能目录
            deleteDirectory(skillDir);

            // 清理 disabledSkills 配置
            if (Files.exists(GLOBAL_CONFIG_FILE)) {
                String content = Files.readString(GLOBAL_CONFIG_FILE, StandardCharsets.UTF_8);
                JSONObject config = JSON.parseObject(content);
                JSONArray disabled = config.getJSONArray("disabledSkills");
                if (disabled != null) {
                    List<String> list = new ArrayList<>(disabled.toJavaList(String.class));
                    list.remove(name);
                    config.put("disabledSkills", list);
                    Files.writeString(GLOBAL_CONFIG_FILE, JSON.toJSONString(config, true), StandardCharsets.UTF_8);
                }
            }

            return ResponseEntity.ok(Map.of("success", true, "message", "技能 " + name + " 已删除"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (var entries = Files.list(dir)) {
                for (Path entry : (Iterable<Path>) entries::iterator) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.delete(dir);
    }

    /**
     * 加载全局技能列表（内部方法）
     */
    private List<Map<String, Object>> loadGlobalSkillsList() {
        // 读取禁用列表
        Set<String> disabledSkills = new HashSet<>();
        if (Files.exists(GLOBAL_CONFIG_FILE)) {
            try {
                String content = Files.readString(GLOBAL_CONFIG_FILE, StandardCharsets.UTF_8);
                JSONObject config = JSON.parseObject(content);
                JSONArray arr = config.getJSONArray("disabledSkills");
                if (arr != null) {
                    disabledSkills.addAll(arr.toJavaList(String.class));
                }
            } catch (IOException ignored) {}
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Path globalSkillsDir = GLOBAL_CONFIG_DIR.resolve("skills");
        if (Files.exists(globalSkillsDir)) {
            for (Skill skill : new DirectorySkillLoader(globalSkillsDir).loadAll()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", skill.getName());
                item.put("description", skill.getDescription());
                item.put("source", "global");
                item.put("enabled", !disabledSkills.contains(skill.getName()));
                item.put("scripts", skill.getScripts().stream()
                        .map(SkillScript::getDescription)
                        .collect(Collectors.toList()));
                item.put("references", skill.getReferenceNames());
                result.add(item);
            }
        }
        return result;
    }

    private static String fileExt(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
