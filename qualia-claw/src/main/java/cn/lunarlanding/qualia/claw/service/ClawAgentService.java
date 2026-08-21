package cn.lunarlanding.qualia.claw.service;

import cn.lunarlanding.qualia.claw.ClawAgentDefinition;
import cn.lunarlanding.qualia.claw.ClawConfig;
import cn.lunarlanding.qualia.claw.ClawMcpServerConfig;
import cn.lunarlanding.qualia.claw.ClawModelConfig;
import cn.lunarlanding.qualia.claw.ClawWorkspace;
import cn.lunarlanding.qualia.core.agent.HarnessAgent;
import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;
import cn.lunarlanding.qualia.core.mcp.client.McpClient;
import cn.lunarlanding.qualia.core.mcp.client.McpClientParameters;
import cn.lunarlanding.qualia.core.memory.Memory;
import cn.lunarlanding.qualia.core.memory.MemoryMessage;
import cn.lunarlanding.qualia.core.memory.impl.JsonMemory;
import cn.lunarlanding.qualia.core.model.chat.ChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.ChatCompletions;
import cn.lunarlanding.qualia.core.model.chat.impl.DashscopeChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.DeepSeekChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.MimoChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.MimoTokenPlanChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.OpenAIChatModel;
import cn.lunarlanding.qualia.core.skill.Skill;
import cn.lunarlanding.qualia.core.skill.loader.DirectorySkillLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 单智能体服务：一个 ClawAgentService 对应一个智能体工位
 * （独立工作区 + 独立 HarnessAgent + 独立 Memory/会话）
 *
 * 由 AgentRegistry 按智能体定义惰性创建与管理生命周期
 */
public class ClawAgentService {

    private static final Logger logger = LoggerFactory.getLogger(ClawAgentService.class);

    /** 全局技能目录（~/.qualia/claw/skills），本产品所有智能体共享 */
    private static final Path GLOBAL_SKILLS_DIR = ClawConfig.GLOBAL_SKILLS_DIR;

    /**
     * 旧版工具名归一：早期前端展示清单与真实注册名不一致（replace/delete_file/http），
     * 存量智能体配置中的旧名称按此映射继续生效
     */
    private static final Map<String, String> LEGACY_TOOL_NAMES = Map.of(
            "replace", "edit",
            "delete_file", "delete",
            "http", "http_request");

    /** 智能体定义（可被 update 替换，替换后需 reloadConfig） */
    private ClawAgentDefinition definition;

    private HarnessAgent agent;
    private Memory memory;
    private Path memoryDir;
    private ClawConfig config;
    /** 已建立的 MCP 连接（随 Agent 生命周期管理，reloadConfig 时关闭） */
    private final List<McpClient> mcpClients = new ArrayList<>();

    /** 缓存ChatModel实例，key为模型名称 */
    private final Map<String, ChatModel> modelCache = new HashMap<>();
    /** 当前使用的模型名称 */
    private String currentModelName;

    /** 本智能体活跃流式对话计数（实例级：各工位互不阻塞） */
    private final AtomicInteger activeStreams = new AtomicInteger(0);

    /** Memory 初始化独立锁，避免被 initialize() 阻塞会话创建等轻量操作 */
    private final Object memoryLock = new Object();

    public ClawAgentService(ClawAgentDefinition definition) {
        this.definition = definition;
    }

    public ClawAgentDefinition getDefinition() {
        return definition;
    }

    /**
     * 替换智能体定义（编辑智能体后由 Registry 调用，配合 reloadConfig 生效）
     */
    public synchronized void applyDefinition(ClawAgentDefinition definition) {
        this.definition = definition;
    }

    /**
     * 流式对话开始/结束记账
     */
    public void beginStream() {
        activeStreams.incrementAndGet();
    }

    public void endStream() {
        activeStreams.updateAndGet(n -> Math.max(0, n - 1));
    }

