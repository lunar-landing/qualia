# Qualia 项目改进与迭代路线图

> 基于对整个项目（`qualia-core` 核心框架 + `qualia-example` 业务应用）的全面代码审查生成

---

## 目录

- [P0 — 紧急修复（安全与正确性）](#p0--紧急修复安全与正确性)
- [P1 — 短期迭代（代码质量与架构）](#p1--短期迭代码质量与架构)
- [P2 — 中期规划（功能完善与性能）](#p2--中期规划功能完善与性能)
- [P3 — 长期愿景（工程化与生态）](#p3--长期愿景工程化与生态)

---

## P0 — 紧急修复（安全与正确性）

### 1. 移除硬编码密钥与敏感信息
- **[文件]** `qualia-example/src/main/java/.../config/BasicConfig.java:20`
  - API Key `sk-13b1a53147bc47aab99fc39d65b72567` 硬编码在源码中
  - 应改为从环境变量 `DASHSCOPE_API_KEY` 或配置中心读取
- **[文件]** `qualia-example/src/main/resources/application.yml:18-20`
  - MySQL 数据库 IP、用户名、密码明文暴露
  - 建议改为 `${DB_URL}` / `${DB_USERNAME}` / `${DB_PASSWORD}` 环境变量占位符
  - `.gitignore` 需忽略含真实凭证的 profile 文件

### 2. 接口签名不一致修复
- **[文件]** `qualia-core/src/main/java/.../agent/ReActAgent.java:197`
  - `call()` 返回类型为 `AgentResponse`，但 [Agent](qualia-core/src/main/java/com/lunarlanding/qualia/core/agent/Agent.java) 接口声明返回 `ReActAgentResponse`
  - 类型不匹配会导致编译警告/错误，需统一为 `ReActAgentResponse`（接口定义的类型）

### 3. 响应式流阻塞问题
- **[文件]** `qualia-core/src/main/java/.../agent/ReActAgent.java:614-624`
  - `streamChat()` 方法调用 `chatStream` 后将 token 全部收集到 `StringBuilder`，**完全抵消了流式调用的意义**
  - 应改为直接消费 `Flux<String>` 或改为真正的流式累积
- **[文件]** `qualia-core/src/main/java/.../agent/ReActAgent.java:937-954`
  - `generateSuggestions()` 在最终回答阶段同步调用 `chatModel.chat()`，阻塞事件循环
  - 应改为异步执行（`Mono.fromCallable`）或以 `suggestions` responseType 追加到 Flux 流中
- **[文件]** `qualia-core/src/main/java/.../agent/ReActAgent.java:644-658`
  - `detectLanguage()` 同样使用同步 `chat()` 调用，阻塞主流程
  - 可考虑用简单的字符集启发式检测替代 LLM 调用（成本更低）

### 4. 调试代码清理
- **[文件]** `qualia-core/src/main/java/.../agent/ReActAgent.java:463`
  - `System.err.println(finalAnswerMessages.toString())` — 生产代码中残留的调试输出
- **[文件]** `qualia-core/src/main/java/.../tool/impl/HttpTool.java:135`
  - `System.err.println(parsedBody)` 残留调试输出
- **[文件]** `qualia-core/src/main/java/.../memory/impl/SessionMemory.java:113`
  - `System.err.println(e.getMessage())` — 异常信息写 stderr，应使用 SLF4J Logger.error

---

## P1 — 短期迭代（代码质量与架构）

### 5. JSON 库统一
- 项目同时使用了三种 JSON 处理方式：
  - `Fastjson`（`com.alibaba:fastjson:2.0.51`）— SessionMemory、HttpTool、ReActAgent
  - `Fastjson2`（`com.alibaba.fastjson2`）— DashscopeChatModel
  - `Gson`（`com.google.code.gson:2.10.1`）— qualia-example
- **影响**: 增加 JAR 体积，API 不一致容易误用
- **建议**: 统一为 Fastjson2（`com.alibaba.fastjson2`），其性能更优且与 Jakarta 兼容更好

### 6. 重复 import 清理
- **[文件]** `ReActAgent.java:31-32`：`java.util.List` 被 import 两次

### 7. qualia-core 测试依赖优化
- **[文件]** `qualia-core/pom.xml:49-55`
  - `spring-boot-starter-test`（4.0.3）作为 test scope 依赖引入了一个重型框架，与"零外部框架依赖"的设计原则矛盾
  - 建议替换为纯 `spring-test` 或直接使用 `Mockito + JUnit` 组合

### 8. 注释掉代码的清理
- **[文件]** `qualia-example/pom.xml:143-169`
  - 整段 build 插件配置被注释（包括 jib-maven-plugin for Docker）
  - 如果不再需要应直接删除；如果需要 Docker 镜像构建，应恢复并维护

### 9. 大文件拆分
| 文件 | 大小 | 建议 |
|------|------|------|
| `ReActAgent.java` | 956 行 | 提取 `Summarizer`、`LanguageDetector`、`SuggestionGenerator` 为独立类 |
| `PremiumDataInitializer.java` | 65KB | 拆分为多个按 CSV 类型分离的 Initializer |
| `QuestionSetController.java` | 18.6KB | 业务逻辑下沉到 Service 层 |
| `GlobalToolController.java` | 12.3KB | 拆分为独立的 Tool + MCP + Skill 管理 Controller |
| `GraphPreviewController.java` | 12.2KB | 提取图表渲染逻辑到独立的 Renderer |

### 10. 统一的异常处理与错误码体系
- `qualia-core` 中大量使用 `throw new RuntimeException(...)` 包裹所有异常
- 应定义 `QualiaException` 基类及子类（`ModelException`、`ToolException`、`McpException` 等）
- `qualia-example` 中各 Controller 的错误响应格式不完全一致（有的返回 `code + message`，有的仅返回字符串）

### 11. API 文档版本不一致
- `DOCS.md` 抬头版本为 `1.0.18`
- `pom.xml` 中 `revision` 为 `1.3.2`
- 需建立发布流程，确保文档版本与代码版本同步

### 12. MCP SPI 反射绕过方案加固
- **[文件]** `McpClient.java:45-68`
  - 当前通过反射注入 Jackson SPI 以绕过 Fat JAR 下 ServiceLoader 问题
  - 这是一个 workaround，应持续跟踪 MCP SDK 更新，一旦官方提供 `McpJsonDefaults.setMapperSupplier()` API 后立即替换
  - 另需添加 SPI 注册失败的**显式日志**（而不仅仅是 `logger.warn`）

---

## P2 — 中期规划（功能完善与性能）

### 13. 工具并发执行支持
- **[文件]** `ReActAgent.java:381-422`
  - 当模型返回多个 `actions` 时，工具是**串行执行**的
  - 对于无依赖关系的批量工具调用（如同时查询多个保单），应改为并发执行以降低总延迟
  - 实现时需注意：有一个工具失败不应阻止其他工具结果的使用

### 14. 工具执行超时与重试
- 当前 `Tool.execute()` 无超时控制，一个挂起的 HTTP 请求可能导致 Agent 永久卡住
- 建议在 `FunctionTool` 基类增加 `timeout(Duration)` + `retry(int)` 配置
- `ReActAgent` 在执行工具时使用 `Future.get(timeout, unit)`

### 15. LLM 调用容错增强
- `DashscopeChatModel` 无重试机制，API 瞬时失败直接抛异常
- 建议增加：
  - 指数退避重试（3次，间隔 1s / 2s / 4s）
  - 断路器模式（连续失败 N 次后熔断）
  - 请求超时配置（当前仅依赖 `HttpClient` 默认超时）

### 16. 流式 API 真正的流式化
- `ReActAgent.callStream()` 返回 `Flux<AgentResponse>`，但内部 `streamChat()` 收集全部 token 后才返回
- 理想的流式行为：每收到一个 token 立即通过 `FluxSink` 推送，让 UI 能实时看到思考过程
- 这需要 `ChatModel.chatStream()` 改为返回 `Flux<ChatResponse>`（响应式），而非通过回调

### 17. Agent 执行可取消性
- 当前 Agent 一旦启动就无法取消
- 应支持通过 `FluxSink.isCancelled()` 检测取消信号，或在 `runIteration` 递归中检查中断标志
- UI 层（如用户点击"停止生成"按钮）需要此能力

### 18. 短期记忆查询性能优化
- **[文件]** `SessionMemory.java`
  - `getRecentMessages` 使用 `ORDER BY sequence_num DESC LIMIT ?` 后反转
  - 在大数据量会话中，`BeanListHandler` 反射反序列化 + steps_json 反序列化开销大
  - 建议：为 `chat_message` 表增加 `reasoning_content` 列索引；对高容量会话引入消息归档机制

### 19. qualia-example 分页与大数据量优化
- 多个查询接口（`/api/premium/customers`、`/api/service-memo/*` 等）**无分页参数**，全量返回
- 对于 1600+ 客户、2400+ 服务记录的规模，应统一增加 `page` / `size` 分页支持

### 20. API Key 管理完善
- `LoginInterceptor` 中对 `/chat` 路径的 API Key 认证已实现，但：
  - API Key 不支持细粒度权限（如限制可调用的 Agent、限制访问的保单范围）
  - API Key 的调用审计日志未落地
  - 建议增加 API Key 的 RBAC 能力与调用限流

### 21. 配置文件外置与多环境支持
- `application.yml` 中所有配置（端口、数据源、文件上传限制）均硬编码
- 应通过 Spring Profile（`application-dev.yml` / `application-prod.yml`）区分环境
- 敏感配置通过环境变量或外部配置中心注入

---

## P3 — 长期愿景（工程化与生态）

### 22. 测试体系建设
- 当前测试覆盖率极低（仅 `McpIntegrationTest`、`QualiaExampleApplicationTests`）
- 建议优先级：
  1. `qualia-core` 各 Tool 的单元测试（`HttpTool`、`SystemTool`、`LoadSkillTool`）
  2. `ReActAgent` 的 Mock 测试（Mock LLM 输出，验证工具解析与迭代逻辑）
  3. `SessionMemory` 的数据库集成测试
  4. `qualia-example` 各 Controller 的 WebMvcTest

### 23. Docker 容器化
- 恢复 `qualia-example/pom.xml` 中被注释的 `jib-maven-plugin` 或新增 Dockerfile
- 提供 `docker-compose.yml`（含 MySQL + qualia-app），实现一键本地启动

### 24. CI/CD 流水线
- 添加 `.github/workflows/ci.yml`（或 GitLab CI）：
  - `mvn test` 自动运行测试
  - `mvn package` 构建 Fat JAR
  - 可选的 Docker 镜像构建与推送

### 25. 可观测性
- **日志**: 统一使用 SLF4J + Logback，消除 `System.err` 调用
- **指标**: 接入 Micrometer，暴露 Agent 调用次数、工具执行耗时、LLM token 消耗等指标
- **链路追踪**: Agent 的 ReAct 步骤可通过 OpenTelemetry Span 记录，便于排查问题

### 26. 文档整合与规范化
- 当前有 5 份独立 API 文档（`API.md`、`CLIENT_POLICY_API.md`、`POLRIDER_API.md`、`SERVICEMEMO_API.md`、`SUPPORT_TICKET_API.md`），建议：
  - 整合为一份 `API_REFERENCE.md`，或拆分为按模块（Premium、Policy、Claims、SupportTicket）的结构化目录
  - 使用 OpenAPI 3.0 / Swagger 注解自动生成在线文档
- `DOCS.md` 内容与代码同步更新，建议在 CI 中加入版本号一致性检查

### 27. 多模型供应商插件化
- 当前 `ChatModel` 实现（Dashscope、Moark）与 core 包耦合
- 建议定义 SPI 加载机制（`ChatModelProvider`），让第三方可以以 JAR 插件方式接入新模型提供商（如 OpenAI、Azure、Anthropic），无需修改 core 代码

### 28. 技能系统的 IDE 支持
- 当前 Skill 通过 `DirectorySkillLoader` 从文件系统加载 `skill.md` + Python 脚本
- 可提供：
  - `skill.md` 的 JSON Schema 校验
  - VS Code / IntelliJ 插件提供 skill 模板和补全支持
  - Skill 市场/注册中心（中心化存储与版本管理）

### 29. 多智能体协作
- 当前一个 `ReActAgent` 实例处理所有对话
- 未来可支持：
  - **Agent Router**: 根据用户意图路由到不同 Agent（保险咨询 Agent、理赔 Agent、工单 Agent）
  - **Agent 间通信**: 一个 Agent 可将子任务委托给另一个 Agent 并汇总结果
  - **Agent 流水线**: 定义 Agent 执行顺序，如 查询 → 分析 → 报告生成

### 30. 合规与数据治理（针对 YF Life 场景）
- 香港《个人资料（私隐）条例》要求：
  - 客户敏感字段（姓名、证件号、手机号）的**脱敏日志**
  - 数据保留策略（历史消息定期归档/删除）
  - 访问审计日志（谁在何时查看了哪些保单数据）
- 建议在 `qualia-example` 中增加审计拦截器与数据脱敏工具类

---

## 优先级汇总

| 优先级 | 条目 | 预估工时 | 风险/影响 |
|--------|------|:--------:|-----------|
| **P0** | #1 移除硬编码密钥 | 2h | 安全漏洞，必须立即修复 |
| **P0** | #2 接口类型不一致 | 1h | 编译/运行时错误隐患 |
| **P0** | #3 流式阻塞 | 4h | 流式 API 形同虚设 |
| **P0** | #4 调试代码清理 | 0.5h | 信息泄露与日志污染 |
| **P1** | #5 JSON 库统一 | 4h | 依赖管理与 API 一致性 |
| **P1** | #9 大文件拆分 | 3d | 代码可维护性 |
| **P1** | #10 统一异常处理 | 1d | 错误响应一致性 |
| **P2** | #13 工具并发 | 1d | Agent 响应延迟降低 50%+ |
| **P2** | #15 LLM 容错 | 1d | 生产可用性 |
| **P2** | #16 真正流式化 | 2d | 用户体验改善 |
| **P2** | #19 分页改造 | 1d | API 性能 |
| **P3** | #22 测试体系 | 3d | 回归安全网 |
| **P3** | #23 Docker 化 | 1d | 部署标准化 |
| **P3** | #26 文档整合 | 1.5d | 开发者体验 |

---

> **总工时预估**: P0（1d）+ P1（1w）+ P2（2w）+ P3（2.5w）≈ **6 周**
>
> 建议按 P0 → P1 顺序快速推进，P2/P3 可并行开展。
