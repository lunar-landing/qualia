package cn.lunarlanding.qualia.code.service;

import cn.lunarlanding.qualia.code.CodeAgentConfig;
import cn.lunarlanding.qualia.code.CodeAgentMcpServerConfig;
import cn.lunarlanding.qualia.code.CodeAgentModelConfig;
import cn.lunarlanding.qualia.core.agent.CallOptions;
import cn.lunarlanding.qualia.core.agent.HarnessAgent;
import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;
import cn.lunarlanding.qualia.core.mcp.client.McpClient;
import cn.lunarlanding.qualia.core.mcp.client.McpClientParameters;
import cn.lunarlanding.qualia.core.memory.Memory;
import cn.lunarlanding.qualia.core.memory.MemoryMessage;
import cn.lunarlanding.qualia.core.memory.impl.JsonMemory;
import cn.lunarlanding.qualia.core.model.chat.impl.ChatCompletions;
import cn.lunarlanding.qualia.core.model.chat.impl.DashscopeChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.DeepSeekChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.MimoChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.MimoTokenPlanChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.OpenAIChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.ZhipuChatModel;
import cn.lunarlanding.qualia.core.model.chat.impl.ZhipuCodingPlanChatModel;
import cn.lunarlanding.qualia.core.skill.Skill;
import cn.lunarlanding.qualia.core.skill.loader.DirectorySkillLoader;
import cn.lunarlanding.qualia.core.tool.impl.harness.LocalWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import cn.lunarlanding.qualia.core.model.chat.Attachment;
import cn.lunarlanding.qualia.core.model.chat.ChatModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一会话服务
 * 
 * CLI 和 Web 模式共享此服务，保证核心逻辑一致
 */
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    /** 全局技能目录（~/.qualia/code/skills），所有工作区共享 */
    private static final Path GLOBAL_SKILLS_DIR = CodeAgentConfig.GLOBAL_SKILLS_DIR;

    private static ChatService instance;

    /** 活跃会话 id（最近一次 require 激活的会话，root 解析与终端等镜像跟随它） */
    private String activeSessionId;

    /** 活跃流式对话计数（跨实例：切换 workspace 重建实例时旧流仍在进行，计数必须全局） */
    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE_STREAMS =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private final Path workspacePath;
    private HarnessAgent agent;
    private Memory memory;
    private Path memoryDir;
    private CodeAgentConfig config;
    /** 已建立的 MCP 连接（随 Agent 生命周期管理，reloadConfig 时关闭） */
    private final List<McpClient> mcpClients = new ArrayList<>();

    /** 上次加载到 Agent 的全局技能名（热重载时据此精准移除） */
    private final Set<String> loadedGlobalSkillNames = new HashSet<>();
    
    /** 缓存ChatModel实例，key为模型名称 */
    private final Map<String, ChatModel> modelCache = new HashMap<>();
    /** 当前使用的模型名称 */
    private String currentModelName;

    /** Memory 初始化独立锁，避免被 initialize() 阻塞会话创建等轻量操作 */
    private final Object memoryLock = new Object();

    private ChatService(Path workspacePath) {
        this.workspacePath = workspacePath;
    }

    /**
     * 获取单例（CLI 入口使用：启动即确定工作区路径）。
     * Web 层请使用无参 {@link #getInstance()} + {@link #require(String)}，绑定跟随会话。
     */
    public static synchronized ChatService getInstance(Path workspacePath) {
        if (instance == null) {
            instance = new ChatService(workspacePath);
        }
        return instance;
    }

    /**
     * 获取单例（Web 层只读接口使用）：无活跃实例时以 null root 起步，
     * Memory/会话列表等只读能力不依赖 root；Agent 相关操作必须先 require 解析 root。
     */
    public static synchronized ChatService getInstance() {
        if (instance == null) {
            instance = new ChatService(null);
        }
        return instance;
    }

    /**
     * 会话激活统一入口：解析会话的 Agent 根目录 → 与当前实例 root 不同则整体重建
     * （复用 {@link #switchWorkspace}，MCP / 配置重载）→ 记录活跃会话。
     *
     * <p>绑定/解绑、发消息、打开会话等所有以会话为上下文的操作前调用；
     * 同 root 重复调用零开销。</p>
     */
    public static synchronized void require(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return;
        }
        Path root = resolveAgentRoot(sessionId);
        try {
            // 派生沙箱目录惰性创建（空会话不 require 即不落盘）；绑定目录幂等
            Files.createDirectories(root);
        } catch (IOException e) {
            logger.warn("创建会话工作目录失败: {} - {}", root, e.getMessage());
        }
        if (instance == null || !root.equals(instance.workspacePath)) {
            switchWorkspace(root);
        }
        instance.activeSessionId = sessionId;
    }

    /**
     * Agent 根目录解析（binding.json 唯一权威）：绑定 → 项目根；未绑定 → 派生工作区
     * （独立全局目录 workspaces/{sid}，支持自定义位置，解析时惰性迁移存量沙箱）
     */
    public static Path resolveAgentRoot(String sessionId) {
        Path bound = WorkspaceDataStore.readBinding(sessionId);
        if (bound != null) {
            return bound;
        }
        return WorkspaceDataStore.resolveDerivedWithMigration(sessionId);
    }

    /**
     * 当前活跃实例的 Agent 根目录（无实例时返回 null；WebApplication 镜像数据源）
     */
    public static Path activeRoot() {
        return instance == null ? null : instance.workspacePath;
    }

    /**
     * 切换到新工作区：释放旧实例的 MCP 连接后整体重建（workspacePath 为 final，只能换实例），
     * Agent/Memory 均惰性重建；会话数据统一存全局桶，切换时惰性触发该工作区的存量迁移
     */
    public static synchronized void switchWorkspace(Path workspacePath) {
        if (instance != null) {
            instance.reloadConfig();
        }
        instance = new ChatService(workspacePath);
    }

    /**
     * 流式对话开始/结束记账（由 SSE 入口调用，切换工作区前用于互斥检查）
     */
    public static void beginStream() {
        ACTIVE_STREAMS.incrementAndGet();
    }

    public static void endStream() {
        ACTIVE_STREAMS.updateAndGet(n -> Math.max(0, n - 1));
    }

    /**
     * 是否有流式对话正在进行
     */
    public static boolean isStreaming() {
        return ACTIVE_STREAMS.get() > 0;
    }

    /**
     * 初始化 Agent（延迟加载，需要有效的模型配置）
     * 只执行一次，后续切换模型只替换ChatModel
     */
    public synchronized void initialize() {
        if (agent != null) {
            return;
        }
        
        config = CodeAgentConfig.load(workspacePath);
        String defaultModelName = config.getDefaultModel();
        
        // 获取或创建默认模型的ChatModel
        ChatModel chatModel = getOrCreateChatModel(defaultModelName);
        
        // 创建Agent实例（只初始化一次工具、技能、MCP连接等）
        agent = new HarnessAgent(chatModel, new LocalWorkspace(workspacePath));
        // 全局系统提示词（设置「应用」Tab 保存的 ~/.qualia/code/AGENT.md）优先于工作区文件/默认值
        Path globalAgentMd = CodeAgentConfig.GLOBAL_CONFIG_DIR.resolve("AGENT.md");
        if (Files.exists(globalAgentMd)) {
            try {
                agent.setSystemPrompt(Files.readString(globalAgentMd));
            } catch (IOException e) {
                logger.warn("读取全局 AGENT.md 失败，保持工作区/默认提示词", e);
            }
        }
        disableTools();
        loadGlobalSkills();
        connectMcpServers();
        // 记忆与 ensureMemory 同源（全局桶）：HarnessAgent 构造时自建工作区内 memory（.qualia/sessions），
        // 必须统一覆盖为全局桶实例——否则问答写入工作区目录、getSessions 读全局桶，列表查询不到数据
        ensureMemory();
        agent.setMemory(memory);
        currentModelName = defaultModelName;
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
        
        // 获取或创建ChatModel
        ChatModel chatModel = getOrCreateChatModel(modelName);
        if (chatModel == null) {
            logger.warn("模型 {} 不存在，保持当前模型 {}", modelName, currentModelName);
            return;
        }
        
        // 替换Agent中的ChatModel
        agent.setModel(chatModel);
        currentModelName = modelName;
        logger.info("已切换到模型: {}", modelName);
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
        
        // 从配置中获取模型配置
        CodeAgentModelConfig modelConfig = config.getModelByName(modelName);
        if (modelConfig == null) {
            // 如果指定模型不存在，使用默认模型
            if (modelName == null || modelName.isEmpty()) {
                modelConfig = config.getCurrentModel();
            } else {
                return null;
            }
        }
        
        // 根据provider和type判断使用哪个ChatModel实现
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
        
        // 缓存ChatModel
        modelCache.put(modelName, chatModel);
        return chatModel;
    }
    
    /**
     * 根据模型名称创建对应的ChatModel实例
     */
    private ChatCompletions createChatModelByConfig(String provider, String type, String apiKey) {
        // 根据provider和type判断使用哪个实现
        if (provider != null) {
            String lowerProvider = provider.toLowerCase();
            // 小米模型：根据type区分按量计费 vs 令牌计划
            if (lowerProvider.contains("mimo") || lowerProvider.contains("xiaomi")) {
                if ("token-plan".equals(type)) {
                    return new MimoTokenPlanChatModel(apiKey);
                }
                return new MimoChatModel(apiKey);
            }
            // 智谱模型：根据type区分按量付费 vs 令牌计划（Coding Plan）
            if (lowerProvider.contains("zhipu") || lowerProvider.contains("bigmodel") || lowerProvider.contains("glm")) {
                if ("token-plan".equals(type)) {
                    return new ZhipuCodingPlanChatModel(apiKey);
                }
                return new ZhipuChatModel(apiKey);
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
     * 根据配置禁用工具
     */
    private void disableTools() {
        Set<String> disabledTools = new HashSet<>(config.getDisabledTools());
        if (disabledTools.isEmpty()) {
            return;
        }
        // 注意：getTools() 返回防御性副本，必须走 removeTool 才能真正移除
        for (String toolName : disabledTools) {
            agent.removeTool(toolName);
        }
    }

    /**
     * 加载全局技能（~/.qualia/code/skills），与工作区技能同名时项目级优先、全局不覆盖
     */
    private void loadGlobalSkills() {
        if (!Files.exists(GLOBAL_SKILLS_DIR)) {
            return;
        }
        Set<String> projectSkillNames = agent.getSkills().stream()
                .map(Skill::getName)
                .collect(Collectors.toSet());
        Set<String> disabledSkills = new HashSet<>(config.getDisabledSkills());
        List<Skill> globalSkills = new DirectorySkillLoader(GLOBAL_SKILLS_DIR).loadAll();
        for (Skill skill : globalSkills) {
            if (projectSkillNames.contains(skill.getName())) {
                logger.info("全局技能 [{}] 与项目技能同名，已跳过", skill.getName());
                continue;
            }
            if (disabledSkills.contains(skill.getName())) {
                logger.info("全局技能 [{}] 已禁用，跳过", skill.getName());
                continue;
            }
            agent.addSkill(skill);
            loadedGlobalSkillNames.add(skill.getName());
        }
    }

    /**
     * 热重载全局技能（技能市场安装/卸载后调用）：移除上次加载的全局技能后按当前配置重新加载，
     * 无需重建 Agent（模型/MCP/会话上下文均不受影响）；Agent 未初始化时无需处理（initialize 时自然加载）
     */
    public synchronized void reloadGlobalSkills() {
        if (agent == null) {
            return;
        }
        for (String name : loadedGlobalSkillNames) {
            agent.removeSkill(name);
        }
        loadedGlobalSkillNames.clear();
        loadGlobalSkills();
        logger.info("全局技能已热重载，当前 {} 个", loadedGlobalSkillNames.size());
    }

    /**
     * 运行时更新系统提示词（AGENT.md 在线编辑保存后即时生效，无需重建 Agent）。
     * Agent 未初始化时跳过——下次 initialize 时会自动读取 AGENT.md 文件。
     */
    public void applySystemPrompt(String prompt) {
        if (agent != null) {
            agent.setSystemPrompt(prompt);
            logger.info("系统提示词已热更新（{} 字符）", prompt.length());
        }
    }

    /**
     * 按配置连接 MCP 服务器并注册工具（单个失败不影响其他服务器和对话可用性）
     */
    private void connectMcpServers() {
        for (CodeAgentMcpServerConfig server : config.getMcpServers()) {
            if (!server.isEnabled()) {
                logger.info("MCP 服务器 [{}] 已禁用，跳过", server.getName());
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
    private static McpClientParameters toMcpParameters(CodeAgentMcpServerConfig server) {
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
     * 初始化 Memory（只依赖工作区目录，不需要模型配置）
     * 
     * 会话列表、历史、统计等只读能力在未配置模型时也可用
     */
    private void ensureMemory() {
        synchronized (memoryLock) {
            if (memory == null) {
                // 存量迁移（惰性幂等）：把当前工作区 .qualia/sessions 搬入全局桶，项目目录零会话数据
                WorkspaceDataStore.migrateProjectSessions(workspacePath);
                memoryDir = WorkspaceDataStore.globalSessionsDir();
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
     * 创建新会话：一律落在默认工作区（不写 binding.json = 派生沙箱语义），
     * 「新建即绑项目」由绑定接口显式完成，绑定入口唯一。
     */
    public SessionInfo createSession(String title) {
        ensureMemory();
        String sessionId = UUID.randomUUID().toString();
        String sessionTitle = title != null ? title : "新会话";
        return new SessionInfo(sessionId, sessionTitle, System.currentTimeMillis());
    }

    /**
     * 获取全部会话（跨项目历史库）：归属以 binding.json 回填到 workspace 字段
     * （null = 默认工作区），分组展示交给前端。
     */
    public List<SessionInfo> getSessions() {
        ensureMemory();

        List<SessionInfo> sessionList = new ArrayList<>();
        if (memoryDir != null && Files.exists(memoryDir)) {
            try (var paths = Files.list(memoryDir)) {
                // 新布局：memoryDir/{sessionId}/session.json；全量返回，归属见 info.workspace
                paths.filter(Files::isDirectory)
                     .forEach(dir -> {
                         SessionInfo info = buildSessionInfo(dir);
                         if (info != null) {
                             sessionList.add(info);
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
     * 获取单个会话信息（含 workspace 归属；会话为空或不存在返回 null）
     */
    public SessionInfo getSessionInfo(String sessionId) {
        ensureMemory();
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return buildSessionInfo(sessionDir(sessionId));
    }

    /** 单会话信息构建：标题（自定义优先）+ 时间 + workspace 归属；空会话返回 null */
    private SessionInfo buildSessionInfo(Path dir) {
        Path file = dir.resolve("session.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String sessionId = dir.getFileName().toString();
        List<MemoryMessage> messages = memory.getSessionHistory(sessionId);
        if (messages.isEmpty()) {
            return null;
        }
        String title = readCustomTitle(dir);
        if (title == null) {
            title = messages.get(0).getContent();
            if (title.length() > 20) {
                title = title.substring(0, 20) + "...";
            }
        }
        long lastModified = 0;
        try {
            lastModified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {}
        SessionInfo info = new SessionInfo(sessionId, title, lastModified, messages);
        Path bound = WorkspaceDataStore.readBinding(sessionId);
        info.workspace = bound == null ? null : bound.toString();
        info.locked = !messages.isEmpty();
        return info;
    }

    /**
     * 获取会话消息历史
     */
    public List<MemoryMessage> getSessionHistory(String sessionId) {
        ensureMemory();
        return memory.getSessionHistory(sessionId);
    }

    /**
     * 会话上下文视图数据（输入框信号条）：usedTokens = 历史消息字符数×2（对齐 ContextManager
     * 估算口径，供点亮比例）；turnCount = assistant 消息数（对话轮数）；
     * sessionTotalTokens = 全会话 assistant 消息 totalTokens 累加（真实消耗）；
     * contextWindow = 当前模型配置的窗口上限（未配置不返回，前端按默认值估算）
     */
    public Map<String, Object> getContextInfo(String sessionId) {
        ensureMemory();
        Map<String, Object> result = new HashMap<>();
        List<MemoryMessage> history = memory.getSessionHistory(sessionId);
        long chars = 0;
        int turns = 0;
        long totalTokens = 0;
        for (MemoryMessage m : history) {
            if (m.getContent() != null) {
                chars += m.getContent().length();
            }
            if (m.getRole() == MemoryMessage.Role.ASSISTANT) {
                turns++;
                if (m.getTotalTokens() != null) {
                    totalTokens += m.getTotalTokens();
                }
            }
        }
        result.put("usedTokens", chars * 2);
        result.put("turnCount", turns);
        result.put("sessionTotalTokens", totalTokens);
        CodeAgentModelConfig modelConfig = currentModelName != null ? config.getModelByName(currentModelName) : null;
        if (modelConfig != null && modelConfig.getContextWindow() != null) {
            result.put("contextWindow", modelConfig.getContextWindow());
        }
        return result;
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
                // 新布局：memoryDir/{sessionId}/session.json
                paths.filter(Files::isDirectory)
                     .forEach(dir -> {
                         Path file = dir.resolve("session.json");
                         if (!Files.isRegularFile(file)) {
                             return;
                         }
                         String sessionId = dir.getFileName().toString();
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
        // 先删绑定元数据，保证 clearSession 后会话域目录可正常清空
        WorkspaceDataStore.deleteBinding(sessionId);
        // 联动清理派生工作区（默认/自定义根 + 旧版会话域位置，沙箱生成文件一并删除）
        WorkspaceDataStore.deleteDerivedWorkspace(sessionId);
        memory.clearSession(sessionId);
        // 联动清理会话附件仓库
        AttachmentService.getInstance().clearSession(sessionId);
        // 清理自定义标题残留并移除空会话目录
        try {
            Path dir = sessionDir(sessionId);
            Files.deleteIfExists(dir.resolve("title.txt"));
            if (Files.isDirectory(dir)) {
                try (var entries = Files.list(dir)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(dir);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return true;
    }

    /**
     * 重命名会话：自定义标题存会话目录 title.txt（列表展示优先于首条消息自动标题）；
     * 空标题视为恢复自动标题
     */
    public void renameSession(String sessionId, String title) {
        ensureMemory();
        String cleaned = title == null ? "" : title.trim();
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50);
        }
        Path file = sessionDir(sessionId).resolve("title.txt");
        try {
            if (cleaned.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, cleaned, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.error("重命名会话失败: {}", sessionId, e);
            throw new RuntimeException("重命名失败: " + e.getMessage());
        }
    }

    /** 会话域目录：全局桶 sessions/{sessionId}（路径唯一权威见 WorkspaceDataStore） */
    private Path sessionDir(String sessionId) {
        return WorkspaceDataStore.sessionDir(sessionId);
    }

    /** 读会话自定义标题（title.txt 不存在或为空返回 null，回退自动标题） */
    private String readCustomTitle(Path sessionDir) {
        Path file = sessionDir.resolve("title.txt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String title = Files.readString(file, StandardCharsets.UTF_8).trim();
            return title.isEmpty() ? null : title;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 发送消息并获取流式响应（CLI使用）
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
     * 发送带附件的消息并获取Flux流式响应（图片直传视觉模型，文档解析注入）
     */
    public Flux<AgentResponse> sendMessageFlux(String sessionId, String message, List<Attachment> attachments) {
        initialize();
        return agent.callStream(sessionId, message, attachments);
    }

    /**
     * 发送带附件与会话选项的消息并获取Flux流式响应
     *
     * @param options 会话选项（只读问答等），null = 全部默认值
     */
    public Flux<AgentResponse> sendMessageFlux(String sessionId, String message, List<Attachment> attachments, CallOptions options) {
        initialize();
        return agent.callStream(sessionId, message, attachments, options);
    }

    /**
     * 获取 Agent 实例（CLI 模式用于打印工具/技能信息）
     */
    public HarnessAgent getAgent() {
        initialize();
        return agent;
    }

    /**
     * 获取配置
     */
    public CodeAgentConfig getConfig() {
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
        /** 会话绑定的项目路径；null = 默认工作区（派生沙箱态） */
        public String workspace;
        /** 工作区锁定：已产生对话（无论是否绑定）——后端拒绝任何绑定变更（前端只读展示） */
        public boolean locked;
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