    /**
     * 是否有流式对话正在进行
     */
    public boolean isStreaming() {
        return activeStreams.get() > 0;
    }

    private Path workspacePath() {
        return Path.of(definition.getWorkspacePath());
    }

    /**
     * 初始化 Agent（延迟加载，需要有效的模型配置）
     * 只执行一次，后续切换模型只替换ChatModel
     */
    public synchronized void initialize() {
        if (agent != null) {
            return;
        }

        try {
            Files.createDirectories(workspacePath());
        } catch (IOException e) {
            logger.error("工作区目录创建失败: {}", e.getMessage());
        }
        config = ClawConfig.load();

        // 智能体可覆盖模型，缺省用全局默认模型
        String modelName = definition.getModel() != null && !definition.getModel().isBlank()
                ? definition.getModel()
                : config.getDefaultModel();

        ChatModel chatModel = getOrCreateChatModel(modelName);

        // 先迁移旧版记忆再建 Agent（HarnessAgent 会按工作区的 memory 目录创建 JsonMemory）
        memoryDir = memoryDir();
        agent = new HarnessAgent(chatModel, new ClawWorkspace(workspacePath(), definition.getId()));
        disableTools();
        loadGlobalSkills();
        connectMcpServers();
        applyRolePrompt();
        memory = agent.getMemory();
        currentModelName = modelName;
    }

    /**
     * 智能体记忆目录（~/.qualia/claw/agents/{id}/memory，与工作区隔离）
     * 首次访问时迁移旧版工作区内的会话记忆
     */
    private Path memoryDir() {
        Path dir = ClawWorkspace.memoryDirFor(definition.getId());
        migrateLegacyMemory(dir);
        return dir;
    }

    /**
     * 旧版迁移：工作区 .qualia/memory 内的会话记忆搬迁到智能体目录（幂等，搬完即删旧目录），
     * 并顺带清理旧版预建的 .qualia/skills 与空的 .qualia（工作区级配置已禁用）
     */
    private void migrateLegacyMemory(Path targetDir) {
        Path legacyDir = workspacePath().resolve(".qualia").resolve("memory");
        if (!Files.isDirectory(legacyDir)) {
            cleanupLegacyQualia();
            return;
        }
        try {
            Files.createDirectories(targetDir);
            try (var files = Files.list(legacyDir)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.deleteIfExists(legacyDir);
            logger.info("已迁移智能体 [{}] 的会话记忆到 {}", definition.getName(), targetDir);
        } catch (IOException e) {
            logger.warn("迁移旧版会话记忆失败: {}", e.getMessage());
        }
        cleanupLegacyQualia();
    }

    /**
     * 清理旧版预建的工作区 .qualia 残留：仅删除空的 skills 子目录与空的 .qualia，
     * 若用户自行放入了内容则原样保留
     */
    private void cleanupLegacyQualia() {
        try {
            Path qualiaDir = workspacePath().resolve(".qualia");
            Path legacySkills = qualiaDir.resolve("skills");
            if (Files.isDirectory(legacySkills) && isEmpty(legacySkills)) {
                Files.delete(legacySkills);
            }
            if (Files.isDirectory(qualiaDir) && isEmpty(qualiaDir)) {
                Files.delete(qualiaDir);
            }
        } catch (IOException e) {
            // 清理失败不影响功能，仅残留一个空目录
        }
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findFirst().isEmpty();
        }
    }

    /**
     * 叠加职能角色设定到 system prompt（claw 已禁用工作区 AGENT.md，基础提示词为框架默认值，角色描述追加其后）
     */
    private void applyRolePrompt() {
        String role = definition.getRole();
        if (role == null || role.isBlank()) {
            return;
        }
        String roleBlock = "# 职能角色\n你是「" + definition.getName() + "」，" + role.trim()
                + "\n请始终以该职能角色的专业视角处理任务。";
        agent.setSystemPrompt(agent.getSystemPrompt() + "\n\n" + roleBlock);
    }

