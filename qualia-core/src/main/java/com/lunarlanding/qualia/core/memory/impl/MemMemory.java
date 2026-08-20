package com.lunarlanding.qualia.core.memory.impl;

import com.lunarlanding.qualia.core.agent.spec.AgentStep;
import com.lunarlanding.qualia.core.memory.Memory;
import com.lunarlanding.qualia.core.memory.MemoryMessage;
import com.lunarlanding.qualia.core.model.chat.ChatUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于内存的会话级记忆实现
 * 适用于开发测试或无需持久化的场景，进程重启后数据丢失
 */
public class MemMemory implements Memory {

    private static final Logger logger = LoggerFactory.getLogger(MemMemory.class);

    // sessionId -> 消息列表
    private final ConcurrentHashMap<String, List<MemoryMessage>> messageStore = new ConcurrentHashMap<>();
    
    // sessionId -> 摘要列表
    private final ConcurrentHashMap<String, List<SummaryEntry>> summaryStore = new ConcurrentHashMap<>();
    
    // sessionId -> 序列号计数器
    private final ConcurrentHashMap<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    @Override
    public void addUserMessage(String sessionId, String content) {
        List<MemoryMessage> messages = messageStore.computeIfAbsent(sessionId, k -> new ArrayList<>());
        int sequence = getNextSequence(sessionId);
        MemoryMessage msg = new MemoryMessage(sessionId, MemoryMessage.Role.USER, content);
        msg.setSequence(sequence);
        synchronized (messages) {
            messages.add(msg);
        }
        logger.debug("[MemMemory] 添加用户消息 sessionId={}, sequence={}", sessionId, sequence);
    }

    @Override
    public void addAssistantMessage(String sessionId, String content, List<AgentStep> steps,
                                    String reasoningContent, ChatUsage usage, Long durationMs) {
        List<MemoryMessage> messages = messageStore.computeIfAbsent(sessionId, k -> new ArrayList<>());
        int sequence = getNextSequence(sessionId);
        MemoryMessage msg = new MemoryMessage(sessionId, MemoryMessage.Role.ASSISTANT, content);
        msg.setSequence(sequence);
        msg.setSteps(steps);
        msg.setReasoningContent(reasoningContent);
        if (usage != null) {
            msg.setPromptTokens(usage.getPromptTokens());
            msg.setCompletionTokens(usage.getCompletionTokens());
            msg.setTotalTokens(usage.getTotalTokens());
        }
        msg.setDurationMs(durationMs);
        synchronized (messages) {
            messages.add(msg);
        }
        logger.debug("[MemMemory] 添加助手消息 sessionId={}, sequence={}", sessionId, sequence);
    }

    @Override
    public List<MemoryMessage> getRecentMessages(String sessionId, int limit) {
        List<MemoryMessage> messages = messageStore.get(sessionId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (messages) {
            int from = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(from, messages.size()));
        }
    }

    @Override
    public List<MemoryMessage> getSessionHistory(String sessionId) {
        List<MemoryMessage> messages = messageStore.get(sessionId);
        if (messages == null) {
            return Collections.emptyList();
        }
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    @Override
    public List<AgentStep> getMessageSteps(String messageId) {
        for (List<MemoryMessage> messages : messageStore.values()) {
            synchronized (messages) {
                for (MemoryMessage msg : messages) {
                    if (msg.getId().equals(messageId) && msg.getRole() == MemoryMessage.Role.ASSISTANT) {
                        return msg.getSteps();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void clearSession(String sessionId) {
        messageStore.remove(sessionId);
        summaryStore.remove(sessionId);
        sequenceCounters.remove(sessionId);
        logger.debug("[MemMemory] 清空会话 sessionId={}", sessionId);
    }

    @Override
    public List<String> getAllSummaries(String sessionId) {
        List<SummaryEntry> summaries = summaryStore.get(sessionId);
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (summaries) {
            List<String> result = new ArrayList<>();
            for (SummaryEntry entry : summaries) {
                result.add(entry.summaryContent);
            }
            return result;
        }
    }

    @Override
    public java.util.Set<String> getSummarizedMessageIds(String sessionId) {
        List<SummaryEntry> summaries = summaryStore.get(sessionId);
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptySet();
        }
        java.util.Set<String> result = new java.util.HashSet<>();
        synchronized (summaries) {
            for (SummaryEntry entry : summaries) {
                result.addAll(entry.summarizedMessageIds);
            }
        }
        return result;
    }

    @Override
    public void saveSummary(String sessionId, String summary, List<String> summarizedMessageIds, int tokenBefore, int tokenAfter) {
        List<SummaryEntry> summaries = summaryStore.computeIfAbsent(sessionId, k -> new ArrayList<>());
        SummaryEntry entry = new SummaryEntry();
        entry.summaryContent = summary;
        entry.summarizedMessageIds = summarizedMessageIds;
        entry.tokenBefore = tokenBefore;
        entry.tokenAfter = tokenAfter;
        entry.createdAt = System.currentTimeMillis();
        synchronized (summaries) {
            summaries.add(entry);
        }
        logger.debug("[MemMemory] 保存摘要 sessionId={}, summarizedCount={}", sessionId, summarizedMessageIds.size());
    }

    @Override
    public List<MemoryMessage> getMessagesAfterId(String sessionId, String afterMessageId) {
        List<MemoryMessage> messages = messageStore.get(sessionId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (messages) {
            List<MemoryMessage> result = new ArrayList<>();
            boolean found = false;
            for (MemoryMessage msg : messages) {
                if (found) {
                    result.add(msg);
                }
                if (msg.getId().equals(afterMessageId)) {
                    found = true;
                }
            }
            return result;
        }
    }

    /**
     * 获取下一个序列号
     */
    private int getNextSequence(String sessionId) {
        AtomicInteger counter = sequenceCounters.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }

    /**
     * 摘要条目
     */
    private static class SummaryEntry {
        String summaryContent;
        List<String> summarizedMessageIds;
        int tokenBefore;
        int tokenAfter;
        long createdAt;
    }
}
