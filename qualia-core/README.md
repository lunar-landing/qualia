# Qualia Core

Qualia Core 是一个轻量级、模块化的 Java 框架，用于构建基于大语言模型的智能体应用。它提供了完整的 ReAct 智能体实现、多模型适配、工具系统、记忆管理和 RAG 检索能力。

## 核心特性

- **ReAct 智能体** - 基于反应式思维链的智能体实现
- **多模型支持** - 统一接口适配 DashScope、OpenAI 等模型服务
- **工具系统** - 注解驱动的工具注册与 MCP 协议集成
- **记忆管理** - 会话级消息存储与上下文管理
- **RAG 检索** - 向量存储、文档解析与重排序能力
- **技能系统** - 可复用的提示词模板与脚本执行，支持按需加载

## 架构概览

![Qualia Core 架构](docs/architecture.svg)

## 模块说明

### Agent 模块

智能体核心模块，实现 ReAct 推理框架。

| 类 | 说明 |
|---|------|
| `Agent` | 智能体接口，定义 `call()` 和 `callStream()` 方法 |
| `ReActAgent` | ReAct 智能体实现，支持工具调用、记忆管理和流式输出 |
| `AgentResponse` | 智能体响应封装，包含步骤、答案、Token 用量等 |
| `AgentStep` | 单个执行步骤（思考/行动/观察/回答） |

### Model 模块

大语言模型抽象层，提供统一的模型调用接口。

| 接口/类 | 说明 |
|---------|------|
| `ChatModel` | 聊天模型接口，支持同步和流式调用 |
| `ChatMessage` | 消息对象（system/user/assistant/tool） |
| `ChatResponse` | 模型响应，包含 choices 和 usage |
| `DashscopeChatModel` | 阿里云 DashScope 模型适配 |
| `EmbeddingModel` | 向量嵌入模型接口 |
| `RerankModel` | 重排序模型接口 |

### Tool 模块

工具系统，支持多种工具注册方式。

| 类 | 说明 |
|---|------|
| `Tool` | 工具抽象基类 |
| `FunctionTool` | 函数工具接口 |
| `@AsFunctionTool` | 工具注解，标记方法为可调用工具 |
| `FunctionToolScanner` | 注解扫描器，自动注册工具 |
| `MethodToolAdapter` | 方法适配器，将 Java 方法包装为工具 |
| `McpToolAdapter` | MCP 工具适配器，集成远程 MCP 服务 |
| `AgentToolAdapter` | 子智能体适配器，实现多智能体协作 |

### Memory 模块

会话记忆管理。

| 接口 | 说明 |
|------|------|
| `Memory` | 统一记忆接口 |
| `SessionMemoryMessage` | 会话消息实体 |
| `InMemoryMemory` | 基于内存的记忆实现 |
| `JdbcMemory` | 基于 JDBC 的持久化记忆 |

### Retrieval 模块

RAG 检索能力。

| 类 | 说明 |
|---|------|
| `Retriever` | 检索器接口 |
| `AbstractRetriever` | 检索器基类，提供重排和去重 |
| `QueryRetriever` | 基于向量的查询检索器 |
| `QueryRewritingRetriever` | 查询重写检索器 |
| `DocumentParser` | 文档解析器（PDF/Word/TXT/Markdown） |
| `TextSplitter` | 文本分块器 |

### Store 模块

向量存储抽象。

| 接口 | 说明 |
|------|------|
| `VectorStore` | 向量存储接口 |
| `InMemoryVectorStore` | 内存向量存储 |
| `MysqlVectorStore` | MySQL 向量存储 |

### Skill 模块

技能系统，支持可复用的提示词模板。

| 类 | 说明 |
|---|------|
| `Skill` | 技能定义，包含内容、引用和脚本 |
| `SkillScript` | 技能脚本元数据 |
| `ScriptEngine` | 脚本执行引擎接口 |
| `PythonScriptEngine` | Python 脚本执行引擎 |

#### 技能工具

技能系统通过工具与智能体交互，支持渐进式加载：

