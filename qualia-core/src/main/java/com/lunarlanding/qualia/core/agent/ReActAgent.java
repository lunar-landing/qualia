package com.lunarlanding.qualia.core.agent;

import com.lunarlanding.qualia.core.agent.spec.AgentResponse;
import com.lunarlanding.qualia.core.agent.spec.AgentStep;
import com.lunarlanding.qualia.core.knowledge.KnowledgeSourceUtil;
import com.lunarlanding.qualia.core.memory.MemoryMessage;
import com.lunarlanding.qualia.core.model.chat.ChatChoice;
import com.lunarlanding.qualia.core.model.chat.ChatModel;
import com.lunarlanding.qualia.core.model.chat.ChatResponse;
import com.lunarlanding.qualia.core.model.chat.ChatMessage;
import com.lunarlanding.qualia.core.model.chat.ChatUsage;
import com.lunarlanding.qualia.core.model.chat.conf.ResponseFormatType;
import com.lunarlanding.qualia.core.mcp.client.McpClient;
import com.lunarlanding.qualia.core.mcp.client.McpClientParameters;
import io.modelcontextprotocol.spec.McpSchema;
import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.FunctionToolScanner;
import com.lunarlanding.qualia.core.tool.McpToolAdapter;
import com.lunarlanding.qualia.core.tool.AgentToolAdapter;
import com.lunarlanding.qualia.core.knowledge.KnowledgeBase;
import com.lunarlanding.qualia.core.model.rerank.RerankModel;
import com.lunarlanding.qualia.core.tool.KnowbaseToolAdapter;
import com.lunarlanding.qualia.core.memory.Memory;
import com.lunarlanding.qualia.core.memory.impl.MemMemory;
import com.lunarlanding.qualia.core.agent.context.ContextManager;
import com.lunarlanding.qualia.core.constant.Constant;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.tool.ToolCall;
import com.lunarlanding.qualia.core.tool.impl.skill.SkillSelector;
import com.lunarlanding.qualia.core.tool.impl.skill.SkillLoader;
import com.lunarlanding.qualia.core.tool.impl.skill.SkillReferenceReader;
import com.lunarlanding.qualia.core.tool.impl.skill.SkillScriptRunner;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReActAgent 是一个基于反应式思维链的智能体实现
 * 使用单个 ChatModel，通过调用时指定 ResponseFormatType 区分输出格式
 * 它能够根据输入执行工具并做出决策
 */
