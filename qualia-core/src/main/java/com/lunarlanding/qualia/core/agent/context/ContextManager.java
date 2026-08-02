package com.lunarlanding.qualia.core.agent.context;

import com.lunarlanding.qualia.core.agent.spec.AgentStep;
import com.lunarlanding.qualia.core.memory.Memory;
import com.lunarlanding.qualia.core.memory.MemoryMessage;
import com.lunarlanding.qualia.core.model.chat.ChatModel;
import com.lunarlanding.qualia.core.model.chat.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 上下文管理器
 * 负责管理会话上下文消息的获取和工具结果裁剪
 */
public class ContextManager {

    private static final Logger logger = LoggerFactory.getLogger(ContextManager.class);
    
    private Memory memory;
    private ChatModel model;

    // 描述：压缩配置，控制上下文消息的保留数量
    // keepRecentRounds:保留最近多少轮对话（每轮 = 1条用户 + 1条助手）
    // keepRecentToolResults:工具裁剪时，保留最近多少个工具调用的结果
    // enableToolTrimming:是否启用工具结果裁剪
    private int keepRecentRounds = 5;
    private int keepRecentToolResults = 3;
    private boolean enableToolTrimming = false;

    // 摘要压缩配置
    // compressBatchSize:每次压缩的对话轮数（每轮 = 2条消息）
    // maxSummaries:最多保留摘要数量
    // enableSummaryCompression:是否启用摘要压缩
    private int compressBatchSize = 2;
    private int maxSummaries = 3;
    private boolean enableSummaryCompression = true;

    public ContextManager() {}

    public ContextManager(Memory memory) {
        this.memory = memory;
    }

    public ContextManager(Memory memory, ChatModel model) {
        this.memory = memory;
        this.model = model;
    }

    // ===== 上下文消息获取 =====

    /**
     * 获取上下文消息列表（压缩前置，可选裁剪工具结果）
     *
     * @param sessionId 会话ID
     * @return 处理后的消息列表，按时间正序
     */
    public List<MemoryMessage> getContextMessages(String sessionId) {
        if (memory == null) {
            return Collections.emptyList();
        }
        
        // 1. 先压缩
        compressIfNeeded(sessionId);
        
        // 2. 构建上下文
        List<MemoryMessage> context = buildContext(sessionId);
        
        // 3. 工具结果裁剪
        if (enableToolTrimming) {
            trimToolResults(context);
        }
        
        return context;
    }
    
    // ===== 摘要压缩 =====
    
    /**
     * 压缩检查与执行
     */
    private void compressIfNeeded(String sessionId) {
        if (!enableSummaryCompression || memory == null || model == null) return;
        
        // 获取未压缩消息
        Set<String> summarizedIds = memory.getSummarizedMessageIds(sessionId);
        List<MemoryMessage> allMessages = memory.getSessionHistory(sessionId);
        List<MemoryMessage> unsummarized = allMessages.stream().filter(m -> !summarizedIds.contains(m.getId())).toList();
        
        int keepCount = keepRecentRounds * 2;           // 20 条
        int compressCount = compressBatchSize * 2;      // 8 条
        
        logger.info("[ContextManager] sessionId={}, allMessages={}, summarizedIds={}, unsummarized={}, keepCount={}", sessionId, allMessages.size(), summarizedIds.size(), unsummarized.size(), keepCount);
                
        // 未压缩消息不足 20 条，不压缩
        if (unsummarized.size() < keepCount) {
            logger.info("[ContextManager] 未压缩消息不足 {} 条，跳过压缩", keepCount);
            return;
        }
        
        // 压缩最早的 8 条（4 轮）
        List<MemoryMessage> toCompress = unsummarized.subList(0, compressCount);
        
        try {
            logger.info("[ContextManager] 触发压缩，sessionId={}, 压缩 {} 条消息", sessionId, compressCount);
            String summary = generateSummary(toCompress);
            List<String> messageIds = toCompress.stream().map(MemoryMessage::getId).toList();
            memory.saveSummary(sessionId, summary, messageIds, estimateTokens(toCompress), estimateTokens(summary));
            logger.info("[ContextManager] 压缩成功，sessionId={}, 生成摘要长度={}", sessionId, summary.length());
        } catch (Exception e) {
            logger.error("[ContextManager] 压缩失败，sessionId={}", sessionId, e);
        }
    }
    