| 工具 | 说明 |
|------|------|
| `skill-selector` | 查询可用技能列表，返回名称和描述 |
| `skill-loader` | 加载指定技能的完整内容（skill.md、脚本、文档） |
| `skill-script-runner` | 执行技能中的脚本 |
| `skill-reference-reader` | 读取技能的附属文档 |

### MCP 模块

Model Context Protocol 集成。

| 类 | 说明 |
|---|------|
| `McpServerConnection` | MCP 服务器连接管理 |
| `McpServerParameters` | MCP 连接参数 |
| `McpException` | MCP 异常 |

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.lunarlanding</groupId>
    <artifactId>qualia-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 创建智能体

```java
// 初始化模型
ChatModel model = new DashscopeChatModel();
model.baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
model.apiKey("your-api-key");

// 创建记忆
Memory memory = new InMemoryMemory();

// 创建智能体
ReActAgent agent = new ReActAgent(model, memory);
agent.setSystemPrompt("你是一个专业的智能助手。");

// 添加工具
agent.addTool(new HttpTool());

// 添加技能
Skill skill = new Skill("query-data", "数据查询技能")
    .withContent("请根据用户问题查询数据...");
agent.addSkill(skill);
```

> **注意**：技能系统采用渐进式加载设计。智能体在运行时会自动注册 `skill-selector`、`skill-loader` 等工具，无需手动添加。智能体会根据用户问题自动判断是否需要查询和加载技能。

### 3. 调用智能体

```java
// 同步调用
AgentResponse response = agent.call("session-1", "你好，请介绍一下自己");
System.out.println(response.getFinalAnswer());

// 流式调用
agent.callStream("session-1", "解释一下量子计算")
    .subscribe(response -> {
        if (response.getCurrentStep() != null) {
            System.out.println("步骤: " + response.getCurrentStep().getContent());
        }
        if (response.getFinalAnswer() != null) {
            System.out.println("回答: " + response.getFinalAnswer());
        }
    });
```

### 4. 注册自定义工具

```java
// 方式一：注解驱动
public class MyTools {
    @AsFunctionTool(name = "calculate", description = "数学计算器")
    public String calculate(@Param("expression") String expression) {
        // 计算逻辑
        return String.valueOf(result);
    }
}

agent.registerToolsFrom(new MyTools());

// 方式二：实现 FunctionTool 接口
public class CustomTool extends FunctionTool {
    public CustomTool() {
        this.setName("custom");
        this.setDescription("自定义工具");
        this.setParameters(new Parameter[]{
            new Parameter("input", "输入参数", "string", true)
        });
    }
    
    @Override
    public String execute(Map<String, Object> arguments) {
        return "执行结果";
    }
}

agent.addTool(new CustomTool());
```

### 5. 集成 MCP 服务

```java
// 连接 MCP 服务器
McpServerParameters params = new McpServerParameters();
params.setName("web-search");
params.setType("streamableHttp");
params.setUrl("https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp");
params.setHeaders(Map.of("Authorization", "Bearer " + apiKey));

McpServerConnection connection = new McpServerConnection(params);
connection.connect();

// 获取并注册 MCP 工具
List<McpToolAdapter> mcpTools = connection.getTools();
mcpTools.forEach(agent::addTool);
```

### 6. 多智能体协作

```java
// 创建子智能体
ReActAgent subAgent = new ReActAgent(model, memory);
subAgent.name("data-analyst");
subAgent.description("数据分析专家");
subAgent.setSystemPrompt("你是数据分析专家...");

// 注册为主智能体的工具
agent.addSubAgent(subAgent);

// 主智能体会自动将子智能体作为工具调用
```

## 设计原则

1. **接口驱动** - 核心能力通过接口定义，支持多种实现
2. **模块解耦** - 各模块独立，可按需组合使用
3. **渐进式披露** - 技能系统采用渐进式加载，智能体先通过 `skill-selector` 查询技能列表，再按需加载具体技能，优化 Token 使用
4. **统一抽象** - 模型、工具、记忆等通过统一抽象层访问
5. **流式优先** - 原生支持 Reactor 响应式流

## 依赖项

- Java 17+
- Reactor Core (响应式流)
- Fastjson2 (JSON 处理)
- SLF4J (日志)
- MCP SDK (MCP 协议支持)

## 许可证

Apache License 2.0