public class ReActAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(ReActAgent.class);
    
    private String name;
    private String description;
    protected List<FunctionTool> tools = new ArrayList<>();
    protected List<Skill> skills = new ArrayList<>();
    protected int maxIterations = 20;
    protected String systemPrompt = "你是一个智能助手。";
    protected boolean suggestionsEnabled = true;
    protected String suggestionsPrompt;
    private String detectedLanguage;
    private String preferredLanguage;
    protected ContextManager contextManager;
    protected ChatModel model;

    public ReActAgent(ChatModel model) {
        this(model, new MemMemory()); // 默认使用内存记忆，保证会话连续性
    }

    public ReActAgent(ChatModel model, Memory memory) {
        this.model = model;
        this.contextManager = new ContextManager(memory, model);

        addTool(new SkillSelector(this));
        addTool(new SkillScriptRunner(this));
        addTool(new SkillReferenceReader(this));
        addTool(new SkillLoader(this));
    }

    /**
     * 添加技能
     */
    public void addSkill(Skill skill) {
        this.skills.add(skill);
    }

    /**
     * Sets an optional BCP 47 language preference for reasoning, retrieval, and the final answer.
     */
    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = normalizePreferredLanguage(preferredLanguage);
    }

    /**
     * 获取所有技能
     */
    public List<Skill> getSkills() {
        return skills;
    }

    /**
     * 动态替换ChatModel（用于模型切换）
     * 
     * @param model 新的ChatModel实例
     */
    public void setModel(ChatModel model) {
        this.model = model;
        if (this.contextManager != null) {
            this.contextManager.setModel(model);
        }
    }

    /**
     * 获取当前ChatModel
     * 
     * @return 当前ChatModel实例
     */
    public ChatModel getModel() {
        return model;
    }

    /**
     * 根据名称查找技能
     */
    public Skill findSkill(String name) {
        return skills.stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /**
     * 获取当前可用工具
     * 技能脚本通过 SkillScriptRunner 统一调用，不单独暴露
     * 技能列表为空时，过滤掉 skill 相关工具，避免模型猜测加载不存在的技能
     */
    private List<FunctionTool> getAvailableTools() {
        List<FunctionTool> availableTools = new ArrayList<>(tools);
        if (skills.isEmpty()) {
            availableTools.removeIf(tool ->
                tool instanceof SkillLoader ||
                tool instanceof SkillScriptRunner ||
                tool instanceof SkillReferenceReader
            );
        }
        return availableTools;
    }

    /**
     * 添加工具到智能体
     */
    public void addTool(FunctionTool tool) {
        this.tools.add(tool);
    }

    /**
     * 添加子智能体作为工具
     *
     * <p>将另一个 Agent 包装为 FunctionTool 注册到当前智能体，
     * 实现多智能体协作。子智能体拥有独立的资源（记忆和工具）。</p>
     *
     * @param agent 子智能体实例
     */
    public void addSubAgent(Agent agent) {
        AgentToolAdapter adapter = new AgentToolAdapter(
            agent, agent.name(), agent.description()
        );
        addTool(adapter);
        logger.info("子智能体 [{}] 已注册为工具", agent.name());
    }

    /**
     * 扫描对象中的注解方法并注册为工具
     *
     * <p>扫描 toolProvider 中标注了 {@code @AsFunctionTool} 的方法，
     * 将其转换为 FunctionTool 并注册。</p>
     *
     * @param toolProvider 包含 @AsFunctionTool 方法的对象
     * @see com.lunarlanding.qualia.core.tool.annotation.AsFunctionTool
     */
    public void addTools(Object toolProvider) {
        FunctionToolScanner.register(toolProvider, this.tools);
        logger.info("从 {} 注册了注解工具", toolProvider.getClass().getSimpleName());
    }

    /**
     * 获取当前注册的工具列表（用于测试验证）
     */
    public List<FunctionTool> getTools() {
        return new ArrayList<>(tools);
    }

    /**
     * 接入 MCP Server，自动发现并注册远端工具。
     * 调用方负责管理返回的 McpClient 生命周期（推荐 try-with-resources）。
     *
     * @param params MCP 服务器连接参数
     * @return McpClient 已建立连接的 MCP 客户端（可用于关闭）
     */
    public McpClient addMcpClient(McpClientParameters params) {
        McpClient client = new McpClient(params);
        client.connect();
        registerMcpTools(client);
        return client;
    }

    /**
     * 接入已建立的 MCP 连接（用于测试注入）
     *
     * @param client 已建立连接的 MCP 客户端
     */
    public void addMcpClient(McpClient client) {
        registerMcpTools(client);
    }

    /**
     * 将 MCP 客户端发现的远程工具注册为 FunctionTool
     */
    private void registerMcpTools(McpClient client) {
        for (McpSchema.Tool mcpTool : client.getTools()) {
            McpToolAdapter adapter = new McpToolAdapter(mcpTool, client);
            addTool(adapter);
        }
    }

    /**
     * 添加知识库，内部自动创建检索工具并注册。
     *
     * <p>与 {@link #addMcpClient(McpClientParameters)} 设计对齐：
     * 调用方只需传入 {@link KnowledgeBase} 配置对象，内部创建
     * {@link KnowbaseToolAdapter} 并注册为工具。</p>
     *
     * @param knowledgeBase 知识库配置
     * @param rerankModel   用于结果重排序的 RerankModel（可为 null）
     */
    public void addKnowbase(KnowledgeBase knowledgeBase, RerankModel rerankModel) {
        KnowbaseToolAdapter tool = new KnowbaseToolAdapter(knowledgeBase, rerankModel);
        addTool(tool);
        logger.info("知识库 [{}] 已注册为工具: {}", knowledgeBase.getName(), tool.getName());
    }

    /**
     * 根据名称在可用工具中查找
     */
    private FunctionTool findToolByName(String name) {
        for (FunctionTool tool : getAvailableTools()) {
            if (tool.getClass().getSimpleName().equals(name) || tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 运行智能体
     *
     * @param sessionId 会话编号
     * @param input 输入
     */
    public AgentResponse call(String sessionId, String input) {
        List<AgentResponse> responses = callStream(sessionId, input).collectList().block();
        return mergeResponses(responses, System.currentTimeMillis());
    }

    /**
     * 流式运行智能体
     *
     * @param sessionId 会话编号
     * @param input 输入
     */
    @Override
    public Flux<AgentResponse> callStream(String sessionId, String input) {

        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        return Flux.create(emitter -> {
            int[] usageAccumulator = {0, 0, 0};
            long startTime = System.currentTimeMillis();
            if (contextManager.getMemory() != null) {
                contextManager.getMemory().addUserMessage(sessionId, input);
            }
            List<AgentStep> allSteps = Collections.synchronizedList(new ArrayList<>());
            List<ChatMessage> messages = initializeMessages(sessionId, input, emitter, allSteps);
            runIteration(emitter, messages, new AtomicInteger(0), startTime, allSteps, sessionId, usageAccumulator);
        });
    }

    /**
     * 初始化消息列表
     *
     * 逻辑：
     *
     * 1. 获取所有摘要列表和所有历史消息
     * 2. 计算总token = 所有摘要token + 所有历史消息token
     * 3. 如果 <= MAX_CONTEXT_TOKENS：使用所有摘要 + 所有历史消息
     * 4. 如果 > MAX_CONTEXT_TOKENS：压缩最近4条之前的消息，使用所有摘要 + 最近4条
     *
     * @param sessionId 会话编号
     * @param input 输入
     * @param emitter SSE 发射器（用于推送压缩步骤）
     * @param allSteps 步骤收集器（用于存储压缩步骤）
     */
    private List<ChatMessage> initializeMessages(String sessionId, String input, FluxSink<AgentResponse> emitter, List<AgentStep> allSteps) {
        logger.info("[ReActAgent] initializeMessages sessionId={}", sessionId);
        
        List<ChatMessage> chatMessages = new ArrayList<>();
        String preferredLanguageInstruction = buildPreferredLanguageInstruction();
        this.detectedLanguage = preferredLanguageInstruction != null
                ? preferredLanguageInstruction
                : detectLanguage(input);

        List<MemoryMessage> contextMessages = contextManager.getContextMessages(sessionId);
        logger.info("[ReActAgent] contextMessages size={}", contextMessages.size());
        for (MemoryMessage msg : contextMessages) {
            String role = msg.getRole() == MemoryMessage.Role.USER ? "USER" : "ASSISTANT";
            String content = msg.getContent();
            String preview = content != null && content.length() > 200 ? content.substring(0, 200) + "..." : content;
            logger.info("[ReActAgent]   {} : {}", role, preview);
        }
        appendRecentMessages(chatMessages, contextMessages);
        chatMessages.add(ChatMessage.system(buildSystemPrompt()));
        chatMessages.add(ChatMessage.user(input));
        return chatMessages;
    }

    /**
     * 追加最近消息到上下文
     */
    private void appendRecentMessages(List<ChatMessage> messages, List<MemoryMessage> recentMessages) {
        if (!recentMessages.isEmpty()) {
            StringBuilder context = new StringBuilder("最近对话:\n");
            for (MemoryMessage msg : recentMessages) {
                String roleName = msg.getRole() == MemoryMessage.Role.USER ? "用户" : "助手";
                context.append(roleName).append(": ").append(msg.getContent()).append("\n");

                // 追加ReAct步骤详情（仅ASSISTANT角色）
                if (msg.getRole() == MemoryMessage.Role.ASSISTANT && msg.getSteps() != null && !msg.getSteps().isEmpty()) {
                    for (AgentStep step : msg.getSteps()) {
                        String stepPrefix = formatStepPrefix(step.getStepType());
                        if (stepPrefix != null) {
                            String stepContent = step.getStepType() == AgentStep.StepType.OBSERVATION ? flattenRagflowJson(step.getContent()) : step.getContent();
                            context.append("  ").append(stepPrefix).append(stepContent).append("\n");
                        }
                    }
                }

            }
            messages.add(ChatMessage.system(context.toString()));
        }
    }

    /**
     * 格式化步骤类型前缀
     */
    private String formatStepPrefix(AgentStep.StepType stepType) {
        switch (stepType) {
            case THOUGHT:
                return "[思考] ";
            case ACTION:
                return "[行动] ";
            case OBSERVATION:
                return "[观察] ";
            case ANSWER:
                return "[回答] ";
            case ERROR:
                return "[错误] ";
            default:
                return null;
        }
    }

    /**
     * 递归执行单轮迭代（替代 while 循环）
     */
    private void runIteration(FluxSink<AgentResponse> emitter, List<ChatMessage> messages,
                              AtomicInteger iterationCount, long startTime,
                              List<AgentStep> allSteps,
                              String sessionId,
                              int[] usageAccumulator) {


        int current = iterationCount.get();

        // 检查是否超过最大迭代次数
        if (current >= maxIterations) {

            String finalResponse = "达到最大迭代次数，未能得到最终答案";
            AgentStep errorStep = new AgentStep();
            errorStep.setStepType(AgentStep.StepType.ERROR);
            errorStep.setContent(finalResponse);
            allSteps.add(errorStep);

            // 保存到记忆
            if (contextManager.getMemory() != null) {
                contextManager.getMemory().addAssistantMessage(sessionId, finalResponse, new ArrayList<>(allSteps), null, null, null, null);
            }

            AgentResponse errorResponse = new AgentResponse();
            errorResponse.setResponseType("step");
            errorResponse.addStep(errorStep);
            errorResponse.setErrorMessage(finalResponse);
            errorResponse.setDurationMs(System.currentTimeMillis() - startTime);
            errorResponse.setAnswer(finalResponse);
            errorResponse.setSuccess(false);
            emitter.next(errorResponse);
            emitter.complete();
            return;
        }

        // 流式调用 LLM
        String response = streamChat(messages, usageAccumulator);
        ChatMessage assistantMessage = ChatMessage.assistant(response);
        messages.add(assistantMessage);

        // 推送 THOUGHT 步骤
        AgentStep thoughtStep = new AgentStep();
        thoughtStep.setStepType(AgentStep.StepType.THOUGHT);
        thoughtStep.setContent(response);
        allSteps.add(thoughtStep);

        AgentResponse thoughtResponse = new AgentResponse();
        thoughtResponse.setResponseType("step");
        thoughtResponse.addStep(thoughtStep);
        emitter.next(thoughtResponse);

        // 解析工具调用请求（支持多 action）
        List<ToolCall> toolRequests = parseToolRequests(response);

        if (!toolRequests.isEmpty()) {

            // 收集所有工具执行结果
            StringBuilder combinedResult = new StringBuilder();
            boolean hasError = false;
            String errorMsg = null;

            for (ToolCall toolRequest : toolRequests) {

                FunctionTool tool = findToolByName(toolRequest.toolName());

                if (tool == null) {
                    errorMsg = "错误：找不到 '" + toolRequest.toolName() + "' 工具";
                    hasError = true;
                    break;
                }

                AgentStep actionStep = new AgentStep();
                actionStep.setStepType(AgentStep.StepType.ACTION);
                actionStep.setContent("调用工具: " + toolRequest.toolName());
                actionStep.setToolName(toolRequest.toolName());
                actionStep.setToolArgs(toolRequest.arguments());
                allSteps.add(actionStep);

                AgentResponse actionResponse = new AgentResponse();
                actionResponse.setResponseType("step");
                actionResponse.addStep(actionStep);
                emitter.next(actionResponse);

                // 执行工具
                String toolResult = tool.execute(toolRequest.arguments());

                AgentStep observationStep = new AgentStep();
                observationStep.setStepType(AgentStep.StepType.OBSERVATION);
                observationStep.setContent(toolResult);
                allSteps.add(observationStep);

                AgentResponse observationResponse = new AgentResponse();
                observationResponse.setResponseType("step");
                observationResponse.addStep(observationStep);
                emitter.next(observationResponse);

                // 合并结果
                if (combinedResult.length() > 0) {
                    combinedResult.append("\n");
                }

                combinedResult.append("【").append(toolRequest.toolName()).append("】\n");
                combinedResult.append(flattenRagflowJson(toolResult));
            }

            if (hasError) {

                // 推送错误步骤
                AgentStep errorStep = new AgentStep();
                errorStep.setStepType(AgentStep.StepType.ERROR);
                errorStep.setContent(errorMsg);
                allSteps.add(errorStep);

                // 保存到记忆
                if (contextManager.getMemory() != null) {
                    contextManager.getMemory().addAssistantMessage(sessionId, errorMsg, new ArrayList<>(allSteps), null, null, null, null);
                }

                AgentResponse errorResponse = new AgentResponse();
                errorResponse.setDurationMs(System.currentTimeMillis() - startTime);
                errorResponse.setResponseType("step");
                errorResponse.addStep(errorStep);
                errorResponse.setErrorMessage(errorMsg);
                errorResponse.setSuccess(false);

                emitter.next(errorResponse);
                emitter.complete();

            } else {

                // 更新消息（合并所有结果）
                String toolCallId = "call_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                ChatMessage toolResultMessage = ChatMessage.tool("工具执行结果:\n" + combinedResult.toString(), toolCallId);
                messages.add(toolResultMessage);

                // 递归继续下一轮
                iterationCount.incrementAndGet();
                runIteration(emitter, messages, iterationCount, startTime, allSteps, sessionId, usageAccumulator);
            }

        } else {

            // 在最终回答之前，用 ChatModel 检测用户问题的语言，注入到系统提示词
            List<ChatMessage> finalAnswerMessages = buildFinalAnswerMessages(messages, allSteps);
            StringBuilder finalAnswerBuffer = new StringBuilder();
            StringBuilder thinkingBuffer = new StringBuilder();
            final ChatUsage[] streamUsage = {null};

            model.chatStream(finalAnswerMessages).doOnNext(token -> {

                // 捕获 token 用量（最后一条chunk包含）
                if (token.getUsage() != null) {
                    streamUsage[0] = token.getUsage();
                }

                if (token.getChoices() == null || token.getChoices().isEmpty()) {
                    return;
                }

                ChatChoice choice = token.getChoices().get(0);
                String thinking = choice.getMessage() != null ? choice.getMessage().getReasoningContent() : null;

                // 累积思考内容
                if (thinking != null && !thinking.isEmpty()) {
                    thinkingBuffer.append(thinking);
                }

                // 处理最终回答内容
                String content = choice.getMessage() != null ? choice.getMessage().getContent() : null;
                if (content != null && !content.isEmpty()) {
                    finalAnswerBuffer.append(content);
                }

                // 统一发送 answer 类型的响应，包含 reasoningContent 和 answer
                AgentResponse partialResponse = new AgentResponse();
                partialResponse.setResponseType("answer");
                partialResponse.setReasoningContent(thinkingBuffer.toString());
                partialResponse.setAnswer(finalAnswerBuffer.toString());
                emitter.next(partialResponse);

            }).blockLast();

            // 将最后一步的 usage 累加到总量
            if (streamUsage[0] != null) {
                accumulateUsage(usageAccumulator, streamUsage[0]);
            }

            // 构建最终累计 usage
            ChatUsage totalUsage = new ChatUsage(usageAccumulator[0], usageAccumulator[1], usageAccumulator[2]);

            // 保存完整答案
            String finalAnswer = finalAnswerBuffer.toString();
            String reasoningContent = thinkingBuffer.toString();

            // 生成建议问题（仅在启用时生成）
            String userQuestion = findUserQuestion(messages);
            List<String> suggestions = suggestionsEnabled ? generateSuggestions(userQuestion, finalAnswer) : null;

            long durationMs = System.currentTimeMillis() - startTime;

            if (contextManager.getMemory() != null) {
                contextManager.getMemory().addAssistantMessage(sessionId, finalAnswer, new ArrayList<>(allSteps), reasoningContent, suggestions, totalUsage, durationMs);
            }

            // 推送最终响应
            AgentResponse finalResponse = new AgentResponse();
            finalResponse.setResponseType("answer");
            finalResponse.setAnswer(finalAnswer);
            finalResponse.setReasoningContent(reasoningContent);
            finalResponse.setDurationMs(durationMs);
            finalResponse.setUsage(totalUsage);
            finalResponse.setSources(KnowledgeSourceUtil.fromSteps(allSteps));
            finalResponse.setSuccess(true);
            emitter.next(finalResponse);

            // 推送建议问题
            if (suggestions != null && !suggestions.isEmpty()) {
                AgentResponse suggestionsResponse = new AgentResponse();
                suggestionsResponse.setResponseType("suggestions");
                suggestionsResponse.setSuggestions(suggestions);
                suggestionsResponse.setSuccess(true);
                emitter.next(suggestionsResponse);
            }

            emitter.complete();
        }
    }

    /**
     * 处理工具执行结果：当结果非常长时，按块并发总结，避免直接将超大文本注入后续推理。
     *
     * @param toolName 工具名称
     */
    private String processToolResult(String toolName, String rawResult, String originalQuestion, String currentThought, int[] usageAccumulator) {
        // 直接返回原始结果，不做分块总结
        return rawResult;
    }

    private String streamChat(List<ChatMessage> messages, int[] usageAccumulator) {
        // 推理阶段使用 JSON 格式
        StringBuilder buffer = new StringBuilder();
        final ChatUsage[] lastUsage = {null};
        model.chatStream(messages, ResponseFormatType.JSON_OBJECT).doOnNext(token -> {
            if (token.getUsage() != null) {
                lastUsage[0] = token.getUsage();
            }
            if (token.getChoices() != null && !token.getChoices().isEmpty()) {
                String content = token.getChoices().get(0).getMessage().getContent();
                if (content != null) {
                    buffer.append(content);
                }
            }
        }).blockLast();

        // 累加思考阶段的 token 用量
        if (lastUsage[0] != null) {
            accumulateUsage(usageAccumulator, lastUsage[0]);
        }

        return buffer.toString().replace("```json", "").replace("```", "");
    }

    /**
     * 线程安全地累加 token 用量到累加器
     * @param accumulator [promptTokens, completionTokens, totalTokens]
     * @param usage 本次调用的用量
     */
    private synchronized void accumulateUsage(int[] accumulator, ChatUsage usage) {
        if (usage.getPromptTokens() != null) {
            accumulator[0] += usage.getPromptTokens();
        }
        if (usage.getCompletionTokens() != null) {
            accumulator[1] += usage.getCompletionTokens();
        }
        if (usage.getTotalTokens() != null) {
            accumulator[2] += usage.getTotalTokens();
        }
    }

    /**
     * 从消息列表中提取最后一条用户问题的文本
     */
    private String findUserQuestion(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                return messages.get(i).getContent();
            }
        }
        return null;
    }

    /**
     * 使用 ChatModel 检测文本的主要语言
     *
     * @param text 待检测文本
     * @return BCP 47 语言代码（zh/en/ja 等），失败时返回 null
     */
    private String detectLanguage(String text) {
        try {
            String prompt = Constant.LANGUAGE_DETECT_PROMPT.formatted(text);
            ChatResponse response = model.chat(prompt);
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                ChatMessage msg = response.getChoices().get(0).getMessage();
                if (msg != null && msg.getContent() != null) {
                    return "输入问题语言：" + msg.getContent().trim().toLowerCase();
                }
            }
        } catch (Exception e) {
            logger.warn("语言检测失败: {}", e.getMessage());
        }
        return null;
    }

    private String normalizePreferredLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String normalized = language.trim().replace('_', '-').toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("en") || normalized.startsWith("en-")) {
            return "en";
        }
        if (normalized.equals("zh-tw")
                || normalized.equals("zh-hk")
                || normalized.equals("zh-mo")
                || normalized.equals("zh-hant")) {
            return "zh-TW";
        }
        if (normalized.equals("zh")
                || normalized.equals("zh-cn")
                || normalized.equals("zh-sg")
                || normalized.equals("zh-hans")) {
            return "zh-CN";
        }
        return null;
    }

    private String buildPreferredLanguageInstruction() {
        if (preferredLanguage == null) {
            return null;
        }
        return switch (preferredLanguage) {
            case "zh-CN" -> """
                    用户选择的回答语言：简体中文（zh-CN）。
                    请使用简体中文进行推理并给出最终回答。知识库可能包含简体中文、繁体中文或英文资料；
                    检索时可以使用任何语言的相关资料，必要时将检索词翻译或扩展为其他语言后调用知识库工具。
                    回答应翻译为简体中文，专有名词可保留原文。
                    """.trim();
            case "zh-TW" -> """
                    使用者選擇的回答語言：繁體中文（zh-TW）。
                    請使用繁體中文進行推理並給出最終回答。知識庫可能包含簡體中文、繁體中文或英文資料；
                    檢索時可以使用任何語言的相關資料，必要時把檢索詞翻譯或擴展為其他語言後再調用知識庫工具。
                    回答應轉為繁體中文，專有名詞可保留原文。
                    """.trim();
            case "en" -> """
                    The user selected English (en) as the response language.
                    Reason and provide the final answer in English. The knowledge base may contain Simplified Chinese,
                    Traditional Chinese, or English sources. Retrieve relevant sources in any language and translate or
                    expand the search query into other supported languages when needed before calling knowledge tools.
                    Translate the answer into English and preserve original product names or technical terms when useful.
                    """.trim();
            default -> null;
        };
    }

    private List<ChatMessage> buildFinalAnswerMessages(List<ChatMessage> messages, List<AgentStep> allSteps) {

        List<ChatMessage> finalAnswerMessages = new ArrayList<>();
        String langHint = this.detectedLanguage != null ? this.detectedLanguage : "";
        finalAnswerMessages.add(ChatMessage.system(this.systemPrompt + (langHint.isEmpty() ? "" : "\n\n" + langHint)));

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                finalAnswerMessages.add(msg);
                break;
            }
        }

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("模型思考过程：\n");

        for (AgentStep step : allSteps) {
            if (step.getStepType() == AgentStep.StepType.THOUGHT) {
                contextBuilder.append(extractThoughtContent(step.getContent())).append("\n");
            } else if (step.getStepType() == AgentStep.StepType.OBSERVATION) {
                contextBuilder.append("工具调用结果：").append(flattenRagflowJson(step.getContent())).append("\n");
            }
        }

        String toolCallId = "call_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        finalAnswerMessages.add(ChatMessage.tool(contextBuilder.toString(), toolCallId));
        return finalAnswerMessages;
    }

    /**
     * 将 RagflowTool 返回的结构化 JSON 转为纯文本段落（供 LLM 上下文使用）。
     * 非 ragflow 格式则原样返回。
     */
    private String flattenRagflowJson(String content) {
        if (content == null || content.isEmpty()) return content;
        try {
            JSONObject json = JSON.parseObject(content);
            if (json != null && "ragflow".equals(json.getString("source"))) {
                JSONArray items = json.getJSONArray("items");
                if (items != null && !items.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("检索到 ").append(items.size()).append(" 条相关内容：\n\n");
                    for (int i = 0; i < items.size(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String title = item.getString("title");
                        if (title != null && !title.isEmpty()) {
                            sb.append("【").append(title).append("】\n");
                        }
                        sb.append(item.getString("content")).append("\n\n");
                    }
                    return sb.toString().trim();
                }
            }
        } catch (Exception e) {
            // 非 JSON 格式，直接返回原文
        }
        return content;
    }

    /**
     * 安全提取 THOUGHT 步骤中的思考内容。
     * 模型可能不输出严格的 JSON 格式，需要容错处理。
     */
    private String extractThoughtContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 尝试按 JSON 解析提取 thought 字段
        try {
            JSONObject obj = JSONObject.parseObject(content);
            if (obj.containsKey("thought") && obj.get("thought") != null) {
                return obj.getString("thought");
            }
        } catch (Exception e) {
            // JSON 解析失败，回退到原始内容
        }
        // 回退：直接使用原始内容（去除可能的 JSON 外壳）
        return content.trim();
    }

    /**
     * 合并多个 AgentResponse 为一个
     */
    private AgentResponse mergeResponses(List<AgentResponse> responses, long startTime) {

        if (responses == null || responses.isEmpty()) {
            AgentResponse empty = new AgentResponse();
            empty.setErrorMessage("无响应");
            empty.setDurationMs(System.currentTimeMillis() - startTime);
            empty.setSuccess(false);
            return empty;
        }

        AgentResponse merged = new AgentResponse();

        for (AgentResponse response : responses) {
            for (AgentStep step : response.getSteps()) {
                merged.addStep(step);
            }
        }

        // 找到包含 answer 的 response（跳过 suggestions 类型）
        AgentResponse answerResponse = null;
        for (int i = responses.size() - 1; i >= 0; i--) {
            AgentResponse r = responses.get(i);
            if (r.getAnswer() != null && !"suggestions".equals(r.getResponseType())) {
                answerResponse = r;
                break;
            }
        }

        if (answerResponse != null) {
            merged.setAnswer(answerResponse.getAnswer());
            merged.setReasoningContent(answerResponse.getReasoningContent());
            merged.setErrorMessage(answerResponse.getErrorMessage());
            merged.setDurationMs(answerResponse.getDurationMs());
            merged.setSources(answerResponse.getSources());
            merged.setSuccess(answerResponse.isSuccess());
        } else {
            // fallback: 使用最后一个 response
            AgentResponse last = responses.get(responses.size() - 1);
            merged.setAnswer(last.getAnswer());
            merged.setReasoningContent(last.getReasoningContent());
            merged.setErrorMessage(last.getErrorMessage());
            merged.setDurationMs(last.getDurationMs());
            merged.setSources(last.getSources());
            merged.setSuccess(last.isSuccess());
        }

        return merged;
    }

    /**
     * 构建系统提示词，包含当前可用工具信息和可用技能信息
     */
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        // 首先注入用户配置的系统提示词（限制、角色定义等），确保 ReAct 推理阶段和最终回答阶段一致生效
        prompt.append(this.systemPrompt).append("\n\n");
        // 注入检测到的用户语言，确保 thought 过程语言与用户一致
        if (this.detectedLanguage != null) {
            prompt.append(this.detectedLanguage).append("\n\n");
        }
        // 使用统一的提示词（技能列表已封装为skill-selector工具）
        prompt.append(Constant.REACT_PROMPT_NO_SKILLS);

        // ===== 1. 拼接工具列表 =====
        List<FunctionTool> availableTools = getAvailableTools();

        if (!availableTools.isEmpty()) {
            prompt.append("## 工具列表\n\n");
            for (int i = 0; i < availableTools.size(); i++) {
                prompt.append(i + 1).append(". ");
                prompt.append(availableTools.get(i).toPrompt());
                prompt.append("\n");
            }
        } else {
            prompt.append("## 工具列表\n\n");
            prompt.append("空\n");
        }

        return prompt.toString();
    }

    /**
     * 解析模型响应中的工具调用请求（支持多 action）
     *
     * @param response LLM 响应内容
     * @return 工具执行请求列表，空列表表示无工具调用
     */
    private List<ToolCall> parseToolRequests(String response) {
        List<ToolCall> requests = new ArrayList<>();
        try {

            response = response.replace("```json", "").replace("```", "");
            JSONObject jsonResponse = JSON.parseObject(response.trim());

            String type = jsonResponse.getString("type");
            if (!"action".equals(type)) {
                return requests; // 空 list，表示无工具调用
            }

            // 格式1：多 actions（优先）
            JSONArray actionsArray = jsonResponse.getJSONArray("actions");
            if (actionsArray != null && !actionsArray.isEmpty()) {
                for (int i = 0; i < actionsArray.size(); i++) {
                    JSONObject action = actionsArray.getJSONObject(i);
                    ToolCall request = parseSingleAction(action);
                    if (request != null) {
                        requests.add(request);
                    }
                }
                return requests;
            }

            // 格式2：单 action（向后兼容）
            JSONObject singleAction = jsonResponse.getJSONObject("action");
            if (singleAction != null) {
                ToolCall request = parseSingleAction(singleAction);
                if (request != null) {
                    requests.add(request);
                }
            }

        } catch (Exception e) {
            logger.error("解析工具调用请求失败: {}", e.getMessage());
        }
        return requests;
    }

    /**
     * 解析单个 action
     *
     * @param action JSON 对象
     * @return 工具执行请求，解析失败返回 null
     */
    private ToolCall parseSingleAction(JSONObject action) {
        if (action == null) {
            return null;
        }
        String toolName = action.getString("name");
        if (toolName == null || toolName.isEmpty()) {
            return null;
        }
        JSONObject arguments = action.getJSONObject("arguments");

        Map<String, Object> argsMap = new HashMap<>();
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                argsMap.put(key, arguments.get(key));
            }
        }
        return new ToolCall(toolName, argsMap);
    }


    public Memory getMemory() {
        return contextManager.getMemory();
    }
    
    public void setMemory(Memory memory) {
        this.contextManager.setMemory(memory);
    }

    public void name(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void description(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSuggestionsPrompt(String suggestionsPrompt) {
        this.suggestionsPrompt = suggestionsPrompt;
    }

    public String getSuggestionsPrompt() {
        return suggestionsPrompt;
    }

    public void setSuggestionsEnabled(boolean suggestionsEnabled) {
        this.suggestionsEnabled = suggestionsEnabled;
    }

    public boolean isSuggestionsEnabled() {
        return suggestionsEnabled;
    }

    /**
     * 根据用户问题和最终回答，生成后续建议问题
     */
    private List<String> generateSuggestions(String question, String answer) {
        try {
            String truncatedAnswer = answer.length() > 2000 ? answer.substring(0, 2000) + "..." : answer;
            // 叠加逻辑：默认提示词管格式，配置的提示词叠加业务约束
            String businessConstraints = (suggestionsPrompt != null && !suggestionsPrompt.isEmpty()) ? suggestionsPrompt : "";
            String prompt = Constant.SUGGESTIONS_PROMPT.formatted(businessConstraints, question, truncatedAnswer);
            List<ChatMessage> messages = List.of(ChatMessage.user(prompt));
            ChatResponse response = model.chat(messages, ResponseFormatType.JSON_OBJECT);
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                ChatMessage msg = response.getChoices().get(0).getMessage();
                if (msg != null && msg.getContent() != null) {
                    String content = msg.getContent().trim();
                    return JSON.parseArray(content, String.class);
                }
            }
        } catch (Exception e) {
            logger.warn("生成建议问题失败: {}", e.getMessage());
        }
        return null;
    }
    
    // ===== ContextManager 配置方法 =====
    
    public ContextManager getContextManager() {
        return contextManager;
    }
    
    public void setContextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }
    
    public void setKeepRecentRounds(int keepRecentRounds) {
        this.contextManager.setKeepRecentRounds(keepRecentRounds);
    }
    
    public int getKeepRecentRounds() {
        return this.contextManager.getKeepRecentRounds();
    }
    
    public void setKeepRecentToolResults(int keepRecentToolResults) {
        this.contextManager.setKeepRecentToolResults(keepRecentToolResults);
    }
    
    public int getKeepRecentToolResults() {
        return this.contextManager.getKeepRecentToolResults();
    }
    
    public void setEnableToolTrimming(boolean enableToolTrimming) {
        this.contextManager.setEnableToolTrimming(enableToolTrimming);
    }
    
    public boolean isEnableToolTrimming() {
        return this.contextManager.isEnableToolTrimming();
    }
}
