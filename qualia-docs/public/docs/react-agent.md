# 智能代理

## 概述

智能代理是 Qualia 框架的核心组件，负责接收用户输入、调用大模型推理、执行工具并返回结果。框架采用 ReAct（Reasoning + Acting）模式，让智能体在"思考-行动-观察"的循环中完成复杂任务。

### 设计理念

智能代理采用**单模型 + 格式切换**架构，通过调用时指定 `ResponseFormatType` 区分输出格式：推理阶段使用 `JSON_OBJECT` 格式进行结构化思考和工具调用，最终回答阶段使用 `TEXT` 格式生成自然语言。这种设计简化了配置，同时保持了灵活性。

## 实现类

框架提供两个智能代理实现类：

| 类名 | 说明 | 适用场景 |
|------|------|----------|
| `ReActAgent` | 基础实现，需要手动配置工具和记忆 | 需要精细控制的场景 |
| `HarnessAgent` | 开箱即用的封装，自动配置常用工具 | 快速开发、本地开发场景 |

### ReActAgent

`ReActAgent` 是智能代理的核心实现，提供完整的 ReAct 推理循环能力。需要手动注册工具和配置记忆。

### HarnessAgent

`HarnessAgent` 是 `ReActAgent` 的浅封装，继承自 `ReActAgent`，在构造时自动完成以下配置：

- **文件操作工具**：ReadTool、GrepTool、GlobTool、ReplaceTool、WriteTool、BashTool
- **网络工具**：WebFetchTool、BaiduSearchTool、HttpTool
- **记忆实现**：自动使用 `JsonMemory` 持久化存储
- **系统提示词**：从工作区 `AGENT.md` 文件加载
- **技能加载**：从工作区 `skills` 目录自动加载

```java
// HarnessAgent 一行代码即可使用
Workspace workspace = new LocalWorkspace("/path/to/project");
HarnessAgent agent = new HarnessAgent(chatModel, workspace);
```

## 快速开始

### 创建智能体

```java
// 使用 ReActAgent（手动配置）
ReActAgent agent = new ReActAgent(chatModel);
ReActAgent agent = new ReActAgent(chatModel, memory);

// 使用 HarnessAgent（开箱即用）
Workspace workspace = new LocalWorkspace("/path/to/project");
HarnessAgent agent = new HarnessAgent(chatModel, workspace);
```

### 配置智能体

```java
agent.name("数据分析专家");
agent.setSystemPrompt("你是一个专业的数据分析助手。");
agent.setMaxIterations(10);  // 防止无限循环
agent.setSuggestionsEnabled(true);  // 启用建议问题功能
```

### 执行任务

```java
// 同步调用
AgentResponse response = agent.call("session-1", "分析2024年Q1销售数据");
System.out.println("结果：" + response.getAnswer());

// 流式调用（实时返回推理过程）
Flux<AgentResponse> stream = agent.callStream("session-1", "解释量子计算");
stream.subscribe(step -> System.out.print(step.getAnswer()));
```

## ReAct 推理循环

智能体的推理过程遵循 ReAct 模式，在"思考-行动-观察"的循环中逐步解决问题：

```
用户输入 → [思考] 分析问题，制定计划
         → [行动] 调用工具执行操作
         → [观察] 获取工具返回结果
         → [思考] 分析结果，判断是否完成
         → ...（循环直到得到最终答案）
         → 返回最终回答
```

循环会在以下情况终止：模型判断已获得足够信息并给出最终答案，或达到最大迭代次数。

## 工具集成

智能体可以注册多种工具来扩展能力：

```java
// 注册注解工具（推荐）
agent.addTools(new WeatherService());

// 注册单个工具
agent.addTool(searchTool);

// 注册子智能体作为工具（多智能体协作）
agent.addSubAgent(researchAgent);

// 注册 MCP 远程工具
agent.addMcpClient(mcpParams);
```

工具的详细定义方式请参考[工具系统](./function-tool.md)。

## MCP 集成

智能体可以通过 `McpClient` 接入远程 MCP 服务器，自动发现并注册工具。

### 快速接入

```java
// 配置 MCP 服务器
McpClientParameters params = McpClientParameters.streamableHttp("https://your-server.com/mcp")
    .withName("my-server")
    .withHeader("Authorization", "Bearer your-token")
    .withConnectTimeout(60);

// 创建智能体并接入 MCP
ReActAgent agent = new ReActAgent(chatModel, memory);
McpClient client = agent.addMcpClient(params);

// 使用智能体（MCP 工具已自动注册）
AgentResponse response = agent.call("session-1", "使用工具查询信息");
client.close();
```

### try-with-resources 模式

```java
ReActAgent agent = new ReActAgent(chatModel, memory);

try (McpClient client = agent.addMcpClient(params)) {
    AgentResponse response = agent.call("session-1", "查询信息");
    System.out.println(response.getAnswer());
} // 自动关闭连接
```

### 多服务器接入

```java
ReActAgent agent = new ReActAgent(chatModel, memory);

McpClient client1 = agent.addMcpClient(params1);  // 服务器 1
McpClient client2 = agent.addMcpClient(params2);  // 服务器 2

// 所有服务器的工具都已注册
AgentResponse response = agent.call("session-1", "综合查询");
```

详细的 MCP 客户端配置请参考 [MCP 客户端](/docs/mcp-client)。

## 技能系统

智能体支持技能（Skill）机制，技能是一组可复用的脚本和资源：

```java
// 添加技能
agent.addSkill(mySkill);

// 查找技能
Skill skill = agent.findSkill("query-data-skill");
```

技能加载后，智能体会自动在系统提示词中注册可用技能列表。详细的技能定义方式请参考[技能系统](./skill.md)。

## 响应结构

`AgentResponse` 包含智能体执行的完整结果：

| 字段 | 说明 |
|------|------|
| `answer` | 最终回答 |
| `steps` | 推理步骤列表（思考/行动/观察） |
| `reasoningContent` | 深度思考内容（推理模型专用） |
| `suggestions` | 建议的后续问题（启用时自动生成） |
| `usage` | Token 用量统计 |
| `durationMs` | 响应总耗时 |

### 响应类型

流式调用时，每个 `AgentResponse` 包含 `responseType` 字段标识类型：

| 类型 | 说明 |
|------|------|
| `step` | 推理步骤（思考/行动/观察） |
| `answer` | 最终回答（可累积） |
| `suggestions` | 建议问题列表 |

## 最佳实践

1. **明确角色定位**：通过系统提示词清晰定义智能体的职责和能力边界
2. **工具设计精简**：每个工具功能单一、描述清晰，便于模型准确选择
3. **设置迭代上限**：合理配置 `maxIterations`，防止无限循环消耗资源
4. **善用流式输出**：需要实时反馈的场景优先使用 `callStream()`
5. **启用建议问题**：通过 `setSuggestionsEnabled(true)` 提升用户体验