    /**
     * 构建上下文消息列表
     */
    private List<MemoryMessage> buildContext(String sessionId) {
        List<MemoryMessage> context = new ArrayList<>();
        
        // 1. 获取摘要（最多 3 个）
        List<String> summaries = memory.getAllSummaries(sessionId);
        
        logger.info("[ContextManager] buildContext sessionId={}, 摘要总数={}", sessionId, summaries.size());
        
        if (!summaries.isEmpty()) {
            int startIndex = Math.max(0, summaries.size() - maxSummaries);
            List<String> recentSummaries = summaries.subList(startIndex, summaries.size());
            
            // 每个摘要作为单独的 ASSISTANT 消息注入
            for (String summary : recentSummaries) {
                MemoryMessage summaryMsg = new MemoryMessage(sessionId, MemoryMessage.Role.ASSISTANT, "[历史摘要] " + summary);
                context.add(summaryMsg);
            }
        }
        
        // 2. 获取未压缩消息
        Set<String> summarizedIds = memory.getSummarizedMessageIds(sessionId);
        List<MemoryMessage> recentMessages = memory.getRecentMessages(sessionId, keepRecentRounds * 2);
        List<MemoryMessage> unsummarized = recentMessages.stream().filter(m -> !summarizedIds.contains(m.getId())).toList();
        
        logger.info("[ContextManager] buildContext sessionId={}, recentMessages={}, unsummarized={}, 最终上下文={}",
                sessionId, recentMessages.size(), unsummarized.size(), context.size() + unsummarized.size());
        
        context.addAll(unsummarized);
        return context;
    }
    
    /**
     * 生成摘要（调用 LLM）
     */
    private String generateSummary(List<MemoryMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请将以下对话历史压缩为结构化摘要，保留关键信息：\n\n");
        
        for (MemoryMessage msg : messages) {
            String role = msg.getRole() == MemoryMessage.Role.USER ? "用户" : "助手";
            prompt.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        
        prompt.append("\n要求：\n");
        prompt.append("1. 保留用户的核心问题和意图\n");
        prompt.append("2. 保留助手的关键回答和结论\n");
        prompt.append("3. 保留重要的工具调用结果（如有）\n");
        prompt.append("4. 控制在 500 token 以内\n");
        
        // 调用 LLM 生成摘要
        ChatResponse response = model.chat(prompt.toString());
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new RuntimeException("LLM 返回空结果");
        }
        return response.getChoices().get(0).getMessage().getContent();
    }
    

    
    /**
     * 估算 token 数量（简化实现）
     */
    private int estimateTokens(List<MemoryMessage> messages) {
        int totalChars = 0;
        for (MemoryMessage msg : messages) {
            if (msg.getContent() != null) {
                totalChars += msg.getContent().length();
            }
        }
        // 粗略估算：1个中文字符约等于2个token
        return totalChars * 2;
    }
    
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() * 2;
    }

    // ===== 工具结果裁剪 =====

    /**
     * 工具结果裁剪（零 LLM 成本）
     * 保留最近 K 个工具调用结果，更早的替换为占位符
     */
    private void trimToolResults(List<MemoryMessage> messages) {
        int count = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MemoryMessage msg = messages.get(i);
            if (msg.getRole() == MemoryMessage.Role.ASSISTANT && msg.getSteps() != null) {
                for (AgentStep step : msg.getSteps()) {
                    if (step.getStepType() == AgentStep.StepType.OBSERVATION) {
                        count++;
                        if (count > keepRecentToolResults) {
                            step.setContent("[工具结果已清理，如需查看请重新调用]");
                        }
                    }
                }
            }
        }
    }

    // ===== Getter/Setter =====

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public int getKeepRecentRounds() {
        return keepRecentRounds;
    }

    public void setKeepRecentRounds(int keepRecentRounds) {
        this.keepRecentRounds = keepRecentRounds;
    }

    public int getKeepRecentToolResults() {
        return keepRecentToolResults;
    }

    public void setKeepRecentToolResults(int keepRecentToolResults) {
        this.keepRecentToolResults = keepRecentToolResults;
    }

    public boolean isEnableToolTrimming() {
        return enableToolTrimming;
    }

    public void setEnableToolTrimming(boolean enableToolTrimming) {
        this.enableToolTrimming = enableToolTrimming;
    }

    // ===== 摘要压缩配置 =====

    public ChatModel getModel() {
        return model;
    }

    public void setModel(ChatModel model) {
        this.model = model;
    }

    public int getCompressBatchSize() {
        return compressBatchSize;
    }

    public void setCompressBatchSize(int compressBatchSize) {
        this.compressBatchSize = compressBatchSize;
    }

    public int getMaxSummaries() {
        return maxSummaries;
    }

    public void setMaxSummaries(int maxSummaries) {
        this.maxSummaries = maxSummaries;
    }

    public boolean isEnableSummaryCompression() {
        return enableSummaryCompression;
    }

    public void setEnableSummaryCompression(boolean enableSummaryCompression) {
        this.enableSummaryCompression = enableSummaryCompression;
    }
}