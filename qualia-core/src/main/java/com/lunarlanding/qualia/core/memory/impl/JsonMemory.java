package com.lunarlanding.qualia.core.memory.impl;

import com.alibaba.fastjson.JSON;
import com.lunarlanding.qualia.core.agent.spec.AgentStep;
import com.lunarlanding.qualia.core.memory.Memory;
import com.lunarlanding.qualia.core.memory.MemoryMessage;
import com.lunarlanding.qualia.core.model.chat.ChatUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JSON 文件的会话级记忆实现
 * 用于本地 CLI 开发场景，数据存储在 workspace/.qualia/memory/ 目录下
 */
public class JsonMemory implements Memory {

    private static final Logger logger = LoggerFactory.getLogger(JsonMemory.class);
    private final Path memoryDir;
    private final Map<String, List<MemoryMessage>> cache = new ConcurrentHashMap<>();
    private final Map<String, List<SummaryEntry>> summaryCache = new ConcurrentHashMap<>();

    public JsonMemory(Path memoryPath) {
        this.memoryDir = memoryPath;
        try {
            Files.createDirectories(memoryDir);
        } catch (IOException e) {
            throw new RuntimeException("创建 memory 目录失败: " + memoryDir, e);
        }
    }

    @Override
    public void addUserMessage(String sessionId, String content) {
        List<MemoryMessage> messages = loadMessages(sessionId);
        int sequence = messages.isEmpty() ? 1 : messages.get(messages.size() - 1).getSequence() + 1;
        MemoryMessage msg = new MemoryMessage(sessionId, MemoryMessage.Role.USER, content);
        msg.setSequence(sequence);
        messages.add(msg);
        saveMessages(sessionId, messages);
    }

    @Override
    public void addAssistantMessage(String sessionId, String content, List<AgentStep> steps,
                                    String reasoningContent, ChatUsage usage, Long durationMs) {
        List<MemoryMessage> messages = loadMessages(sessionId);
        int sequence = messages.isEmpty() ? 1 : messages.get(messages.size() - 1).getSequence() + 1;
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
        messages.add(msg);
        saveMessages(sessionId, messages);
    }

