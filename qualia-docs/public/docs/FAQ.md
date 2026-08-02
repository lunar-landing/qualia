# 常见问题

## 模型相关

### 如何切换不同的模型？

通过 `modelName()` 方法设置即可，无需修改业务代码：

```java
DashscopeChatModel model = new DashscopeChatModel("sk-xxx");
model.modelName("qwen3.7-plus");  // 切换模型
```

### 支持哪些模型厂商？

目前内置 Dashscope 实现，支持通义千问系列模型。由于框架采用统一接口设计，接入其他厂商只需实现 `ChatModel` 接口即可。

### 流式输出时如何获取完整内容？

订阅 `Flux<ChatResponse>` 流，逐步拼接每个 chunk 的内容：

```java
StringBuilder fullContent = new StringBuilder();
model.chatStream("你好").subscribe(
    chunk -> {
        String content = chunk.getChoices().get(0).getMessage().getContent();
        fullContent.append(content);
    }
);
```

## 智能代理相关

### 单模型和双模式有什么区别？

- **单模型**：推理和对话使用同一个模型，适合简单场景
- **双模型**：推理模型负责思考和选工具，对话模型负责生成回答，适合复杂推理场景

### 智能体陷入无限循环怎么办？

设置 `maxIterations` 限制最大推理轮次：

```java
agent.setMaxIterations(10);  // 最多推理10轮
```

### 如何让智能体记住上下文？

创建智能体时注入 Memory 实例，智能体会自动管理会话历史：

```java
Memory memory = new SessionMemory(dataSource);
ReActAgent agent = new ReActAgent(chatModel, memory);
```

## 工具系统相关

### 注解方式和继承方式怎么选？

- **注解方式**（推荐）：零侵入，只需在方法上加 `@AsFunctionTool`，适合大多数场景
- **继承方式**：需要完全控制工具行为时使用

### 工具执行失败会怎样？

智能体会捕获工具执行异常，将错误信息作为 Observation 反馈给模型，模型会尝试其他方案或告知用户。

### 如何接入 MCP 远程工具？

配置 MCP 服务器参数，智能体会自动发现并注册远程工具：

```java
McpClientParameters params = McpClientParameters.httpSse("https://mcp.example.com")
    .withName("remote-tools");
agent.addMcpClient(params);
```

## 记忆系统相关

### 不同会话之间会互相影响吗？

不会。记忆系统通过 sessionId 隔离，不同会话的消息完全独立。

### 如何清理会话历史？

调用 `clearSession()` 方法即可清空指定会话的所有消息：

```java
memory.clearSession("session-1");
```

### 历史数据太多会影响性能吗？

可以使用 `getRecentMessages()` 限制上下文窗口大小，避免传入过多历史消息：

```java
List<ChatMessage> recent = memory.getRecentMessages("session-1", 10);
```