    /**
     * 切换模型（只替换ChatModel，不重建Agent）
     *
     * @param modelName 目标模型名称
     */
    public synchronized void switchModel(String modelName) {
        if (agent == null) {
            initialize();
        }

        // 如果已经是当前模型，直接返回
        if (modelName != null && modelName.equals(currentModelName)) {
            return;
        }

        ChatModel chatModel = getOrCreateChatModel(modelName);
        if (chatModel == null) {
            logger.warn("模型 {} 不存在，保持当前模型 {}", modelName, currentModelName);
            return;
        }

        agent.setModel(chatModel);
        currentModelName = modelName;
        logger.info("智能体 [{}] 已切换到模型: {}", definition.getName(), modelName);
    }

    /**
     * 获取或创建ChatModel（缓存机制）
     *
     * @param modelName 模型名称
     * @return ChatModel实例，如果模型不存在返回null
     */
    private ChatModel getOrCreateChatModel(String modelName) {
        if (modelCache.containsKey(modelName)) {
            return modelCache.get(modelName);
        }

        ClawModelConfig modelConfig = config.getModelByName(modelName);
        if (modelConfig == null) {
            // 如果指定模型不存在，使用默认模型
            if (modelName == null || modelName.isEmpty()) {
                modelConfig = config.getCurrentModel();
            } else {
                return null;
            }
        }

        String apiKey = modelConfig.getApiKey();
        String provider = modelConfig.getProvider();
        String type = modelConfig.getType();
        ChatCompletions chatModel = createChatModelByConfig(provider, type, apiKey);

        if (modelConfig.getModel() != null && !modelConfig.getModel().isBlank()) {
            chatModel.modelName(modelConfig.getModel());
        }
        String baseUrl = modelConfig.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            chatModel.baseUrl(toChatCompletionsUrl(baseUrl));
        }

