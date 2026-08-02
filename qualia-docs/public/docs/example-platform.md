# 平台示例

## 概述

Qualia Example 是基于 qualia-core 核心框架构建的智能体平台示例，展示了如何将框架能力落地为完整的业务应用。项目采用 Spring Boot + MyBatis 技术栈，实现了保险业务场景下的智能客服、保单管理、理赔处理等功能。

## 项目定位

| 层级 | 组件 | 说明 |
|------|------|------|
| 核心框架 | qualia-core | 提供 Agent、Model、Tool、Memory 等基础能力 |
| 业务平台 | qualia-example | 基于 core 构建的完整业务应用 |

## 技术栈

- **后端框架**：Spring Boot 3.x
- **数据持久化**：MyBatis + MySQL/SQLite
- **核心依赖**：qualia-core
- **构建工具**：Maven

## 核心功能

### 1. 智能对话（ChatController）

提供与智能体交互的 REST API，支持流式响应。

```java
// 对话接口
POST /api/chat
{
    "sessionId": "session-123",
    "message": "帮我查询保单信息"
}

// 流式响应（SSE）
GET /api/chat/stream?sessionId=xxx&message=xxx
```

**核心能力：**
- 多会话管理
- 流式输出（Server-Sent Events）
- 会话历史查询
- 建议问题生成

### 2. 智能体配置（AgentConfigController）

管理智能体的配置信息，支持多智能体场景。

```java
// 获取智能体配置
GET /api/agent-config/{id}

// 更新配置
PUT /api/agent-config/{id}
{
    "name": "保险顾问",
    "systemPrompt": "你是一个专业的保险顾问...",
    "modelId": "qwen-plus",
    "maxIterations": 10
}
```

### 3. 模型管理（ModelController）

管理可用的大语言模型配置。

```java
// 获取模型列表
GET /api/models

// 添加模型
POST /api/models
{
    "name": "qwen-plus",
    "provider": "dashscope",
    "apiKey": "sk-xxx",
    "baseUrl": "https://dashscope.aliyuncs.com"
}
```

### 4. 工具管理（GlobalToolController）

管理全局可用的工具，支持 FunctionTool 和 MCP 工具。

```java
// 获取工具列表
GET /api/tools

// 注册自定义工具
POST /api/tools
{
    "name": "query_policy",
    "description": "查询保单信息",
    "parameters": [...]
}
```

### 5. MCP 服务管理（McpServerController）

管理 MCP 服务器连接和工具发现。

```java
// 获取 MCP 服务器列表
GET /api/mcp-servers

// 添加 MCP 服务器
POST /api/mcp-servers
{
    "name": "policy-tools",
    "url": "https://mcp.example.com",
    "transportType": "HTTP_SSE"
}
```

### 6. 技能管理（SkillController）

管理智能体可用的技能。

```java
// 获取技能列表
GET /api/skills

// 注册技能
POST /api/skills
{
    "name": "policy_analysis",
    "description": "保单分析技能",
    "content": "## 保单分析 SOP\n..."
}
```

### 7. 门户管理（PortalController）

管理前端门户配置，支持多场景定制。

```java
// 获取门户配置
GET /api/portals/{id}

// 更新门户
PUT /api/portals/{id}
{
    "name": "保险智能客服",
    "welcomeMessage": "您好，我是保险智能助手...",
    "theme": {...}
}
```

## 业务模块

### 保单管理

| 服务 | 功能 |
|------|------|
| PolicyMasterService | 保单主数据管理 |
| PremiumService | 保费计算和管理 |
| BeneficiaryService | 受益人管理 |
| InsuredService | 被保险人管理 |
| PolriderService | 保单附加条款 |

### 客户管理

| 服务 | 功能 |
|------|------|
| ClientMasterService | 客户主数据管理 |
| ClientIDInfoService | 客户证件信息 |

### 理赔服务

| 服务 | 功能 |
|------|------|
| ClaimsService | 理赔申请和处理 |
| SupportTicketService | 工单管理 |
| ServiceMemoService | 服务备忘录 |

### 财务管理

| 服务 | 功能 |
|------|------|
| PaymentHistoryService | 缴费历史 |
| WithdrawalHistoryService | 退保/提取历史 |

## 配置管理

