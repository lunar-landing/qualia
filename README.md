# Qualia

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

**一个企业级 Java AI 智能体框架，基于 ReAct 模式，集成 MCP 工具链，帮助快速构建 LLM 驱动的智能应用。**

Qualia 提供完整的智能体开发框架，支持多模型接入、工具调用、知识库检索、技能扩展等能力，适用于构建企业级 AI 助手、智能客服、代码助手等场景。

## ✨ 核心特性

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

### 构建项目

```bash
mvn clean install
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

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.x | 应用框架 |
| Maven | 3.8+ | 构建工具 |
| MyBatis | 3.x | 数据库 ORM |
| SQLite/MySQL | - | 数据存储 |
| WebFlux | - | 响应式编程 |
| MCP SDK | - | 工具链协议 |
| DashScope SDK | - | AI 模型调用 |

## 📖 文档

详细文档请访问 [qualia-docs](./qualia-docs) 或运行文档站点：

```bash
cd qualia-docs
npm install
npm run dev
```

## 🤝 贡献

欢迎贡献代码、报告问题或提出改进建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/your-feature`)
3. 提交更改 (`git commit -m 'Add some feature'`)
4. 推送到分支 (`git push origin feature/your-feature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 🔗 相关链接

- [Qualia 官网](https://qualia.example.com)
- [API 文档](https://qualia.example.com/docs)
- [问题反馈](https://github.com/your-org/qualia/issues)
