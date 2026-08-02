# MCP 客户端

## 概述

MCP（Model Context Protocol）是一种标准化的协议，用于连接大语言模型与外部工具和服务。Qualia 框架原生支持 MCP 协议，可以轻松接入各种 MCP 服务器，自动发现并注册远程工具。

### 设计理念

MCP 集成采用**协议标准化、连接管理封装**的设计，让智能体可以无缝使用远程工具服务。框架支持多种传输方式（STDIO、Streamable HTTP、HTTP SSE），McpClient 返回 MCP 协议格式的工具，ReActAgent 负责将远程工具桥接为本地 FunctionTool，实现统一的调用接口。

### 支持的传输方式

| 传输方式 | 说明 | 适用场景 |
|----------|------|----------|
| STDIO | 标准输入输出 | 本地进程通信 |
| Streamable HTTP | 流式 HTTP 传输 | 远程服务（如 DashScope MCP） |
| HTTP SSE | 服务器发送事件 | 远程服务 |

## 快速开始

### 连接 MCP 服务器

```java
// 创建 MCP 服务器参数（Streamable HTTP）
McpClientParameters params = McpClientParameters.streamableHttp("https://your-mcp-server.com/mcp")
    .withName("my-mcp-server")
    .withHeader("Authorization", "Bearer your-api-key")
    .withConnectTimeout(60);

// 连接到 MCP 服务器
McpClient client = new McpClient(params);
connection.connect();

// 获取发现的工具（MCP 协议格式）
List<McpSchema.Tool> tools = client.getTools();
System.out.println("发现 " + tools.size() + " 个工具");
```

## 传输方式详解

### STDIO 传输

适用于本地进程通信，通过标准输入输出进行数据交换：

```java
McpClientParameters params = McpClientParameters.stdio("node", List.of("mcp-server.js"))
    .withName("local-mcp-server");
```

### Streamable HTTP 传输

适用于远程服务，如 DashScope MCP：

```java
McpClientParameters params = McpClientParameters.streamableHttp("https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp")
    .withName("dashscope-web-search")
    .withHeader("Authorization", "Bearer " + apiKey)
    .withConnectTimeout(60);
```

### HTTP SSE 传输

适用于支持服务器发送事件的远程服务：

```java
McpClientParameters params = McpClientParameters.httpSse("https://your-server.com/sse")
    .withName("sse-server")
    .withHeader("Authorization", "Bearer " + apiKey);
```

## 连接管理

### 生命周期管理

MCP 连接实现了 `AutoCloseable` 接口，推荐使用 try-with-resources 管理生命周期：

```java
try (McpClient client = new McpClient(params)) {
    client.connect();
    
    // 使用连接
    List<McpSchema.Tool> tools = client.getTools();
    McpSchema.CallToolResult result = client.callTool("tool-name", arguments);
    
} // 自动关闭连接
```

### 连接状态检查

```java
// 检查连接状态
if (client.isConnected()) {
    // 连接正常
}

// 获取连接参数
McpClientParameters params = client.getParams();
```

## 工具调用

### 直接调用远程工具

```java
// 调用远程 MCP 工具
Map<String, Object> arguments = Map.of("query", "搜索关键词");
McpSchema.CallToolResult result = client.callTool("search", arguments);

// 获取结果
String content = result.content().get(0).text();
```

### 通过智能体调用

```java
// 智能体会自动选择合适的工具（包括 MCP 工具）
AgentResponse response = agent.call("session-1", "帮我搜索最新的AI新闻");
System.out.println(response.getAnswer());
```

## 错误处理

### 连接异常

```java
try {
    connection.connect();
} catch (McpException e) {
    System.err.println("连接失败: " + e.getMessage());
}
```

### 工具调用异常

```java
try {
    McpSchema.CallToolResult result = client.callTool("tool-name", arguments);
} catch (McpException e) {
    System.err.println("工具调用失败: " + e.getMessage());
}
```

## 最佳实践

1. **使用 try-with-resources**：确保连接正确关闭，避免资源泄漏
2. **设置合理的超时时间**：根据网络环境调整 `connectTimeout`
3. **错误处理**：捕获 `McpException` 处理连接和调用异常
4. **连接复用**：对于频繁调用的场景，复用连接而不是每次都创建新连接
5. **工具发现**：连接后检查 `getTools()` 或 `getToolCount()` 确保工具已正确发现

## 示例代码

### 完整示例

```java
// 1. 配置 MCP 服务器
McpClientParameters params = McpClientParameters.streamableHttp("https://your-server.com/mcp")
    .withName("my-server")
    .withHeader("Authorization", "Bearer your-token")
    .withConnectTimeout(60);

// 2. 创建智能体并接入 MCP
ReActAgent agent = new ReActAgent(chatModel, memory);
agent.setSystemPrompt("你是一个智能助手，可以使用各种工具回答问题。");

try (McpClient client = agent.addMcpClient(params)) {
    // 3. 验证工具已注册
    List<McpSchema.Tool> mcpTools = client.getTools();
    System.out.println("已注册 " + mcpTools.size() + " 个 MCP 工具");
    
    // 4. 使用智能体
    AgentResponse response = agent.call("session-1", "使用工具查询信息");
    System.out.println("回答: " + response.getAnswer());
    
} // 自动关闭连接
```

### DashScope MCP 示例

```java
// 连接 DashScope Web Search MCP
McpClientParameters params = McpClientParameters.streamableHttp(
        "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp")
    .withName("dashscope-web-search")
    .withHeader("Authorization", "Bearer " + dashscopeApiKey)
    .withConnectTimeout(60);

ReActAgent agent = new ReActAgent(chatModel, memory);
try (McpClient client = agent.addMcpClient(params)) {
    AgentResponse response = agent.call("session-1", "搜索最新的科技新闻");
    System.out.println(response.getAnswer());
}
```

## 相关文档

- [MCP 服务端](/docs/mcp-server) - 暴露本地工具为 MCP 服务
- [工具系统](/docs/function-tool) - FunctionTool 与参数声明
