package com.lunarlanding.qualia.code.service;

import com.lunarlanding.qualia.code.CodeAgentConfig;
import com.lunarlanding.qualia.code.CodeAgentMcpServerConfig;
import com.lunarlanding.qualia.code.CodeAgentModelConfig;
import com.lunarlanding.qualia.core.agent.HarnessAgent;
import com.lunarlanding.qualia.core.agent.spec.AgentResponse;
import com.lunarlanding.qualia.core.mcp.client.McpClient;
import com.lunarlanding.qualia.core.mcp.client.McpClientParameters;
import com.lunarlanding.qualia.core.memory.Memory;
import com.lunarlanding.qualia.core.memory.MemoryMessage;
import com.lunarlanding.qualia.core.memory.impl.JsonMemory;
import com.lunarlanding.qualia.core.model.chat.impl.ChatCompletions;
import com.lunarlanding.qualia.core.model.chat.impl.DashscopeChatModel;
import com.lunarlanding.qualia.core.model.chat.impl.DeepSeekChatModel;
import com.lunarlanding.qualia.core.model.chat.impl.MimoChatModel;
import com.lunarlanding.qualia.core.model.chat.impl.MimoTokenPlanChatModel;
import com.lunarlanding.qualia.core.model.chat.impl.OpenAIChatModel;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.skill.loader.DirectorySkillLoader;
import com.lunarlanding.qualia.core.tool.impl.harness.LocalWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import com.lunarlanding.qualia.core.model.chat.ChatModel;

import java.io.IOException;
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
     * 获取单例（同一 workspace 共享同一实例）
     */
    public static synchronized ChatService getInstance(Path workspacePath) {
        if (instance == null) {
            instance = new ChatService(workspacePath);
        }
        return instance;
    }

    /**
     * 切换到新工作区：释放旧实例的 MCP 连接后整体重建（workspacePath 为 final，只能换实例），
     * Agent/Memory 均惰性重建，会话列表自然切到新工作区的 .qualia/memory
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
        disableTools();
        loadGlobalSkills();
        connectMcpServers();
        memory = agent.getMemory();
        memoryDir = workspacePath.resolve(".qualia").resolve("memory");
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
                memoryDir = workspacePath.resolve(".qualia").resolve("memory");
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
