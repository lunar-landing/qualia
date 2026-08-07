package com.lunarlanding.qualia.claw.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lunarlanding.qualia.claw.ClawConfig;
import com.lunarlanding.qualia.claw.ClawMcpServerConfig;
import com.lunarlanding.qualia.claw.ClawModelConfig;
import com.lunarlanding.qualia.claw.service.AgentRegistry;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.skill.loader.DirectorySkillLoader;
import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.impl.file.BashTool;
import com.lunarlanding.qualia.core.tool.impl.file.DeleteTool;
import com.lunarlanding.qualia.core.tool.impl.file.GlobTool;
import com.lunarlanding.qualia.core.tool.impl.file.GrepTool;
import com.lunarlanding.qualia.core.tool.impl.file.ReadTool;
import com.lunarlanding.qualia.core.tool.impl.file.ReplaceTool;
import com.lunarlanding.qualia.core.tool.impl.file.WriteTool;
import com.lunarlanding.qualia.core.tool.impl.internet.HttpTool;
import com.lunarlanding.qualia.core.tool.impl.internet.WebFetchTool;
import com.lunarlanding.qualia.core.tool.impl.search.BaiduSearchTool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理 API（全局：模型/MCP/技能，所有智能体共享；
 * 工具启用状态已下沉到智能体级，见 AgentController 的 disabledTools 字段）
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /** 技能等资源随产品目录隔离，配置与技能读写都在 ~/.qualia/claw/ 下 */
    private static final Path GLOBAL_CONFIG_DIR = ClawConfig.GLOBAL_CONFIG_DIR;
    private static final Path GLOBAL_CONFIG_FILE = ClawConfig.GLOBAL_CONFIG_FILE;

    /**
     * 获取当前配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        try {
            ClawConfig config = ClawConfig.load();

            Map<String, Object> result = new HashMap<>();
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

            // 保存配置
            Files.createDirectories(GLOBAL_CONFIG_DIR);
            Files.writeString(GLOBAL_CONFIG_FILE, JSON.toJSONString(config, true), StandardCharsets.UTF_8);

            // 热生效：所有已创建的智能体服务置空旧 Agent，下次对话时按新配置重建
            AgentRegistry.getInstance().reloadAllConfigs();

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
     * 获取当前模型配置
     */
    @GetMapping("/model")
    public ResponseEntity<Map<String, Object>> getCurrentModel() {
        try {
            ClawConfig config = ClawConfig.load();
            ClawModelConfig model = config.getCurrentModel();

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
    public ResponseEntity<List<ClawMcpServerConfig>> getMcpServers() {
        try {
            ClawConfig config = ClawConfig.load();
            return ResponseEntity.ok(config.getMcpServers());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取可用工具列表（供智能体编辑表单勾选）
     *
     * 名称与描述直接取自工具实例（与 HarnessAgent.initializeAgent 同批构造），
     * 保证与运行期注册名严格一致；rootPath 仅执行期使用，构造传 null 安全
     */
    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, Object>>> getAvailableTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 文件操作
        tools.add(toolInfo(new ReadTool(null), "file"));
        tools.add(toolInfo(new GrepTool(null), "file"));
        tools.add(toolInfo(new GlobTool(null), "file"));
        tools.add(toolInfo(new ReplaceTool(null), "file"));
        tools.add(toolInfo(new WriteTool(null), "file"));
        tools.add(toolInfo(new DeleteTool(null), "file"));
        tools.add(toolInfo(new BashTool(null), "file"));

        // 网络操作
        tools.add(toolInfo(new WebFetchTool(), "network"));
        tools.add(toolInfo(new BaiduSearchTool(), "network"));
        tools.add(toolInfo(new HttpTool(), "network"));

        return ResponseEntity.ok(tools);
    }

    private Map<String, Object> toolInfo(FunctionTool tool, String category) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", tool.getName());
        info.put("description", tool.getDescription());
        info.put("category", category);
        return info;
    }

    /**
     * 获取全局技能列表（直接读 ~/.qualia/claw/skills，不依赖 agent，未配置模型时也可展示）
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
                // 脚本直接展示文件名，直观且不受注释提取规则影响
                item.put("scripts", skill.getScripts().stream()
                        .map(s -> s.getScriptPath().getFileName().toString())
                        .collect(Collectors.toList()));
                item.put("references", skill.getReferenceNames());
                result.add(item);
            }
        }
        return result;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
