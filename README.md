# Qualia

<div align="center">

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36.svg)](https://maven.apache.org/)
[![MCP](https://img.shields.io/badge/MCP-Protocol-purple.svg)](https://modelcontextprotocol.io/)

**企业级 Java AI 智能体框架**

*基于 ReAct 模式，集成 MCP 工具链，快速构建 LLM 驱动的智能应用*

[快速开始](#-快速开始) · [文档](#-文档) · [示例](#-示例) · [贡献](#-贡献)

</div>

---

## 概述

Qualia 是一个轻量级、模块化的 Java 框架，用于构建基于大语言模型的智能体应用。它提供完整的智能体开发框架，支持多模型接入、工具调用、知识库检索、技能扩展等能力，适用于构建企业级 AI 助手、智能客服、代码助手等场景。

### 核心能力

| 能力 | 说明 |
|------|------|
| **ReAct 推理** | 思考-行动-观察循环模式，支持复杂任务分解 |
| **多模型适配** | 统一接口适配 DashScope、OpenAI、Claude 等主流 LLM |
| **工具系统** | 注解驱动 + MCP 协议集成，轻松扩展工具能力 |
| **会话记忆** | 基于 SQLite/MySQL 的会话状态管理，支持滑动窗口和摘要压缩 |
| **RAG 检索** | 向量存储、文档解析、重排序一体化知识库方案 |
| **技能系统** | 可复用的提示词模板与脚本执行，支持渐进式加载 |

## ✨ 特性

### 🤖 智能体系统

- **ReAct 推理循环**：思考-行动-观察循环模式，支持复杂任务分解
- **多智能体协作**：支持子智能体注册和工具化调用
- **会话记忆**：基于 SQLite/MySQL 的会话状态管理
- **流式输出**：原生支持 `Flux<ChatResponse>` 实时响应

### 🔧 工具系统

- **注解驱动**：`@AsFunctionTool` 自动注册工具
- **MCP 集成**：Model Context Protocol 远程工具发现与调用
- **内置工具**：文件操作、HTTP 请求、RagFlow 检索、Tavily 搜索
- **工具权限管理**：支持启用/禁用单个工具

### 📚 知识库

- **文档解析**：支持 TXT、CSV、MD、JSON、XML、HTML、PDF
- **向量存储**：基于嵌入模型的语义检索
- **RagFlow 集成**：支持 RagFlow 知识库服务

### 🎯 技能系统

- **Skill 脚本**：支持 Python 脚本执行
- **动态加载**：运行时加载和执行技能
- **技能管理**：支持启用/禁用/卸载技能

### 🖥️ Qualia Code（Web IDE）

- **代码编辑器**：基于浏览器的代码编辑体验
- **工具可视化**：工具调用过程可视化展示
- **多主题支持**：深色/浅色主题切换
- **MCP 服务器管理**：可视化管理 MCP 服务器连接
- **模型配置**：多模型配置与动态切换

## 📁 项目结构

```
qualia/
├── qualia-core/          # 框架核心模块
├── qualia-code/          # Web IDE 模块
├── qualia-code-desktop/  # 桌面应用模块
└── qualia-docs/          # 文档站点
```

### qualia-core

框架核心模块，提供：
- 智能体核心架构（ReActAgent、HarnessAgent）
- 模型协议抽象层（ChatModel、EmbeddingModel、RerankModel）
- 工具系统和 MCP 集成
- 会话记忆管理
- RAG 检索管道
- 技能系统

### qualia-code

Web IDE 模块，提供：
- 基于浏览器的代码编辑体验
- 工具调用可视化展示
- 多主题支持（深色/浅色）
- MCP 服务器管理
- 模型配置管理
- 技能和工具权限管理

### qualia-code-desktop

桌面应用模块，基于 qualia-code：
- 原生桌面应用体验
- 系统标题栏集成
- 窗口管理

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+

### 安装

#### 方式一：Maven 依赖（推荐）

```xml
<dependency>
    <groupId>com.lunarlanding</groupId>
    <artifactId>qualia-core</artifactId>
    <version>1.5.1</version>
</dependency>
```

#### 方式二：源码构建

```bash
git clone https://github.com/your-org/qualia.git
cd qualia
mvn clean install
```

### 5 分钟上手

#### 1. 创建智能体

```java
// 初始化模型
ChatModel model = new DashscopeChatModel("your-api-key", "qwen-turbo");

// 创建记忆（基于 SQLite）
Memory memory = new InMemoryMemory();

// 创建智能体
ReActAgent agent = new ReActAgent(model, memory);
agent.setSystemPrompt("你是一个专业的智能助手。");
```

#### 2. 注册工具

```java
// 方式一：注解驱动（推荐）
public class MyTools {
    @AsFunctionTool(name = "search", description = "搜索互联网信息")
    public String search(@Param("query") String query) {
        // 搜索逻辑
        return "搜索结果";
    }
}

agent.registerToolsFrom(new MyTools());

// 方式二：手动构建
Tool searchTool = FunctionTool.builder()
    .name("search")
    .description("搜索互联网信息")
    .parameter("query", String.class, "搜索关键词", true)
    .execute(args -> searchWeb(args.get("query").toString()))
    .build();

agent.addTool(searchTool);
```

#### 3. 执行对话

```java
// 同步调用
AgentResponse response = agent.call("session-1", "帮我搜索AI最新进展");
System.out.println(response.getAnswer());

// 流式调用
Flux<AgentResponse> stream = agent.callStream("session-1", "解释量子计算");
stream.subscribe(step -> System.out.println(step.getAnswer()));
```

### 运行 Qualia Code

```bash
cd qualia-code
mvn spring-boot:run
```

访问 http://localhost:8080 查看 Web IDE。

### 配置模型

首次运行需要配置模型 API Key：

```bash
# 交互式初始化
java -jar qualia-code/target/qualia-code-*.jar init
```

或手动编辑配置文件 `~/.qualia/qualia-code.json`：

```json
{
  "models": [
    {
      "name": "qwen-plus",
      "provider": "Dashscope",
      "apiKey": "your-api-key",
      "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
      "modelCode": "qwen-plus"
    }
  ],
  "defaultModel": "qwen-plus"
}
```

## 📖 文档

详细文档请访问 [qualia-docs](./qualia-docs) 或运行文档站点：

```bash
cd qualia-docs
npm install
npm run dev
```

### 核心组件文档

| 组件 | 说明 | 文档 |
|------|------|------|
| **Agent** | ReAct 智能体，支持多步骤推理 | [智能代理](./qualia-docs/public/docs/react-agent.md) |
| **Model** | 多模型适配，支持流式输出 | [模型服务](./qualia-docs/public/docs/multi-model.md) |
| **Memory** | 会话记忆，SQLite 持久化 | [对话记忆](./qualia-docs/public/docs/memory-management.md) |
| **Tool** | 工具系统，支持自定义扩展 | [工具系统](./qualia-docs/public/docs/function-tool.md) |
| **Skill** | 技能编排，任务分解 | [技能管理](./qualia-docs/public/docs/skill.md) |
| **MCP** | 协议集成，远程工具发现 | [MCP 客户端](./qualia-docs/public/docs/mcp-client.md) · [MCP 服务端](./qualia-docs/public/docs/mcp-server.md) |

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.x | 应用框架 |
| Maven | 3.8+ | 构建工具 |
| MyBatis | 3.x | 数据库 ORM |
| SQLite/MySQL | - | 数据存储 |
| WebFlux | - | 响应式编程 |
| MCP SDK | 1.1.2 | 工具链协议 |
| DashScope SDK | - | AI 模型调用 |

## 🎯 示例

### 多智能体协作

```java
// 创建子智能体
ReActAgent subAgent = new ReActAgent(model, memory);
subAgent.name("data-analyst");
subAgent.description("数据分析专家");
subAgent.setSystemPrompt("你是数据分析专家...");

// 注册为主智能体的工具
agent.addSubAgent(subAgent);
```

### MCP 服务集成

```java
// 连接 MCP 服务器
McpServerParameters params = new McpServerParameters();
params.setName("web-search");
params.setType("streamableHttp");
params.setUrl("https://your-server.com/mcp");
params.setHeaders(Map.of("Authorization", "Bearer " + apiKey));

McpServerConnection connection = new McpServerConnection(params);
connection.connect();

// 获取并注册 MCP 工具
List<McpToolAdapter> mcpTools = connection.getTools();
mcpTools.forEach(agent::addTool);
```

### 技能系统

```java
// 添加技能
Skill skill = new Skill("query-data", "数据查询技能")
    .withContent("请根据用户问题查询数据...");
agent.addSkill(skill);

// 智能体会自动在系统提示词中注册可用技能列表
```

## 🤝 贡献

欢迎贡献代码、报告问题或提出改进建议！

### 贡献流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/your-feature`)
3. 提交更改 (`git commit -m 'Add some feature'`)
4. 推送到分支 (`git push origin feature/your-feature`)
5. 创建 Pull Request

### 开发规范

- 遵循 Java 编码规范
- 添加必要的单元测试
- 更新相关文档
- 确保所有测试通过

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 🔗 相关链接

- [文档站点](https://your-org.github.io/qualia)
- [问题反馈](https://github.com/your-org/qualia/issues)
- [讨论区](https://github.com/your-org/qualia/discussions)

## ⭐ Star History

如果这个项目对你有帮助，请考虑给它一个 Star！
