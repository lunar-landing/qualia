package com.lunarlanding.qualia.core.memory;

import com.lunarlanding.qualia.core.agent.spec.AgentStep;
import com.lunarlanding.qualia.core.model.chat.ChatUsage;

import java.util.List;

/**
 * 统一记忆接口
 * 支持会话级消息存储和检索
 */
public interface Memory {

    /**
     * 添加用户消息
     *
     * @param sessionId 会话ID
     * @param content   用户输入内容
     */
    void addUserMessage(String sessionId, String content);

    /**
     * 添加AI回复（包含思考步骤和建议问题）
     *
     * @param sessionId        会话ID
     * @param content          AI最终回复内容
     * @param steps            ReAct执行步骤（THOUGHT/ACTION/OBSERVATION）
     * @param reasoningContent 深度思考内容（推理模型专用）
     * @param suggestions      建议的后续问题
     * @param usage            Token 用量统计
     * @param durationMs       响应总耗时（毫秒）
     */
    void addAssistantMessage(String sessionId, String content, List<AgentStep> steps, String reasoningContent, List<String> suggestions, ChatUsage usage, Long durationMs);

    /**
     * 获取会话的最近消息（用于LLM上下文构建）
     *
     * @param sessionId 会话ID
     * @param limit     最近N条
     * @return 消息列表（按时间正序）
     */
    List<MemoryMessage> getRecentMessages(String sessionId, int limit);

    /**
     * 获取会话完整历史（用于前端展示）
     *
     * @param sessionId 会话ID
     * @return 消息列表（按时间正序）
     */
    List<MemoryMessage> getSessionHistory(String sessionId);

    /**
     * 获取某条assistant消息的详细步骤
     *
     * @param messageId 消息ID
     * @return 步骤列表，如果不是assistant消息返回null
     */
    List<AgentStep> getMessageSteps(String messageId);

    /**
     * 清空会话所有消息
     *
     * @param sessionId 会话ID
     */
    void clearSession(String sessionId);

    /**
     * 获取会话的所有摘要列表（按时间正序）
     *
     * @param sessionId 会话ID
     * @return 摘要内容列表，如果没有则返回空列表
     */
    List<String> getAllSummaries(String sessionId);

    /**
     * 获取所有已被压缩的消息ID集合
     *
     * @param sessionId 会话ID
     * @return 已压缩的消息ID集合
     */
    java.util.Set<String> getSummarizedMessageIds(String sessionId);

    /**
     * 保存压缩摘要
     *
     * @param sessionId 会话ID
     * @param summary 摘要内容
     * @param summarizedMessageIds 被压缩的消息ID列表
     * @param tokenBefore 压缩前token数
     * @param tokenAfter 压缩后token数
     */
    void saveSummary(String sessionId, String summary, List<String> summarizedMessageIds, int tokenBefore, int tokenAfter);

    /**
     * 获取指定消息之后的消息
     *
     * @param sessionId 会话ID
     * @param afterMessageId 起始消息ID（不包含）
     * @return 消息列表（按时间正序）
     */
    List<MemoryMessage> getMessagesAfterId(String sessionId, String afterMessageId);
}