        modelCache.put(modelName, chatModel);
        return chatModel;
    }

    /**
     * 根据模型名称创建对应的ChatModel实例
     */
    private ChatCompletions createChatModelByConfig(String provider, String type, String apiKey) {
        if (provider != null) {
            String lowerProvider = provider.toLowerCase();
            // 小米模型：根据type区分按量计费 vs 令牌计划
            if (lowerProvider.contains("mimo") || lowerProvider.contains("xiaomi")) {
                if ("token-plan".equals(type)) {
                    return new MimoTokenPlanChatModel(apiKey);
                }
                return new MimoChatModel(apiKey);
            }
            if (lowerProvider.contains("qwen") || lowerProvider.contains("dashscope") || lowerProvider.contains("tongyi")) {
                return new DashscopeChatModel(apiKey);
            }
            if (lowerProvider.contains("deepseek")) {
                return new DeepSeekChatModel(apiKey);
            }
        }
        // 默认使用通用OpenAI兼容实现
        return new OpenAIChatModel(apiKey);
    }

    /**
     * 按智能体级禁用名单移除工具（名单为智能体私有配置，null/空 = 全部可用）
     */
    private void disableTools() {
        List<String> refs = definition.getDisabledTools();
        if (refs == null || refs.isEmpty()) {
            return;
        }
        // 注意：getTools() 返回防御性副本，必须走 removeTool 才能真正移除
        for (String toolName : refs) {
            agent.removeTool(LEGACY_TOOL_NAMES.getOrDefault(toolName, toolName));
        }
    }

    /**
     * 加载全局技能（~/.qualia/claw/skills，claw 无工作区级技能，全局目录是唯一来源）
     */
    private void loadGlobalSkills() {
        if (!Files.exists(GLOBAL_SKILLS_DIR)) {
            return;
        }
        Set<String> disabledSkills = new HashSet<>(config.getDisabledSkills());
        // 智能体级白名单：null = 引用全部（存量智能体），否则仅加载名单内的技能
        List<String> refs = definition.getSkills();
        Set<String> allowedSkills = refs != null ? new HashSet<>(refs) : null;
        List<Skill> globalSkills = new DirectorySkillLoader(GLOBAL_SKILLS_DIR).loadAll();
        for (Skill skill : globalSkills) {
            if (disabledSkills.contains(skill.getName())) {
                logger.info("全局技能 [{}] 已禁用，跳过", skill.getName());
                continue;
            }
            if (allowedSkills != null && !allowedSkills.contains(skill.getName())) {
                logger.info("全局技能 [{}] 未被智能体 [{}] 引用，跳过", skill.getName(), definition.getName());
                continue;
            }
            agent.addSkill(skill);
        }
    }

    /**
     * 按配置连接 MCP 服务器并注册工具（单个失败不影响其他服务器和对话可用性）
     */
    private void connectMcpServers() {
        // 智能体级白名单：null = 引用全部（存量智能体），否则仅连接名单内的服务器
        List<String> refs = definition.getMcpServers();
        Set<String> allowedMcp = refs != null ? new HashSet<>(refs) : null;
        for (ClawMcpServerConfig server : config.getMcpServers()) {
            if (!server.isEnabled()) {
                logger.info("MCP 服务器 [{}] 已禁用，跳过", server.getName());
                continue;
            }
            if (allowedMcp != null && !allowedMcp.contains(server.getName())) {
                logger.info("MCP 服务器 [{}] 未被智能体 [{}] 引用，跳过", server.getName(), definition.getName());
                continue;
            }
            try {
                McpClientParameters params = toMcpParameters(server);
                mcpClients.add(agent.addMcpClient(params));
                logger.info("MCP 服务器 [{}] 已接入", server.getName());
            } catch (Exception e) {
                logger.warn("MCP 服务器 [{}] 接入失败，已跳过: {}", server.getName(), e.getMessage());
            }
        }
    }

    /**
     * 配置 transport 字段（streamable-http / http-sse）转换为 MCP 连接参数
     */
    private static McpClientParameters toMcpParameters(ClawMcpServerConfig server) {
        String transport = server.getTransport() != null ? server.getTransport() : "streamable-http";
        McpClientParameters params = switch (transport) {
            case "streamable-http" -> McpClientParameters.streamableHttp(server.getUrl());
            case "http-sse" -> McpClientParameters.httpSse(server.getUrl());
            default -> throw new IllegalArgumentException("不支持的 transport: " + transport);
        };
        return params.withName(server.getName()).withHeaders(server.getHeaders());
    }

    /**
     * 配置中的 baseUrl 为服务基础地址，补全为 OpenAI 兼容的 chat/completions 端点
     */
    private static String toChatCompletionsUrl(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return url.endsWith("/chat/completions") ? url : url + "/chat/completions";
    }

    /**
     * 初始化 Memory（只依赖智能体记忆目录，不需要模型配置）
     *
     * 会话列表、历史、统计等只读能力在未配置模型时也可用
     */
    private void ensureMemory() {
        synchronized (memoryLock) {
            if (memory == null) {
                memoryDir = memoryDir();
                memory = new JsonMemory(memoryDir);
            }
        }
    }

    /**
     * 重新加载配置（惰性重建：下次需要 Agent 时按新配置初始化，
     * 避免新配置无效时导致保存接口报错）
     */
    public synchronized void reloadConfig() {
        // 关闭MCP连接
        for (McpClient client : mcpClients) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("关闭 MCP 连接失败: {}", e.getMessage());
            }
        }
        mcpClients.clear();

        // 清空Agent和模型缓存
        agent = null;
        config = null;
        modelCache.clear();
        currentModelName = null;
    }

    /**
     * 创建新会话
     */
    public SessionInfo createSession(String title) {
        ensureMemory();
        String sessionId = UUID.randomUUID().toString();
        String sessionTitle = title != null ? title : "新会话";
        return new SessionInfo(sessionId, sessionTitle, System.currentTimeMillis());
    }

    /**
     * 获取所有会话
     */
    public List<SessionInfo> getSessions() {
        ensureMemory();

        List<SessionInfo> sessionList = new ArrayList<>();
        if (memoryDir != null && Files.exists(memoryDir)) {
            try (var paths = Files.list(memoryDir)) {
                paths.filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith("_summaries.json"))
                     .forEach(file -> {
                         String fileName = file.getFileName().toString();
                         String sessionId = fileName.replace(".json", "");
                         List<MemoryMessage> messages = memory.getSessionHistory(sessionId);
                         if (!messages.isEmpty()) {
                             String title = messages.get(0).getContent();
                             if (title.length() > 20) {
                                 title = title.substring(0, 20) + "...";
                             }
                             long lastModified = 0;
                             try {
                                 lastModified = Files.getLastModifiedTime(file).toMillis();
                             } catch (IOException ignored) {}
                             sessionList.add(new SessionInfo(sessionId, title, lastModified, messages));
                         }
                     });
            } catch (IOException e) {
                // ignore
            }
        }

        sessionList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return sessionList;
    }

    /**
     * 获取会话消息历史
     */
    public List<MemoryMessage> getSessionHistory(String sessionId) {
        ensureMemory();
        return memory.getSessionHistory(sessionId);
    }

    /**
     * 近 N 日每日 token 用量统计（按自然日聚合，无数据的日期补 0）
     */
    public List<Map<String, Object>> getDailyTokenStats(int days) {
        ensureMemory();

        Map<String, Long> daily = new TreeMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            daily.put(today.minusDays(i).toString(), 0L);
        }

        if (memoryDir != null && Files.exists(memoryDir)) {
            try (var paths = Files.list(memoryDir)) {
                paths.filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith("_summaries.json"))
                     .forEach(file -> {
                         String sessionId = file.getFileName().toString().replace(".json", "");
                         for (MemoryMessage msg : memory.getSessionHistory(sessionId)) {
                             if (msg.getTotalTokens() == null) {
                                 continue;
                             }
                             String day = java.time.Instant.ofEpochMilli(msg.getCreatedAt())
                                     .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
                             daily.computeIfPresent(day, (k, v) -> v + msg.getTotalTokens());
                         }
                     });
            } catch (IOException e) {
                // ignore
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        daily.forEach((date, tokens) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", date);
            item.put("tokens", tokens);
            result.add(item);
        });
        return result;
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String sessionId) {
        ensureMemory();
        memory.clearSession(sessionId);
        return true;
    }

    /**
     * 发送消息并获取流式响应
     */
    public Iterator<AgentResponse> sendMessage(String sessionId, String message) {
        initialize();
        return agent.callStream(sessionId, message).toIterable().iterator();
    }

    /**
     * 发送消息并获取Flux流式响应（Web使用）
     */
    public Flux<AgentResponse> sendMessageFlux(String sessionId, String message) {
        initialize();
        return agent.callStream(sessionId, message);
    }

    /**
     * 获取 Agent 实例
     */
    public HarnessAgent getAgent() {
        initialize();
        return agent;
    }

    /**
     * 获取配置
     */
    public ClawConfig getConfig() {
        initialize();
        return config;
    }

    /**
     * 会话信息
     */
    public static class SessionInfo {
        public String id;
        public String title;
        public long createdAt;
        public List<Map<String, String>> messages = new ArrayList<>();

        public SessionInfo(String id, String title, long createdAt) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
        }

        public SessionInfo(String id, String title, long createdAt, List<MemoryMessage> memoryMessages) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.messages = memoryMessages.stream()
                    .map(msg -> Map.of(
                            "role", msg.getRole().name().toLowerCase(),
                            "content", msg.getContent()
                    ))
                    .collect(Collectors.toList());
        }
    }
}