    @Override
    public List<MemoryMessage> getRecentMessages(String sessionId, int limit) {
        List<MemoryMessage> messages = loadMessages(sessionId);
        int from = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    @Override
    public List<MemoryMessage> getSessionHistory(String sessionId) {
        return new ArrayList<>(loadMessages(sessionId));
    }

    @Override
    public List<AgentStep> getMessageSteps(String messageId) {
        // 遍历所有会话查找消息
        for (List<MemoryMessage> messages : cache.values()) {
            for (MemoryMessage msg : messages) {
                if (msg.getId().equals(messageId) && msg.getRole() == MemoryMessage.Role.ASSISTANT) {
                    return msg.getSteps();
                }
            }
        }
        // 从文件查找
        try (var paths = Files.list(memoryDir)) {
            for (Path file : paths.toList()) {
                if (file.toString().endsWith(".json")) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    SessionData data = JSON.parseObject(json, SessionData.class);
                    if (data != null && data.messages != null) {
                        for (MemoryMessage msg : data.messages) {
                            if (msg.getId().equals(messageId) && msg.getRole() == MemoryMessage.Role.ASSISTANT) {
                                return msg.getSteps();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("查找消息步骤失败", e);
        }
        return null;
    }

    @Override
    public void clearSession(String sessionId) {
        cache.remove(sessionId);
        summaryCache.remove(sessionId);
        
        // 删除消息文件
        Path messageFile = getSessionFile(sessionId);
        try {
            Files.deleteIfExists(messageFile);
        } catch (IOException e) {
            logger.error("删除会话文件失败: {}", messageFile, e);
        }
        
        // 删除摘要文件
        Path summaryFile = getSummaryFile(sessionId);
        try {
            Files.deleteIfExists(summaryFile);
        } catch (IOException e) {
            logger.error("删除摘要文件失败: {}", summaryFile, e);
        }
    }

    @Override
    public List<String> getAllSummaries(String sessionId) {
        List<SummaryEntry> entries = loadSummaries(sessionId);
        return entries.stream().map(e -> e.summary).toList();
    }

    @Override
    public Set<String> getSummarizedMessageIds(String sessionId) {
        List<SummaryEntry> entries = loadSummaries(sessionId);
        Set<String> result = new HashSet<>();
        for (SummaryEntry entry : entries) {
            if (entry.summarizedMessageIds != null) {
                result.addAll(entry.summarizedMessageIds);
            }
        }
        return result;
    }

    @Override
    public void saveSummary(String sessionId, String summary, List<String> summarizedMessageIds,
                            int tokenBefore, int tokenAfter) {
        List<SummaryEntry> entries = loadSummaries(sessionId);
        SummaryEntry entry = new SummaryEntry();
        entry.summary = summary;
        entry.summarizedMessageIds = summarizedMessageIds;
        entry.tokenBefore = tokenBefore;
        entry.tokenAfter = tokenAfter;
        entry.createdAt = System.currentTimeMillis();
        entries.add(entry);
        saveSummaries(sessionId, entries);
    }

    @Override
    public List<MemoryMessage> getMessagesAfterId(String sessionId, String afterMessageId) {
        List<MemoryMessage> messages = loadMessages(sessionId);
        boolean found = false;
        List<MemoryMessage> result = new ArrayList<>();
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

    // ========== 内部方法 ==========

    private Path getSessionFile(String sessionId) {
        return memoryDir.resolve(sessionId + ".json");
    }

    private Path getSummaryFile(String sessionId) {
        return memoryDir.resolve(sessionId + "_summaries.json");
    }

    private List<MemoryMessage> loadMessages(String sessionId) {
        return cache.computeIfAbsent(sessionId, id -> {
            Path file = getSessionFile(id);
            if (!Files.exists(file)) {
                return new ArrayList<>();
            }
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                SessionData data = JSON.parseObject(json, SessionData.class);
                return data != null && data.messages != null ? new ArrayList<>(data.messages) : new ArrayList<>();
            } catch (IOException e) {
                logger.error("读取会话文件失败: {}", file, e);
                return new ArrayList<>();
            }
        });
    }

    private void saveMessages(String sessionId, List<MemoryMessage> messages) {
        Path file = getSessionFile(sessionId);
        SessionData data = new SessionData();
        data.sessionId = sessionId;
        data.messages = messages;
        try {
            Files.writeString(file, JSON.toJSONString(data, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("保存会话文件失败: {}", file, e);
        }
    }

    private List<SummaryEntry> loadSummaries(String sessionId) {
        return summaryCache.computeIfAbsent(sessionId, id -> {
            Path file = getSummaryFile(id);
            if (!Files.exists(file)) {
                return new ArrayList<>();
            }
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                SummaryData data = JSON.parseObject(json, SummaryData.class);
                return data != null && data.summaries != null ? new ArrayList<>(data.summaries) : new ArrayList<>();
            } catch (IOException e) {
                logger.error("读取摘要失败: {}", file, e);
                return new ArrayList<>();
            }
        });
    }

    private void saveSummaries(String sessionId, List<SummaryEntry> summaries) {
        // 保存摘要到独立文件
        Path file = getSummaryFile(sessionId);
        SummaryData data = new SummaryData();
        data.sessionId = sessionId;
        data.summaries = summaries;
        try {
            Files.writeString(file, JSON.toJSONString(data, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("保存摘要失败: {}", file, e);
        }
    }

    // ========== 内部数据结构 ==========

    private static class SessionData {
        public String sessionId;
        public List<MemoryMessage> messages;
    }

    private static class SummaryData {
        public String sessionId;
        public List<SummaryEntry> summaries;
    }

    private static class SummaryEntry {
        public String summary;
        public List<String> summarizedMessageIds;
        public int tokenBefore;
        public int tokenAfter;
        public long createdAt;
    }
}
