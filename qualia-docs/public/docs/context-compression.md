# 记忆压缩

## 概述

记忆压缩用于管理智能体的对话历史长度，通过两种机制降低 token 消耗：

1. **工具裁剪（Layer 1）**：零 LLM 成本，清理旧的工具调用结果
2. **摘要压缩（Layer 2）**：使用 LLM 将旧消息压缩为结构化摘要

## 配置参数

### 摘要压缩配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| keepRecentRounds | 10 | 保留最近多少轮对话（每轮 = 1条用户 + 1条助手） |
| compressBatchSize | 4 | 每次压缩的对话轮数（每轮 = 2条消息） |
| maxSummaries | 3 | 最多保留摘要数量 |
| enableSummaryCompression | true | 是否启用摘要压缩 |

### 工具裁剪配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| keepRecentToolResults | 3 | 保留最近多少个工具调用结果 |
| enableToolTrimming | false | 是否启用工具结果裁剪 |

## 运行逻辑

### 工具裁剪（Layer 1）

工具裁剪是零 LLM 成本的压缩机制，用于减少工具调用结果的 token 消耗：

1. 从消息列表末尾向前遍历
2. 统计 OBSERVATION 类型的步骤（工具调用结果）
3. 保留最近 K 个工具结果（默认 K=3）
4. 更早的工具结果替换为占位符：`[工具结果已清理，如需查看请重新调用]`

```
启用条件：enableToolTrimming = true
保留数量：keepRecentToolResults = 3
执行位置：在上下文组装完成之后
```

### 触发条件

```
未压缩消息数 = 所有消息数 - 已压缩消息数
keepCount = keepRecentRounds * 2  // 20 条

if 未压缩消息数 >= keepCount:
    触发压缩
```

### 压缩流程

1. 获取已压缩消息 ID 集合
2. 从历史消息中过滤出未压缩消息
3. 如果未压缩消息 ≥ 20 条，压缩最早的 8 条（4 轮）
4. 调用 LLM 生成结构化摘要
5. 存储摘要，记录被压缩的消息 ID

### 整体执行顺序

```
getContextMessages(sessionId)
    │
    ├── 1. compressIfNeeded(sessionId)      // 摘要压缩（Layer 2）
    │       ├── 检查未压缩消息数量
    │       └── 如果 ≥ 20 条，压缩最早的 8 条
    │
    ├── 2. buildContext(sessionId)          // 构建上下文
    │       ├── 获取摘要（最多 3 个）
    │       ├── 获取最近未压缩消息
    │       └── 组装上下文列表
    │
    └── 3. trimToolResults(context)         // 工具裁剪（Layer 1）
            ├── 从末尾向前遍历
            ├── 保留最近 K 个工具结果
            └── 更早的替换为占位符
```

### 上下文组装

```
上下文 = [历史摘要1] + [历史摘要2] + ... + [历史摘要N] + [最近未压缩消息] + [系统提示词] + [用户输入]
```

摘要作为单独的 ASSISTANT 消息注入，前缀为 `[历史摘要]`。

工具裁剪在组装完成后执行，对已组装的上下文列表进行工具结果清理（如果启用）。

### 核心特性

#### 工具裁剪特性

- **零 LLM 成本** - 纯内存操作，不调用大模型
- **智能保留** - 保留最近 K 个工具结果，更早的自动清理
- **按需恢复** - 清理的工具结果可通过重新调用恢复
- **开关控制** - 通过 enableToolTrimming 配置开关

#### 摘要压缩特性

- **按数量触发** - 基于未压缩消息数量而非 token 触发
- **增量压缩** - 只压缩未处理的消息，不重复压缩
- **独立摘要** - 每次压缩生成独立摘要，不合并旧摘要
- **数量控制** - 最多保留 3 个摘要，通过获取时控制数量，不删除旧摘要
- **自动触发** - 未压缩消息超过阈值时自动执行

## 存储实现

### JsonMemory（本地文件）

消息和摘要分离存储：

```
.qualia/memory/
├── {sessionId}.json              # 消息文件
└── {sessionId}_summaries.json    # 摘要文件
```

消息文件结构：
```json
{
  "sessionId": "xxx",
  "messages": [...]
}
```

摘要文件结构：
```json
{
  "sessionId": "xxx",
  "summaries": [
    {
      "summary": "摘要内容",
      "summarizedMessageIds": ["msg1", "msg2"],
      "tokenBefore": 2000,
      "tokenAfter": 500,
      "createdAt": 1721462400000
    }
  ]
}
```

### JdbcMemory（数据库）

消息和摘要分离存储：

```sql
-- 消息表
CREATE TABLE chat_message (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT,
  created_at BIGINT NOT NULL
);

-- 摘要表
CREATE TABLE chat_message_summary (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  summary_content TEXT NOT NULL,
  summarized_message_ids TEXT NOT NULL,
  token_before INT,
  token_after INT,
  created_at BIGINT NOT NULL
);
```