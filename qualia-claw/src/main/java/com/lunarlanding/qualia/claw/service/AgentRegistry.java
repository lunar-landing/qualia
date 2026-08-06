package com.lunarlanding.qualia.claw.service;

import com.lunarlanding.qualia.claw.ClawAgentDefinition;
import com.lunarlanding.qualia.claw.ClawConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能体注册表：管理所有智能体定义与对应的 ClawAgentService 实例
 *
 * 定义持久化在 ~/.qualia/claw/config.json 的 agents 数组；
 * 工作区由系统托管：创建时固定生成在 ~/.qualia/claw/workspaces/{名称}；
 * 服务实例惰性创建（首次对话时初始化 Agent），多智能体可并行流式对话。
 */
public final class AgentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistry.class);

    private static final AgentRegistry INSTANCE = new AgentRegistry();

    /** 智能体工作区根目录（产品目录下按名称一人一工位） */
    private static final Path WORKSPACES_ROOT = ClawConfig.GLOBAL_CONFIG_DIR.resolve("workspaces");

    /** 智能体定义（按创建顺序） */
    private final List<ClawAgentDefinition> definitions = new ArrayList<>();
    /** id -> 服务实例（惰性创建） */
    private final Map<String, ClawAgentService> services = new LinkedHashMap<>();

    private AgentRegistry() {
        definitions.addAll(ClawConfig.load().getAgents());
    }

    public static AgentRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 获取所有智能体定义
     */
    public synchronized List<ClawAgentDefinition> list() {
        return new ArrayList<>(definitions);
    }

    /**
     * 按 id 获取定义，不存在返回 null
     */
    public synchronized ClawAgentDefinition getDefinition(String id) {
        return definitions.stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取智能体服务实例（惰性创建）
     */
    public synchronized ClawAgentService getService(String id) {
        ClawAgentDefinition def = getDefinition(id);
        if (def == null) {
            return null;
        }
        return services.computeIfAbsent(id, k -> new ClawAgentService(def));
    }

    /**
     * 创建智能体：工作区固定生成在 ~/.qualia/claw/workspaces/{名称}（目录自动创建）
     *
     * @param skills     引用的全局技能白名单（null = 引用全部）
     * @param mcpServers 引用的全局 MCP 服务器白名单（null = 引用全部）
     * @return 新定义；校验失败抛出 IllegalArgumentException（message 可直接展示）
     */
    public synchronized ClawAgentDefinition create(String name, String emoji, String role, String model,
                                                   List<String> skills, List<String> mcpServers) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("智能体名称不能为空");
        }
        Path workspace = WORKSPACES_ROOT.resolve(sanitizeDirName(name));
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalArgumentException("创建工作区目录失败: " + e.getMessage());
        }

        ClawAgentDefinition def = new ClawAgentDefinition();
        def.setId(UUID.randomUUID().toString());
        def.setName(name.trim());
        def.setEmoji(emoji != null && !emoji.isBlank() ? emoji : "🦞");
        def.setRole(role);
        def.setWorkspacePath(workspace.toString());
        def.setModel(model);
        def.setSkills(skills);
        def.setMcpServers(mcpServers);
        def.setCreatedAt(System.currentTimeMillis());

        definitions.add(def);
        ClawConfig.saveAgents(definitions);
        logger.info("创建智能体 [{}] workspace={}", def.getName(), def.getWorkspacePath());
        return def;
    }

    /**
     * 更新智能体定义（名称/表情/角色/模型/技能与 MCP 引用白名单）；工作区创建后固定不变，改名不搬目录
     *
     * @param skills     技能白名单，null = 保持原值不变
     * @param mcpServers MCP 白名单，null = 保持原值不变
     */
    public synchronized ClawAgentDefinition update(String id, String name, String emoji, String role, String model,
                                                   List<String> skills, List<String> mcpServers) {
        ClawAgentDefinition def = getDefinition(id);
        if (def == null) {
            throw new IllegalArgumentException("智能体不存在");
        }
        ClawAgentService service = services.get(id);
        if (service != null && service.isStreaming()) {
            throw new IllegalStateException("该智能体有对话正在进行，请等待完成后再修改");
        }

        if (name != null && !name.isBlank()) {
            def.setName(name.trim());
        }
        if (emoji != null && !emoji.isBlank()) {
            def.setEmoji(emoji);
        }
        boolean roleChanged = role != null && !role.equals(def.getRole());
        if (role != null) {
            def.setRole(role);
        }

        boolean modelChanged = model != null && !model.equals(def.getModel());
        if (model != null) {
            def.setModel(model.isBlank() ? null : model);
        }

        // 引用白名单变化影响技能加载与 MCP 连接，需要惰性重建
        boolean refsChanged = false;
        if (skills != null && !skills.equals(def.getSkills())) {
            def.setSkills(skills);
            refsChanged = true;
        }
        if (mcpServers != null && !mcpServers.equals(def.getMcpServers())) {
            def.setMcpServers(mcpServers);
            refsChanged = true;
        }

        ClawConfig.saveAgents(definitions);

        // 模型/角色/引用变化影响 Agent 构建，统一惰性重建
        if (service != null && (modelChanged || roleChanged || refsChanged)) {
            service.reloadConfig();
        }
        if (service != null) {
            service.applyDefinition(def);
        }
        return def;
    }

    /**
     * 删除智能体（只移除定义与服务实例，不删除工作区文件与历史会话）
     */
    public synchronized boolean delete(String id) {
        ClawAgentDefinition def = getDefinition(id);
        if (def == null) {
            return false;
        }
        ClawAgentService service = services.get(id);
        if (service != null && service.isStreaming()) {
            throw new IllegalStateException("该智能体有对话正在进行，请等待完成后再删除");
        }
        if (service != null) {
            service.reloadConfig(); // 释放 MCP 连接
            services.remove(id);
        }
        definitions.remove(def);
        ClawConfig.saveAgents(definitions);
        logger.info("删除智能体 [{}]", def.getName());
        return true;
    }

    /**
     * 全局配置热生效：所有已创建的服务实例惰性重建
     */
    public synchronized void reloadAllConfigs() {
        for (ClawAgentService service : services.values()) {
            service.reloadConfig();
        }
    }

    /**
     * 名称转目录名：剔除文件系统非法字符与路径分隔符，避免路径穿越
     */
    private static String sanitizeDirName(String name) {
        String dir = name.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
        if (dir.isBlank()) {
            throw new IllegalArgumentException("智能体名称不能只包含特殊字符");
        }
        return dir;
    }
}
