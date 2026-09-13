# 更新日志

本文档记录 Qualia 框架的所有重要更改。

---

## [未发布]

### 新增

- **qualia-core / 多模态消息**：消息模型支持图片等多模态内容块直传视觉模型。新增 `ContentPart`（`text` / `image_url` 两类内容块，符合 OpenAI Chat Completions 规范）与 `ChatImage`（文件名 + data URL 的图片入参载体）；`ChatMessage` 新增 `contentParts` 字段，非空时序列化为 content 数组、为空时保持纯文本字符串，旧消息格式完全兼容；`Agent`/`ReActAgent` 新增 `callStream(sessionId, input, List<ChatImage>)` 重载，图片以 `image_url` 内容块（data URL）仅当轮直传模型，会话记忆中只保留 `[图片: 文件名]` 文本占位符，避免 base64 进入上下文导致膨胀；`detectLanguage` 对空文本短路返回。需选用具备视觉能力的模型。

### 变更

- **qualia-core / 文件解析器**：文档解析能力从 `retrieval.parser` 迁移为顶级包 `core.parser`，并破坏性重设计。解析器定位为纯文件转换组件：输入支持字节（`parse(byte[])`）与磁盘路径（`parse(Path)`）两种形态，类型判断只依据文件名后缀（`supports(fileName)`），产出统一的 `List<Document>`（分段型解析器附带页码等元数据），不再耦合 RAG 检索或附件上传概念。包含三类实现：文本解析器覆盖纯文本与常见代码/配置后缀，UTF-8 严格解码失败自动回退 GBK 并去除 BOM；Markdown 解析器改为保留原文（含代码块、链接、表格）不做标记清洗，仅提取首个一级/二级标题写入元数据；新增 PDF 解析器（基于 Apache PDFBox）按页提取文字层并附页码元数据，加密 PDF 与全页无文字层的扫描版 PDF 抛出明确的解析异常。解析失败统一抛出新增的 `DocumentParseException`。同步更新 `VectorStore` 体系的 `Document` 引用路径。

## [0.1.1] - 2026-09-09

### 变更

- **qualia-core / BashTool**：Windows 下执行器由 cmd 迁移为 PowerShell。cmd 对模型的 Unix 命令习惯兼容度低（无 `ls`/`cat`/`pwd` 等命令），模型需多轮试错；PowerShell 内置系统级别名兼容大部分 Unix 命令名。实现上以 UTF-16LE Base64 编码传递命令规避引号转义，显式 UTF-8 编解码，输出渲染为固定宽度纯文本，并正确透传原生命令的退出码。macOS/Linux 分支不受影响，仍走原生 `sh -c`。已知限制：`&&` 链接符在 Windows PowerShell 5.1 不支持（模型可自愈改用 `;`）；`curl`/`wget` 别名指向 `Invoke-WebRequest`，需用 `curl.exe`；每次调用有 PowerShell 进程启动开销（数秒）；极少数 GBK 编码输出的原生工具在 UTF-8 设置下可能乱码。

### 修复

- **qualia-core / ReActAgent**：修复并发会话下语言提示互相污染的问题。`detectedLanguage` 原为 Agent 实例字段，在单实例服务多会话的场景下，并发请求的语言检测结果会互相覆盖，导致系统提示词与最终回答阶段注入错误的语言提示；现改为每次请求内局部检测并通过参数传递，会话之间完全隔离。
- **qualia-core / ReActAgent**：工具调用找不到对应工具时不再终止会话，改为将“工具不存在或已被移除 + 当前可用工具列表”作为观察结果（OBSERVATION）反馈给模型继续推理，由模型自行纠正；同时批量调用中不再因单个工具缺失而跳过其余合法调用。
- **qualia-core / BashTool**：修复 Windows 下执行交互式命令（如不带参数的 `date`）一直挂起直至超时的问题。原实现未关闭子进程 stdin，`cmd` 的 `date`、等待确认的 `del` 等命令会阻塞等待键盘输入直到被强制结束；现启动后立即关闭 stdin，交互式提示读到 EOF 后按默认行为正常结束。同时超时错误信息会附上超时前已捕获的输出，便于判断命令卡在何处。

## [0.1.0] - 首发

我们非常高兴地宣布 Qualia 框架的正式发布！这是一个企业级 Java AI 智能体框架，基于 ReAct（Reasoning + Acting）模式构建，旨在帮助开发者快速构建 LLM 驱动的智能应用。

Qualia 提供了完整的智能体开发生态：核心的 ReActAgent 和开箱即用的 HarnessAgent 实现，支持多智能体协作；统一的模型服务抽象层，兼容 DashScope、OpenAI、DeepSeek 等主流厂商；强大的工具系统，通过注解驱动零侵入地暴露自定义工具，并内置了文件操作、网络请求、搜索引擎、知识检索等丰富的工具集；基于数据库的会话记忆持久化，配合两层记忆压缩机制，有效管理上下文长度；技能系统支持将复杂任务封装为可复用的执行单元；原生 MCP 协议集成，支持远程工具发现与调用；完整的 RAG 检索管道，支持多种文档格式的解析与语义检索。

此外，我们还提供了完整的文档站点、示例应用和退休规划领域示例，帮助开发者快速上手。感谢所有贡献者的努力，我们期待 Qualia 能够赋能更多的 AI 应用场景！
