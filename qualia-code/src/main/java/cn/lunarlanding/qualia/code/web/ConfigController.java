package cn.lunarlanding.qualia.code.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.lunarlanding.qualia.code.CodeAgentConfig;
import cn.lunarlanding.qualia.code.CodeAgentModelConfig;
import cn.lunarlanding.qualia.code.CodeAgentMcpServerConfig;
import cn.lunarlanding.qualia.code.WebApplication;
import cn.lunarlanding.qualia.code.service.ChatService;
import cn.lunarlanding.qualia.code.service.SkillMarketService;
import cn.lunarlanding.qualia.code.service.WorkspaceDataStore;
import cn.lunarlanding.qualia.core.skill.Skill;
import cn.lunarlanding.qualia.core.skill.SkillScript;
import cn.lunarlanding.qualia.core.skill.loader.DirectorySkillLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置管理 API
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);

    /** 技能等资源随产品目录隔离，配置与技能读写都在 ~/.qualia/code/ 下 */
    private static final Path GLOBAL_CONFIG_DIR = CodeAgentConfig.GLOBAL_CONFIG_DIR;
    private static final Path GLOBAL_CONFIG_FILE = CodeAgentConfig.GLOBAL_CONFIG_FILE;

    private final SkillMarketService skillMarketService;

    public ConfigController(SkillMarketService skillMarketService) {
        this.skillMarketService = skillMarketService;
    }

    /** 内置默认系统提示词（与 core ReActAgent 的初始值保持对齐） */
    private static final String DEFAULT_AGENT_PROMPT = "你是一个智能助手。";

    /**
     * 读取全局系统提示词（~/.qualia/code/AGENT.md）；文件不存在时 exists=false、content 为空
     */
    @GetMapping("/agent-md")
    public Map<String, Object> getAgentMd() {
        Map<String, Object> res = new HashMap<>();
        res.put("defaultPrompt", DEFAULT_AGENT_PROMPT);
        Path file = GLOBAL_CONFIG_DIR.resolve("AGENT.md");
        if (!Files.exists(file)) {
            res.put("exists", false);
            res.put("content", "");
            return res;
        }
        try {
            res.put("exists", true);
            res.put("content", Files.readString(file));
        } catch (IOException e) {
            logger.error("读取 AGENT.md 失败", e);
            res.put("exists", false);
            res.put("content", "");
        }
        return res;
    }

    /**
     * 保存全局系统提示词并对当前 Agent 热生效；空内容 = 删除文件回退默认提示词
     */
    @PutMapping("/agent-md")
    public Map<String, Object> saveAgentMd(@RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", "");
        Path file = GLOBAL_CONFIG_DIR.resolve("AGENT.md");
        try {
            if (content.isBlank()) {
                Files.deleteIfExists(file);
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, content);
            }
            // 活跃 Agent 存在时热更新提示词；未初始化时下次 initialize 自动读文件
            if (ChatService.activeRoot() != null) {
                ChatService.getInstance().applySystemPrompt(content.isBlank() ? DEFAULT_AGENT_PROMPT : content);
            }
            return Map.of("success", true);
        } catch (IOException e) {
            logger.error("保存 AGENT.md 失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 获取当前配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        try {
            Path workspacePath = WebApplication.getCurrentWorkspace();
            CodeAgentConfig config = CodeAgentConfig.load(workspacePath);

            Map<String, Object> result = new HashMap<>();
            // 启动时未绑定工作区（前端强制选择流程）时下发 null，全局配置仍可用
            result.put("workspace", workspacePath != null ? workspacePath.toAbsolutePath().toString() : null);
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
            // 派生工作区存储位置：null = 默认 ~/.qualia/code/workspaces；默认路径供前端 placeholder 展示
            result.put("derivedWorkspaceDir", config.getDerivedWorkspaceDir());
            result.put("defaultDerivedDir", CodeAgentConfig.defaultWorkspacesDir().toString());
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

            // 派生工作区存储位置：空值 = 恢复默认；非空校验绝对路径且目录可创建
            boolean derivedDirChanged = configData.containsKey("derivedWorkspaceDir");
            Path previousDerivedDir = derivedDirChanged ? WorkspaceDataStore.readDerivedDirSetting() : null;
            if (derivedDirChanged) {
                Object raw = configData.get("derivedWorkspaceDir");
                String dir = raw == null ? null : String.valueOf(raw).trim();
                if (dir == null || dir.isEmpty()) {
                    config.put("derivedWorkspaceDir", null); // fastjson 序列化不输出 null，即回到默认位置
                } else {
                    Path p = Path.of(dir);
                    if (!p.isAbsolute()) {
                        return ResponseEntity.badRequest().body(Map.of("error", "派生工作区存储位置必须为绝对路径"));
                    }
                    try {
                        Files.createDirectories(p);
                    } catch (IOException ioe) {
                        return ResponseEntity.badRequest().body(Map.of("error", "目录不可创建或不可写: " + ioe.getMessage()));
                    }
                    config.put("derivedWorkspaceDir", p.normalize().toString());
                }
            }

            // 保存配置
            Files.createDirectories(GLOBAL_CONFIG_DIR);
            Files.writeString(GLOBAL_CONFIG_FILE, JSON.toJSONString(config, true), StandardCharsets.UTF_8);

            // 热生效：置空旧 Agent，下次对话时按新配置重建
            ChatService.getInstance().reloadConfig();

            // 存储位置变更：立即全量迁移已有派生工作区（上一个自定义根/默认根/旧结构位置 → 新根）
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            if (derivedDirChanged) {
                int[] stats = WorkspaceDataStore.migrateAllDerivedWorkspaces(previousDerivedDir);
                resp.put("migrated", stats[0]);
                resp.put("failed", stats[1]);
                resp.put("message", stats[1] > 0
                        ? "已保存；" + stats[0] + " 个派生工作区已迁移，" + stats[1] + " 个失败（占用中，下次使用时自动重试）"
                        : stats[0] > 0 ? "已保存；" + stats[0] + " 个派生工作区已迁移" : "配置已保存，新对话将使用新配置");
            } else {
                resp.put("message", "配置已保存，新对话将使用新配置");
            }
            return ResponseEntity.ok(resp);
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
        if (workspacePath != null) {
            result.put("path", workspacePath.toAbsolutePath().toString());
            result.put("name", workspacePath.getFileName().toString());
        } else {
            result.put("path", null);
            result.put("name", null);
        }
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
        /**
     * 会话优先的工作区解析：带 sessionId 走会话权威解析（绑定 → 项目根；未绑定 → 派生沙箱），
     * 否则回退实例全局当前工作区
     */
    private Path workspaceRoot(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return ChatService.resolveAgentRoot(sessionId);
        }
        return WebApplication.getCurrentWorkspace();
    }

    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> getWorkspaceFiles(
            @RequestParam(defaultValue = "") String path,
            @RequestParam(required = false) String sessionId) {
        try {
            Path workspacePath = workspaceRoot(sessionId);
            if (workspacePath == null) {
                // 无实例且无会话：空列表，文件树自然呈空态
                return ResponseEntity.ok(List.of());
            }
            if (!Files.exists(workspacePath)) {
                // 派生沙箱惰性落盘（空会话不 require 不落盘）
                Files.createDirectories(workspacePath);
            }
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
    public ResponseEntity<Map<String, Object>> getWorkspaceFile(
            @RequestParam String path, @RequestParam(required = false) String sessionId) {
        try {
            Path workspacePath = workspaceRoot(sessionId);
            if (workspacePath == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "尚未选择工作区"));
            }
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
    public ResponseEntity<byte[]> getWorkspaceFileRaw(
            @RequestParam String path, @RequestParam(required = false) String sessionId) {
        try {
            Path workspacePath = workspaceRoot(sessionId);
            if (workspacePath == null) {
                return ResponseEntity.badRequest().build();
            }
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
     * 在系统文件管理器中定位 workspace 文件（工作区浏览器「在文件夹中打开」动作）
     */
    @PostMapping("/file/reveal")
    public ResponseEntity<Map<String, Object>> revealWorkspaceFile(
            @RequestParam String path, @RequestParam(required = false) String sessionId) {
        try {
            Path workspacePath = workspaceRoot(sessionId);
            if (workspacePath == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "尚未选择工作区"));
            }
            Path target = workspacePath.resolve(path).normalize();
            // 安全检查：确保路径在 workspace 内（与 file/raw 接口同款）
            if (!target.startsWith(workspacePath.normalize())) {
                return ResponseEntity.badRequest().body(Map.of("error", "路径越界"));
            }
            if (!Files.isRegularFile(target)) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件不存在"));
            }

            String os = System.getProperty("os.name", "").toLowerCase();
            List<String> cmd;
            if (os.contains("win")) {
                // /select, 直接跟绝对路径：资源管理器打开父目录并选中该文件
                cmd = List.of("explorer.exe", "/select," + target.toAbsolutePath());
            } else if (os.contains("mac")) {
                cmd = List.of("open", "-R", target.toAbsolutePath().toString());
            } else {
                // Linux 无统一 reveal 语义，退化为打开父目录
                cmd = List.of("xdg-open", target.toAbsolutePath().getParent().toString());
            }
            new ProcessBuilder(cmd).start();
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "打开文件夹失败: " + e.getMessage()));
        }
    }

    /**
     * 获取全局技能列表（直接读 ~/.qualia/code/skills，不依赖 agent，未配置模型时也可展示）
     */
    @GetMapping("/skills")
    public ResponseEntity<List<Map<String, Object>>> getGlobalSkills() {
        return ResponseEntity.ok(loadGlobalSkillsList());
    }

    /** SKILL.md frontmatter 块（首行 --- 至次行 ---） */
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n?");

    /**
     * 技能详情：SKILL.md frontmatter 拆为 meta 键值（version/license 等徽章展示），
     * 正文剥离后由前端渲染 markdown；附脚本（含文件名与描述）与附属文档清单
     */
    @GetMapping("/skills/{dir}/detail")
    public ResponseEntity<Map<String, Object>> getSkillDetail(@PathVariable String dir) {
        Path globalSkillsDir = GLOBAL_CONFIG_DIR.resolve("skills");
        Path skillDir = globalSkillsDir.resolve(dir);
        // 安全检查：确保读取的是技能目录内的内容（与 reveal/delete 同款）
        if (!skillDir.normalize().startsWith(globalSkillsDir.normalize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "路径越界"));
        }
        if (!Files.isDirectory(skillDir)) {
            return ResponseEntity.badRequest().body(Map.of("error", "技能不存在: " + dir));
        }
        Skill skill = new DirectorySkillLoader(globalSkillsDir).loadByName(dir);
        if (skill == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "技能不存在: " + dir));
        }

        String raw = skill.getContent() == null ? "" : skill.getContent();
        Map<String, String> meta = new LinkedHashMap<>();
        String content = raw;
        Matcher fm = FRONTMATTER_PATTERN.matcher(raw);
        if (fm.find()) {
            for (String line : fm.group(1).split("\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && !line.strip().startsWith("#")) {
                    meta.put(line.substring(0, colon).strip(),
                            line.substring(colon + 1).strip().replaceAll("^['\"]|['\"]$", ""));
                }
            }
            content = raw.substring(fm.end());
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("name", skill.getName());
        res.put("dir", dir);
        res.put("description", skill.getDescription());
        res.put("meta", meta);
        res.put("content", content.stripLeading());
        res.put("scripts", skill.getScripts().stream().map(s -> {
            Map<String, String> sm = new LinkedHashMap<>();
            sm.put("name", s.getName());
            sm.put("description", s.getDescription() == null ? "" : s.getDescription());
            sm.put("file", s.getScriptPath().getFileName().toString());
            return sm;
        }).collect(Collectors.toList()));
        res.put("references", skill.getReferenceNames());
        return ResponseEntity.ok(res);
    }

    /**
     * 搜索技能市场（代理 skills.sh 免认证 API，返回条目含本地已安装标记）
     */
    @GetMapping("/skills/market/search")
    public ResponseEntity<List<Map<String, Object>>> searchSkillMarket(@RequestParam("q") String query) {
        try {
            return ResponseEntity.ok(skillMarketService.search(query));
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            List<Map<String, Object>> body = new ArrayList<>();
            body.add(error);
            return ResponseEntity.internalServerError().body(body);
        }
    }

    /**
     * 从技能市场安装技能（GitHub 技能包 → ~/.qualia/code/skills/{skillId}/）
     */
    @PostMapping("/skills/market/install")
    public ResponseEntity<Map<String, Object>> installMarketSkill(@RequestBody Map<String, Object> body) {
        try {
            String id = String.valueOf(body.get("id"));
            String name = skillMarketService.install(id);
            reloadAgentSkills();
            return ResponseEntity.ok(Map.of("success", true, "name", name, "message", "技能 " + name + " 安装成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "安装失败: " + e.getMessage()));
        }
    }

    /** 技能落盘后热重载到运行中的 Agent（未初始化时安全跳过） */
    private void reloadAgentSkills() {
        if (ChatService.activeRoot() != null) {
            ChatService.getInstance().reloadGlobalSkills();
        }
    }

    /**
     * 在系统文件管理器中打开技能目录
     */
    @PostMapping("/skills/{name}/reveal")
    public ResponseEntity<Map<String, Object>> revealSkillFolder(@PathVariable String name) {
        try {
            Path skillDir = GLOBAL_CONFIG_DIR.resolve("skills").resolve(name);
            if (!Files.isDirectory(skillDir)) {
                return ResponseEntity.badRequest().body(Map.of("error", "技能不存在: " + name));
            }
            // 安全检查：确保打开的是技能目录内的内容（与删除接口同款）
            Path globalSkillsDir = GLOBAL_CONFIG_DIR.resolve("skills");
            if (!skillDir.normalize().startsWith(globalSkillsDir.normalize())) {
                return ResponseEntity.badRequest().body(Map.of("error", "路径越界"));
            }

            String os = System.getProperty("os.name", "").toLowerCase();
            List<String> cmd;
            if (os.contains("win")) {
                cmd = List.of("explorer.exe", skillDir.toAbsolutePath().toString());
            } else if (os.contains("mac")) {
                cmd = List.of("open", skillDir.toAbsolutePath().toString());
            } else {
                cmd = List.of("xdg-open", skillDir.toAbsolutePath().toString());
            }
            new ProcessBuilder(cmd).start();
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "打开文件夹失败: " + e.getMessage()));
        }
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

            // 清理禁用列表（disabledSkills 存的是 SKILL.md frontmatter 的显示名，非目录名，需重新解析）
            String skillName = name;
            try {
                Skill skill = new DirectorySkillLoader(GLOBAL_CONFIG_DIR.resolve("skills")).loadByName(name);
                if (skill != null) {
                    skillName = skill.getName();
                }
            } catch (Exception ignored) {
            }
            if (Files.exists(GLOBAL_CONFIG_FILE)) {
                String content = Files.readString(GLOBAL_CONFIG_FILE, StandardCharsets.UTF_8);
                JSONObject config = JSON.parseObject(content);
                JSONArray disabled = config.getJSONArray("disabledSkills");
                if (disabled != null) {
                    List<String> list = new ArrayList<>(disabled.toJavaList(String.class));
                    list.remove(skillName);
                    config.put("disabledSkills", list);
                    Files.writeString(GLOBAL_CONFIG_FILE, JSON.toJSONString(config, true), StandardCharsets.UTF_8);
                }
            }

            reloadAgentSkills();
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
            // 技能显示名（SKILL.md frontmatter）与磁盘目录名可能不同：dir 为目录名，供 reveal/delete 定位
            DirectorySkillLoader loader = new DirectorySkillLoader(globalSkillsDir);
            try (var dirs = Files.list(globalSkillsDir)) {
                for (Path dir : dirs.filter(Files::isDirectory).sorted().collect(Collectors.toList())) {
                    String dirName = dir.getFileName().toString();
                    Skill skill = loader.loadByName(dirName);
                    if (skill == null) {
                        continue;
                    }
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", skill.getName());
                    item.put("dir", dirName);
                    item.put("description", skill.getDescription());
                    item.put("source", "global");
                    item.put("enabled", !disabledSkills.contains(skill.getName()));
                    item.put("scripts", skill.getScripts().stream()
                            .map(SkillScript::getDescription)
                            .collect(Collectors.toList()));
                    item.put("references", skill.getReferenceNames());
                    result.add(item);
                }
            } catch (IOException e) {
                logger.error("读取技能目录失败: {}", globalSkillsDir, e);
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
