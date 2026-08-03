<div align="center">

# Qualia

**企业级 Java AI 智能体框架**

*基于 ReAct 模式，集成 MCP 工具链，快速构建 LLM 驱动的智能应用*

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36.svg)](https://maven.apache.org/)
[![MCP](https://img.shields.io/badge/MCP-Protocol-purple.svg)](https://modelcontextprotocol.io/)

[快速开始](#-快速开始) · [文档](#-文档) · [示例](#-示例) · [贡献](#-贡献)

</div>

---

## 概述

Qualia 是一个轻量级、模块化的 Java 框架，用于构建基于大语言模型的智能体应用。它提供完整的智能体开发框架，支持多模型接入、工具调用、知识库检索、技能扩展等能力，适用于构建企业级 AI 助手、智能客服、代码助手等场景。

基于 ReAct（Reasoning + Acting）推理模式，通过思考-行动-观察循环支持复杂任务分解；统一接口适配 DashScope、OpenAI、Claude 等主流 LLM；采用注解驱动 + MCP 协议集成的工具系统，轻松扩展工具能力；基于 JSON/MySQL 的会话记忆管理，支持滑动窗口和摘要压缩; 以及可复用的提示词模板与脚本执行的技能系统，支持渐进式加载。

## 项目

```
qualia/
├── qualia-core/          # 核心模块
├── qualia-code/          # Code 模块 (包括 web 服务)
├── qualia-code-desktop/  # Code 桌面应用
└── qualia-docs/          # 项目文档
```

### qualia-core

框架核心模块，提供智能体核心架构（ReActAgent、HarnessAgent）、模型协议抽象层（ChatModel、EmbeddingModel、RerankModel）、工具系统和 MCP 集成、会话记忆管理、RAG 检索管道以及技能系统。

### qualia-code

Web IDE 模块，基于 Spring Boot 构建，提供完整的 Web 服务和浏览器端代码编辑体验。支持会话管理与工作区切换、工具调用可视化展示、多主题支持（深色/浅色）、MCP 服务器管理、模型配置管理、技能和工具权限管理，以及流式响应和实时交互。

### qualia-code-desktop

桌面应用模块，基于 SWT 构建，内嵌浏览器加载 Web IDE 界面。提供原生桌面应用体验，支持窗口状态持久化（尺寸、位置、最大化状态）、系统标题栏主题联动（深色/浅色自动适配）以及跨平台支持（Windows/macOS）。

## 🚀 快速开始

### 安装

#### 方式一：Maven 依赖（推荐）

```xml
<dependency>
    <groupId>com.lunarlanding</groupId>
    <artifactId>qualia-core</artifactId>
    <version>1.5.1</version>
</dependency>
```

### 5 分钟上手

```java
// 1. 初始化模型和记忆
ChatModel model = new DashscopeChatModel("your-api-key", "qwen-turbo");
Memory memory = new InMemoryMemory();

// 2. 创建智能体并注册工具
ReActAgent agent = new ReActAgent(model, memory);
agent.setSystemPrompt("你是一个专业的智能助手。");

// 注册工具（注解驱动）
agent.registerToolsFrom(new MyTools());

// 3. 执行对话
AgentResponse response = agent.call("session-1", "帮我搜索AI最新进展");
System.out.println(response.getAnswer());

// 4. 流式调用
Flux<AgentResponse> stream = agent.callStream("session-1", "解释量子计算");
stream.subscribe(step -> System.out.println(step.getAnswer()));

// 工具类定义
public class MyTools {
    @AsFunctionTool(name = "search", description = "搜索互联网信息")
    public String search(@Param("query") String query) {
        // 搜索逻辑
        return "搜索结果";
    }
}
```

## 📦 发布

### GitHub Packages

本项目使用 GitHub Packages 发布 Maven 包。

#### 配置认证

在项目根目录创建 `settings.xml`（已添加到 `.gitignore`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
    <localRepository>C:\Users\你的用户名\.m2\repository</localRepository>
    <servers>
        <server>
            <id>github</id>
            <username>lunar-landing</username>
            <password>你的GitHub Token</password>
        </server>
    </servers>
</settings>
```

#### 发布命令

```bash
# 发布 qualia-core
mvn clean deploy -pl qualia-core -DskipTests -s settings.xml

# 发布所有模块
mvn clean deploy -DskipTests -s settings.xml
```

#### 引用依赖

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/lunar-landing/qualia</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.lunarlanding</groupId>
    <artifactId>qualia-core</artifactId>
    <version>1.5.1</version>
</dependency>
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

- [GitHub Packages](https://github.com/lunar-landing/qualia/packages)
- [问题反馈](https://github.com/lunar-landing/qualia/issues)
- [讨论区](https://github.com/lunar-landing/qualia/discussions)

## ⭐ Star History

如果这个项目对你有帮助，请考虑给它一个 Star！
