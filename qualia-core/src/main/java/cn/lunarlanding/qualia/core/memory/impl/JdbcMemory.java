package cn.lunarlanding.qualia.core.memory.impl;

import com.alibaba.fastjson.JSON;
import cn.lunarlanding.qualia.core.agent.spec.AgentStep;
import cn.lunarlanding.qualia.core.memory.MemoryMessage;
import cn.lunarlanding.qualia.core.model.chat.ChatUsage;
import cn.lunarlanding.qualia.core.memory.Memory;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于MySQL的会话级记忆实现
 * 支持多会话隔离和持久化存储
 */
public class JdbcMemory implements Memory {

    private final DataSource dataSource;
    private final QueryRunner queryRunner;

    // 会话级序列号缓存（sessionId -> 下一个sequence）
    private final ConcurrentHashMap<String, Integer> sequenceCache = new ConcurrentHashMap<>();

    public JdbcMemory(DataSource dataSource) {
        this.dataSource = dataSource;
        this.queryRunner = new QueryRunner(dataSource);
        initTable();
    }

    /**
     * 初始化数据库表
     */
    private void initTable() {
        String createTableSql =
            "CREATE TABLE IF NOT EXISTS chat_message (" +
            "id VARCHAR(64) PRIMARY KEY," +
            "session_id VARCHAR(64) NOT NULL," +
            "role VARCHAR(20) NOT NULL," +
            "content TEXT NOT NULL," +
            "steps_json LONGTEXT," +
            "suggestions_json TEXT," +
            "created_at BIGINT NOT NULL," +
            "sequence_num INT NOT NULL" +
            ")";

        String createIndexSql =
            "CREATE INDEX idx_session_seq ON chat_message(session_id, sequence_num)";

        String createSummaryTableSql =
            "CREATE TABLE IF NOT EXISTS chat_message_summary (" +
            "id VARCHAR(64) PRIMARY KEY," +
            "session_id VARCHAR(64) NOT NULL," +
            "summary_content TEXT NOT NULL," +
            "summarized_message_ids TEXT NOT NULL," +
            "token_before INT," +
            "token_after INT," +
            "created_at BIGINT NOT NULL" +
            ")";

        String createSummaryIndexSql =
            "CREATE INDEX idx_summary_session_id ON chat_message_summary(session_id)";

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(createTableSql);
            try {
                conn.createStatement().execute(createIndexSql);
            } catch (SQLException e) {
                // 索引已存在时忽略错误
                if (!e.getMessage().contains("Duplicate") && !e.getMessage().contains("already exists")) {
                    throw e;
                }
            }
            // 增量添加 token 用量字段（兼容已有表）
            safeAddColumn(conn, "prompt_tokens", "INT");
            safeAddColumn(conn, "completion_tokens", "INT");
            safeAddColumn(conn, "total_tokens", "INT");
            safeAddColumn(conn, "duration_ms", "BIGINT");

            // 初始化摘要表
            conn.createStatement().execute(createSummaryTableSql);
            try {
                conn.createStatement().execute(createSummaryIndexSql);
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate") && !e.getMessage().contains("already exists")) {
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("初始化记忆表失败", e);
        }
    }

    private void safeAddColumn(Connection conn, String columnName, String columnType) {
        try {
            conn.createStatement().execute(
                "ALTER TABLE chat_message ADD COLUMN " + columnName + " " + columnType);
        } catch (SQLException e) {
            // 列已存在时忽略
        }
    }

    @Override
    public void addUserMessage(String sessionId, String content) {
        int seq = getNextSequence(sessionId);
        String sql = "INSERT INTO chat_message (id, session_id, role, content, steps_json, created_at, sequence_num) " +
                     "VALUES (?, ?, ?, ?, NULL, ?, ?)";

        try {
            queryRunner.update(sql,
                UUID.randomUUID().toString(),
                sessionId,
                MemoryMessage.Role.USER.name(),
                content,
                System.currentTimeMillis(),
                seq
            );
        } catch (SQLException e) {
            throw new RuntimeException("保存用户消息失败", e);
        }
    }

    @Override
    public void addAssistantMessage(String sessionId, String content, List<AgentStep> steps, String reasoningContent, ChatUsage usage, Long durationMs) {
        int seq = getNextSequence(sessionId);
        String stepsJson = steps != null ? JSON.toJSONString(steps) : null;

        String sql = "INSERT INTO chat_message (id, session_id, role, content, steps_json, reasoning_content, prompt_tokens, completion_tokens, total_tokens, duration_ms, created_at, sequence_num) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Integer promptTokens = usage != null ? usage.getPromptTokens() : null;
        Integer completionTokens = usage != null ? usage.getCompletionTokens() : null;
        Integer totalTokens = usage != null ? usage.getTotalTokens() : null;

        try {
            queryRunner.update(sql,
                UUID.randomUUID().toString(),
                sessionId,
                MemoryMessage.Role.ASSISTANT.name(),
                content,
                stepsJson,
                reasoningContent,
                promptTokens,
                completionTokens,
                totalTokens,
                durationMs,
                System.currentTimeMillis(),
                seq
            );
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            throw new RuntimeException("保存AI消息失败", e);
        }
    }

    @Override
    public List<MemoryMessage> getRecentMessages(String sessionId, int limit) {
        String sql = "SELECT id, session_id as sessionId, role, content, steps_json as stepsJson, " +
                     "suggestions_json as suggestionsJson, reasoning_content as reasoningContent, " +
                     "prompt_tokens as promptTokens, completion_tokens as completionTokens, " +
                     "total_tokens as totalTokens, duration_ms as durationMs, feedback, " +
                     "created_at as createdAt, sequence_num as sequence " +
                     "FROM chat_message WHERE session_id = ? " +
                     "ORDER BY sequence_num DESC LIMIT ?";

        try {
            List<MemoryMessage> messages = queryRunner.query(sql,
                new BeanListHandler<>(MemoryMessage.class), sessionId, limit);

            // 反转回正序
            Collections.reverse(messages);
            return messages;
        } catch (SQLException e) {
            throw new RuntimeException("查询消息失败", e);
        }
    }

