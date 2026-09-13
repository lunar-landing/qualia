package cn.lunarlanding.qualia.core.memory.impl;

import com.alibaba.fastjson.JSON;
import cn.lunarlanding.qualia.core.agent.spec.AgentStep;
import cn.lunarlanding.qualia.core.memory.AttachmentRef;
import cn.lunarlanding.qualia.core.memory.Memory;
import cn.lunarlanding.qualia.core.memory.MemoryMessage;
import cn.lunarlanding.qualia.core.model.chat.ChatUsage;
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
 * 用于本地 CLI 开发场景，数据存储在 workspace/.qualia/sessions/ 目录下（每会话一个以会话 id 命名的子目录：
 * session.json 聊天记录 / summaries.json 压缩摘要，附件等其他会话资源同域聚合）
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
            migrateLegacyMemoryDir();
            migrateLegacyLayout();
        } catch (IOException e) {
            throw new RuntimeException("创建 memory 目录失败: " + memoryDir, e);
        }
    }

    /**
     * 旧版记忆根目录迁移（幂等）：{memoryDir 旁的 memory/} → {memoryDir}。
     * qualia-code 旧版会话数据存放在 .qualia/memory/，新版为 .qualia/sessions/，
     * 此处将旧目录内容整体搬入新目录后移除旧目录；
     * claw 的记忆目录自身名为 memory（旧目录即自身），天然跳过。
     */
    private void migrateLegacyMemoryDir() {
        String name = memoryDir.getFileName() == null ? "" : memoryDir.getFileName().toString();
        Path parent = memoryDir.getParent();
        if ("memory".equals(name) || parent == null) {
            return;
        }
        Path legacyDir = parent.resolve("memory");
        if (!Files.isDirectory(legacyDir)) {
            return;
        }
        try {
            try (var paths = Files.list(legacyDir)) {
                for (Path file : paths.toList()) {
                    Path target = memoryDir.resolve(file.getFileName());
                    if (Files.exists(target)) {
                        continue; // 已存在不覆盖（可能已迁移过）
                    }
                    Files.move(file, target);
                }
            }
            try (var paths = Files.list(legacyDir)) {
                if (paths.findAny().isEmpty()) {
                    Files.deleteIfExists(legacyDir);
                    logger.info("[JsonMemory] 旧版记忆目录已迁移: {} -> {}", legacyDir, memoryDir);
                }
            }
        } catch (IOException e) {
            logger.error("[JsonMemory] 旧版记忆目录迁移失败: {}", legacyDir, e);
        }
    }

    /**
     * 旧版扁平布局迁移（幂等，启动时执行一次）：
     * {memoryDir}/{sessionId}.json           → {memoryDir}/{sessionId}/session.json
     * {memoryDir}/{sessionId}_summaries.json → {memoryDir}/{sessionId}/summaries.json
     * 仅识别会话 id 命名的文件，搬完即删旧文件；新位置已存在时视为已迁移，仅清理旧文件
     */
    private void migrateLegacyLayout() {
        try (var paths = Files.list(memoryDir)) {
            for (Path file : paths.toList()) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file) || !name.endsWith(".json")) {
                    continue;
                }
                boolean summary = name.endsWith("_summaries.json");
                String sessionId = summary
                        ? name.substring(0, name.length() - "_summaries.json".length())
                        : name.substring(0, name.length() - ".json".length());
                if (!sessionId.matches("[A-Za-z0-9_-]+")) {
                    continue;
                }
                Path target = memoryDir.resolve(sessionId).resolve(summary ? "summaries.json" : "session.json");
                try {
                    Files.createDirectories(target.getParent());
                    Files.move(file, target);
                    logger.info("[JsonMemory] 旧版会话文件已迁移: {} -> {}/{}", name, sessionId, target.getFileName());
                } catch (java.nio.file.FileAlreadyExistsException e) {
                    // 新位置已存在（已迁移过），清理旧文件
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                } catch (IOException e) {
                    logger.error("[JsonMemory] 会话文件迁移失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            logger.error("[JsonMemory] 扫描旧版记忆布局失败", e);
        }
    }

    @Override
    public void addUserMessage(String sessionId, String content, List<AttachmentRef> attachments) {
        List<MemoryMessage> messages = loadMessages(sessionId);
        int sequence = messages.isEmpty() ? 1 : messages.get(messages.size() - 1).getSequence() + 1;
        MemoryMessage msg = new MemoryMessage(sessionId, MemoryMessage.Role.USER, content);
        msg.setAttachments(attachments);
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
            for (Path dir : paths.toList()) {
                Path file = dir.resolve("session.json");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
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
        } catch (IOException e) {
            logger.error("查找消息步骤失败", e);
        }
        return null;
    }

    @Override
    public void clearSession(String sessionId) {
        cache.remove(sessionId);
        summaryCache.remove(sessionId);
        
        // 删除会话目录内自己的文件（附件等其他会话资源由上层联动清理，此处仅移除空壳目录）
        Path sessionDir = getSessionDir(sessionId);
        try {
            Files.deleteIfExists(getSessionFile(sessionId));
            Files.deleteIfExists(getSummaryFile(sessionId));
            if (Files.isDirectory(sessionDir)) {
                try (var entries = Files.list(sessionDir)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(sessionDir);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("清理会话文件失败: {}", sessionDir, e);
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

    /** 会话目录：{memoryDir}/{sessionId}（聊天记录、摘要等会话资源同域聚合） */
    private Path getSessionDir(String sessionId) {
        return memoryDir.resolve(sessionId);
    }

    private Path getSessionFile(String sessionId) {
        return memoryDir.resolve(sessionId).resolve("session.json");
    }

    private Path getSummaryFile(String sessionId) {
        return memoryDir.resolve(sessionId).resolve("summaries.json");
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
            Files.createDirectories(file.getParent());
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
        // 保存摘要到会话目录内的独立文件
        Path file = getSummaryFile(sessionId);
        SummaryData data = new SummaryData();
        data.sessionId = sessionId;
        data.summaries = summaries;
        try {
            Files.createDirectories(file.getParent());
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