### 场景配置（SceneConfigController）

管理不同业务场景的配置。

```java
// 场景配置包含：
// - 系统提示词
// - 可用工具列表
// - 可用技能列表
// - 模型参数
```

### 图表配置（GraphConfigController）

管理数据可视化图表配置。

```java
// 图表配置包含：
// - 数据源配置
// - 图表类型
// - 样式配置
```

### 问题集（QuestionSetController）

管理常见问题和答案模板。

```java
// 问题集用于：
// - 快速问答
// - 培训数据
// - 测试用例
```

## 监控与运维

### 监控接口（MonitorController）

```java
// 系统状态
GET /api/monitor/status

// 活跃会话
GET /api/monitor/sessions

// Token 用量统计
GET /api/monitor/token-usage
```

### 设置管理（SettingsController）

```java
// 系统设置
GET /api/settings

// 更新设置
PUT /api/settings
{
    "maxConcurrentSessions": 100,
    "sessionTimeout": 3600,
    "enableLogging": true
}
```

## 项目结构

```
qualia-example/
├── src/main/java/com/lunarlanding/qualia/example/
│   ├── config/           # 配置类
│   │   ├── BasicConfig.java
│   │   ├── WebConfig.java
│   │   └── DataSourceConfig.java
│   ├── controller/       # REST 控制器（34个）
│   │   ├── ChatController.java
│   │   ├── AgentConfigController.java
│   │   ├── ModelController.java
│   │   ├── GlobalToolController.java
│   │   ├── McpServerController.java
│   │   └── ...
│   ├── entity/           # 数据实体
│   ├── mapper/           # MyBatis Mapper
│   ├── service/          # 业务服务（26个）
│   │   ├── QaService.java        # 核心问答服务
│   │   ├── SessionService.java   # 会话管理
│   │   ├── DatasourceService.java
│   │   └── ...
│   ├── initializer/      # 数据初始化
│   └── util/             # 工具类
└── src/main/resources/
    ├── application.yml   # 应用配置
    └── mapper/           # MyBatis XML
```

## 核心服务说明

### QaService

核心问答服务，负责协调 Agent、Memory、Tool 完成用户请求处理。

```java
@Service
public class QaService {
    // 创建智能体
    // 管理会话
    // 处理用户请求
    // 返回响应结果
}
```

### SessionService

会话管理服务，处理会话的创建、查询、删除。

```java
@Service
public class SessionService {
    // 创建会话
    // 获取会话历史
    // 删除会话
    // 会话超时处理
}
```

### DatasourceService

数据源管理服务，支持多数据源动态切换。

```java
@Service
public class DatasourceService {
    // 注册数据源
    // 动态切换数据源
    // 数据源健康检查
}
```

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.6+
- MySQL 8.0+（或使用 SQLite）

### 2. 配置数据库

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/qualia
    username: root
    password: your-password
```

### 3. 配置 API Key

```yaml
# application.yml 或环境变量
qualia:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
```

### 4. 启动应用

```bash
cd qualia-example
mvn spring-boot:run
```

### 5. 访问 API

```bash
# 测试对话接口
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test","message":"你好"}'
```

## 最佳实践

1. **会话管理**：使用有意义的 sessionId，便于追踪和调试
2. **错误处理**：实现统一的异常处理和错误码体系
3. **日志记录**：记录关键操作和异常信息
4. **性能监控**：监控 Token 用量和响应时间
5. **安全防护**：实现 API Key 认证和权限控制

## 扩展指南

### 添加新业务模块

1. 创建 Entity 实体类
2. 创建 Mapper 接口和 XML
3. 创建 Service 业务服务
4. 创建 Controller REST 接口
5. 注册到智能体工具或技能

### 集成新工具

```java
// 创建自定义工具
FunctionTool newTool = new FunctionTool() {
    {
        setName("new_tool");
        setDescription("新工具描述");
        setParameters(new Parameter[]{...});
    }
    
    @Override
    public String execute(Map<String, Object> args) {
        // 实现逻辑
    }
};

// 注册到智能体
agent.addTool(newTool);
```

## 注意事项

- 生产环境需要配置安全的 API Key 管理
- 大数据量场景需要实现分页和缓存
- 敏感数据需要脱敏处理
- 定期清理过期会话数据