    @Override
    public List<MemoryMessage> getSessionHistory(String sessionId) {
        String sql = "SELECT id, session_id as sessionId, role, content, steps_json as stepsJson, " +
                     "suggestions_json as suggestionsJson, reasoning_content as reasoningContent, " +
                     "prompt_tokens as promptTokens, completion_tokens as completionTokens, " +
                     "total_tokens as totalTokens, duration_ms as durationMs, feedback, " +
                     "created_at as createdAt, sequence_num as sequence " +
                     "FROM chat_message WHERE session_id = ? " +
                     "ORDER BY sequence_num ASC";

        try {
            List<MemoryMessage> messages = queryRunner.query(sql,
                new BeanListHandler<>(MemoryMessage.class), sessionId);

            return messages;
        } catch (SQLException e) {
            throw new RuntimeException("查询会话历史失败", e);
        }
    }

    @Override
    public List<AgentStep> getMessageSteps(String messageId) {
        String sql = "SELECT steps_json FROM chat_message WHERE id = ? AND role = 'ASSISTANT'";

        try {
            String stepsJson = queryRunner.query(sql, new ScalarHandler<String>(), messageId);

            if (stepsJson == null) {
                return null;
            }

            return JSON.parseArray(stepsJson, AgentStep.class);
        } catch (SQLException e) {
            throw new RuntimeException("查询消息步骤失败", e);
        }
    }

    @Override
    public void clearSession(String sessionId) {
        String sql = "DELETE FROM chat_message WHERE session_id = ?";

        try {
            queryRunner.update(sql, sessionId);
            sequenceCache.remove(sessionId);
        } catch (SQLException e) {
            throw new RuntimeException("清空会话失败", e);
        }
    }

    @Override
    public List<String> getAllSummaries(String sessionId) {
        String sql = "SELECT summary_content FROM chat_message_summary WHERE session_id = ? ORDER BY created_at ASC";

        try {
            return queryRunner.query(sql, new ColumnListHandler<String>(), sessionId);
        } catch (SQLException e) {
            throw new RuntimeException("查询摘要失败", e);
        }
    }

    @Override
    public java.util.Set<String> getSummarizedMessageIds(String sessionId) {
        String sql = "SELECT summarized_message_ids FROM chat_message_summary WHERE session_id = ?";
        java.util.Set<String> result = new java.util.HashSet<>();

        try {
            List<String> jsonList = queryRunner.query(sql, new ColumnListHandler<String>(), sessionId);
            for (String json : jsonList) {
                if (json != null && !json.isEmpty()) {
                    List<String> ids = JSON.parseArray(json, String.class);
                    result.addAll(ids);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询已压缩消息ID失败", e);
        }

        return result;
    }

    @Override
    public void saveSummary(String sessionId, String summary, List<String> summarizedMessageIds, int tokenBefore, int tokenAfter) {
        String sql = "INSERT INTO chat_message_summary (id, session_id, summary_content, summarized_message_ids, token_before, token_after, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {
            queryRunner.update(sql,
                UUID.randomUUID().toString(),
                sessionId,
                summary,
                JSON.toJSONString(summarizedMessageIds),
                tokenBefore,
                tokenAfter,
                System.currentTimeMillis()
            );
        } catch (SQLException e) {
            throw new RuntimeException("保存摘要失败", e);
        }
    }

    @Override
    public List<MemoryMessage> getMessagesAfterId(String sessionId, String afterMessageId) {
        String sql = "SELECT id, session_id as sessionId, role, content, steps_json as stepsJson, " +
                     "suggestions_json as suggestionsJson, reasoning_content as reasoningContent, " +
                     "prompt_tokens as promptTokens, completion_tokens as completionTokens, " +
                     "total_tokens as totalTokens, duration_ms as durationMs, feedback, " +
                     "created_at as createdAt, sequence_num as sequence " +
                     "FROM chat_message WHERE session_id = ? " +
                     "AND sequence_num > (SELECT sequence_num FROM chat_message WHERE id = ?) " +
                     "ORDER BY sequence_num ASC";

        try {
            return queryRunner.query(sql,
                new BeanListHandler<>(MemoryMessage.class), sessionId, afterMessageId);
        } catch (SQLException e) {
            throw new RuntimeException("查询消息失败", e);
        }
    }

    /**
     * 更新消息反馈
     */
    public void updateFeedback(String messageId, String feedback) {
        String sql = "UPDATE chat_message SET feedback = ? WHERE id = ?";
        try {
            queryRunner.update(sql, feedback, messageId);
        } catch (SQLException e) {
            throw new RuntimeException("更新反馈失败", e);
        }
    }

    /**
     * 获取下一个序列号
     */
    private int getNextSequence(String sessionId) {
        Integer cached = sequenceCache.get(sessionId);
        if (cached != null) {
            int next = cached + 1;
            sequenceCache.put(sessionId, next);
            return next;
        }

        // 从数据库查询当前最大序列号
        try {
            String sql = "SELECT MAX(sequence_num) FROM chat_message WHERE session_id = ?";
            Integer maxSeq = queryRunner.query(sql, new ScalarHandler<Integer>(), sessionId);
            int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
            sequenceCache.put(sessionId, nextSeq);
            return nextSeq;
        } catch (SQLException e) {
            throw new RuntimeException("获取序列号失败", e);
        }
    }

}
