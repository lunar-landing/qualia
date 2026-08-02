# 记忆

## 概述

记忆系统负责管理会话历史、追踪 Token 用量、构建对话上下文。框架提供了统一的 Memory 接口抽象和基于数据库的 JdbcMemory 持久化实现，让智能体具备跨轮次的上下文感知能力。

### 设计理念

记忆系统采用**会话隔离**的设计，每个会话通过 sessionId 独立管理消息历史。框架支持 SQLite 和 MySQL 等多种数据源，完整保存推理步骤、Token 用量和建议问题，支持事后分析和成本监控。智能体在调用前自动从记忆获取历史构建上下文，调用后自动存入新消息——开发者无需手动管理。

### 消息模型

每条记忆消息（`MemoryMessage`）包含以下核心字段：

| 字段 | 说明 |
|------|------|
| `role` | 角色：`USER` 或 `ASSISTANT` |
| `content` | 消息内容 |
| `steps` | ReAct 推理步骤列表（仅 ASSISTANT） |
| `suggestions` | 建议的后续问题列表 |
| `promptTokens` / `completionTokens` | Token 用量统计 |
| `durationMs` | 响应耗时（毫秒） |

框架自动处理 Java 对象与数据库 JSON 字段之间的序列化/反序列化，开发者只需操作类型安全的 Java 对象。

## 快速开始

### 初始化

```java
// SQLite 数据源
DataSource dataSource = new SQLiteDataSource();
((SQLiteDataSource) dataSource).setUrl("jdbc:sqlite:memory.db");

// 或 MySQL 数据源
MysqlDataSource dataSource = new MysqlDataSource();
dataSource.setUrl("jdbc:mysql://localhost:3306/qualia");

// 创建记忆实例
Memory memory = new JdbcMemory(dataSource);
```

### 集成到智能体

```java
// 创建智能体（注入记忆）
ReActAgent agent = new ReActAgent(chatModel, memory);

// 智能体会自动管理记忆：
// - 调用前：获取历史消息构建上下文
// - 调用后：存入用户消息和AI回复，记录推理步骤和Token用量
AgentResponse response = agent.call("session-1", "你好");
```

### 多会话管理

```java
// 不同用户/场景使用不同sessionId
String sessionId = "chat-user123-" + System.currentTimeMillis();

agent.call(sessionId, "我想查询保单信息");
agent.call(sessionId, "保单号是ABC123");  // 自动记住上文
```

## 核心能力

### 消息管理

记忆系统自动记录每轮对话，包括用户输入、AI回复、推理步骤和建议问题：

```java
// 获取最近10条消息（用于构建LLM上下文）
List<MemoryMessage> recent = memory.getRecentMessages("session-1", 10);

// 获取完整会话历史（用于前端展示）
List<MemoryMessage> history = memory.getSessionHistory("session-1");

// 获取某条回复的推理过程
List<AgentStep> steps = memory.getMessageSteps("message-id-123");
```

### Token 用量追踪

每条 ASSISTANT 消息都记录了 Token 用量和响应耗时，便于成本监控：

```java
List<MemoryMessage> history = memory.getSessionHistory("session-1");

for (MemoryMessage msg : history) {
    if (msg.getRole() == MemoryMessage.Role.ASSISTANT) {
        System.out.println("输入Token: " + msg.getPromptTokens());
        System.out.println("输出Token: " + msg.getCompletionTokens());
        System.out.println("耗时: " + msg.getDurationMs() + "ms");
    }
}
```

### 推理步骤回溯

可以查看智能体每一步的推理过程，便于调试和优化：

```java
List<AgentStep> steps = memory.getMessageSteps("assistant-msg-123");

for (AgentStep step : steps) {
    // 思考 / 行动 / 观察
    System.out.println(step.getType() + ": " + step.getContent());
}
```

## 最佳实践

1. **会话ID设计**：使用有意义的格式（如 `用户ID+时间戳`），便于追踪和调试
2. **定期清理**：长期运行的系统应定期清理过期会话，避免数据膨胀
3. **Token 监控**：利用内置的用量追踪监控模型调用成本
4. **步骤分析**：通过推理步骤数据发现模型行为问题，优化 Agent 表现

## 注意事项

- SQLite 适合单机部署，分布式场景建议使用 MySQL
- 大量历史数据可能影响查询性能，建议定期归档
- Token 计数因模型而异，注意兼容性
- 深度思考（Reasoning Content）默认禁用，需在 ChatModel 中启用
