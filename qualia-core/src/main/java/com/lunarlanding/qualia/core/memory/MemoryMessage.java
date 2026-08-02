package com.lunarlanding.qualia.core.memory;

import com.alibaba.fastjson.JSON;
import com.lunarlanding.qualia.core.agent.spec.AgentStep;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

/**
 * 会话记忆消息类
 * 用于持久化存储会话消息到MySQL数据库
 * 与 chat.ChatMessage 的区别：
 * - chat.ChatMessage: LLM交互的轻量级消息模型
 * - SessionMemoryMessage: 带持久化字段的数据库实体（包含stepsJson、sequence等）
 */
@ToString
public class MemoryMessage {

    public enum Role {
        USER,       // 用户输入
        ASSISTANT   // AI回复（包含思考步骤）
    }

    private String id;
    private String sessionId;
    private Role role;
    private String content;
    private List<AgentStep> steps;    // 仅ASSISTANT角色有效
    private String reasoningContent;  // 深度思考内容（推理模型专用）
    private List<String> suggestions; // 建议问题列表
    private Integer promptTokens;     // 输入 token 数
    private Integer completionTokens; // 输出 token 数
    private Integer totalTokens;      // 总 token 数
    private Long durationMs;          // 响应耗时（毫秒）
    private String feedback;          // 用户反馈：up-赞，down-踩，NULL-未反馈
    private long createdAt;
    private int sequence;             // 会话内顺序号

    public MemoryMessage() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public MemoryMessage(String sessionId, Role role, String content) {
        this();
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps;
    }

    /**
     * 数据库映射用：自动反序列化 JSON 字符串到 steps
     */
    public void setStepsJson(String stepsJson) {
        if (stepsJson != null && !stepsJson.isEmpty()) {
            try {
                this.steps = JSON.parseArray(stepsJson, AgentStep.class);
            } catch (Exception e) {
                this.steps = null;
            }
        }
    }

    /**
     * 数据库映射用：自动序列化 steps 为 JSON 字符串
     */
    public String getStepsJson() {
        return steps != null ? JSON.toJSONString(steps) : null;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    /**
     * 数据库映射用：自动反序列化 JSON 字符串到 suggestions
     */
    public void setSuggestionsJson(String suggestionsJson) {
        if (suggestionsJson != null && !suggestionsJson.isEmpty()) {
            try {
                this.suggestions = JSON.parseArray(suggestionsJson, String.class);
            } catch (Exception e) {
                this.suggestions = null;
            }
        }
    }

    /**
     * 数据库映射用：自动序列化 suggestions 为 JSON 字符串
     */
    public String getSuggestionsJson() {
        return suggestions != null ? JSON.toJSONString(suggestions) : null;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
