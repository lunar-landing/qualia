# 资料库（Knowledge Base）技术方案

> 产品：qualia-code ｜ 日期：2026-10-10 ｜ 状态：方案评审稿
>
> 定位：qualia-code 内置本地资料库。自包含实现（存储 / 解析 / 索引 / 检索全部在 qualia-code 模块内），不依赖 qualia-core 的 RAG/知识库体系，不依赖外部 RagFlow 服务。检索演进对齐业界融合架构：**v1 BM25 → v1.5 混合检索 → v2 LLM 库导读**，Agent 按需工具化检索（Agentic RAG）。

## 1. 总体架构

```
qualia-code-ui (Vue 3)
  ├─ 资料库视图（整栏，side-rail 入口）
  ├─ 输入区引用 chips（多选知识库，随消息发送）
  └─ 步骤流工具卡片（复用现有渲染，自动覆盖）
        │ REST /api/knowledge/*
qualia-code (Spring Boot)
  ├─ KnowledgeController        # CRUD / 上传 / 状态 / 预览 / 调试检索
  ├─ KnowledgeService           # 树管理、异步解析队列、BM25 索引与检索
  └─ KnowledgeSearchTool        # Agent 工具（isReadOnly），按会话引用动态注册
        │
~/.qualia/code/knowledge/{kbId}/   # 本地存储（原子写：临时文件 + 替换）
```

## 2. 数据模型与存储

```
~/.qualia/code/knowledge/
  {kbId}/kb.json                 # 库元数据：{id, name, description, createdAt}
  {kbId}/tree.json               # 元数据树（物理文件按 fileId 平铺，重命名/移动不动物理文件）
  {kbId}/files/{fileId}/source.{ext}   # 原文件
  {kbId}/files/{fileId}/meta.json      # {fileId, name, ext, size, status, error, uploadedAt}
  {kbId}/files/{fileId}/chunks.json    # 切块 + 索引
```

- `kbId` / `fileId` / `nodeId` 均为 UUID；文件夹与文件均为 tree.json 中的节点：
  `{id, type: "folder"|"file", name, parentId, fileId?, createdAt}`，根节点 parentId = null
- chunks.json：
  `{chunks:[{id, nodeId, seq, heading, text, offset:{start,end}}], df:{term→df}}`（tf 按 chunk 内嵌；v1.5 增加 `vectors:[...]` 字段）
- 分词约定：中文 CJK 二元组（bigram）+ 英文按词、数字串整体；BM25 参数 k1=1.5、b=0.75

## 3. 解析管线（异步）

状态机：`UPLOADED → PARSING → PARSED | FAILED(可重试)`

| 步骤 | 实现 |
|---|---|
| 文本抽取 | md/txt 读 UTF-8；pdf → `PdfDocumentParser`；docx → `WordDocumentParser`（与附件管线同款解析器） |
| 规则切块 | md 按标题层级（# ~ ####）聚合，超 1200 字再按段落二分；非 md 按空行段落聚合，1200 字上限、150 字重叠；chunk 记 heading 路径与原文偏移 |
| 索引 | 分词 → chunk 级 tf 表 + 库级 df 表，落盘 chunks.json |
| 并发 | 单线程文件级队列（避免写冲突）；单文件失败不阻塞后续 |
| 轮询 | 前端 800ms 间隔 `GET /status`，PARSED / FAILED 即停；失败记录原因，`/reparse` 重试 |

限制：单文件 ≤ 50MB；纯文本超 2MB 截断并在状态中标记。

## 4. 检索

**v1（本期）BM25**：query 分词 → 命中倒排 → BM25 打分 → topK（默认 8）；多库时分别检索后按分数合并。

返回结构（工具输出与调试接口同构）：

```json
{
  "source": "knowledge",
  "items": [
    { "kbId": "...", "kbName": "产品文档", "fileId": "...", "fileName": "PRD-v2.pdf",
      "heading": "第二章 需求范围", "text": "…原文片段…", "score": 12.3 }
  ]
}
```

**v1.5 混合检索**：调用已配置模型厂商的 embedding API（按 defaultModel 的 provider 推断，如 dashscope / openai 的 text-embedding）；向量 base64 存入 chunks.json 的 vectors 字段；检索时 BM25 与 cosine 双路各取 topK，RRF 融合。旧库无向量时自动退化为纯 BM25。

**v2 库导读（LLM Wiki 融合层）**：库内容变化后异步生成 `guide.md`（目录树 + 每主题摘要，LLM 通读 chunk 生成）；会话引用时将导读注入上下文头部，Agent 先读导读定位主题、再用工具检索原文块。导读只做"地图"不做答案，溯源引用始终回原文。

## 5. Agent 集成

| 项 | 设计 |
|---|---|
| 请求扩展 | `POST /api/chat/stream` 增加 `knowledgeIds: string[]`（可空） |
| 工具注册 | ChatService 发送时按 knowledgeIds 动态 `addTool/removeTool`（注意 getTools 为防御性副本）；未引用时不注册，零开销 |
| 工具定义 | name=`knowledge_search`，参数 `{query: string}`，description 动态拼接库名（"在用户引用的资料库【产品文档】中检索…"），`isReadOnly = true`（不触发确认闸门） |
| 系统提示词 | 引用存在时追加："用户已引用资料库：{名称列表}。回答资料类问题前先使用 knowledge_search 检索，引用内容需标注来源文件与章节" |
| 会话记忆 | 只记 `[资料库: 名称]` 占位符（对齐附件占位符策略），不注入全文 |

## 6. API 设计

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/knowledge` | 库列表（含文件数 / 大小统计） |
| POST | `/api/knowledge` | 创建库 `{name, description}` |
| PATCH | `/api/knowledge/{kbId}` | 重命名 / 改描述 |
| DELETE | `/api/knowledge/{kbId}` | 删除库（物理删除，前端 confirm） |
| GET | `/api/knowledge/{kbId}/tree` | 文件树 |
| POST | `/api/knowledge/{kbId}/folders` | 新建文件夹 `{name, parentId}` |
| PATCH | `/api/knowledge/{kbId}/nodes/{nodeId}` | 重命名 / 移动（改 parentId） |
| DELETE | `/api/knowledge/{kbId}/nodes/{nodeId}` | 删除节点（文件夹连带子树与 chunks） |
| POST | `/api/knowledge/{kbId}/files` | multipart 多文件上传（`parentId` 可选），立即返回 fileId 列表 |
| GET | `/api/knowledge/{kbId}/files/{fileId}/status` | 解析状态轮询 |
| POST | `/api/knowledge/{kbId}/files/{fileId}/reparse` | 失败重试 |
| GET | `/api/knowledge/{kbId}/files/{fileId}/preview` | 原文预览（对齐 ConfigController 预览语义：文本 / image / binary） |
| POST | `/api/knowledge/search` | `{query, kbIds[]}` 检索调试接口 |

## 7. 前端设计（qualia-code-ui）

**入口**：side-rail 新增「资料库」图标（fa-database）→ 聊天区整栏切换视图（与欢迎页 / 工作区浏览器同层机制）。

**视图布局**：

```
┌─────────────┬──────────────────────────────────────┐
│ 知识库列表   │  产品文档 ▾               [上传][新建文件夹] │
│ ● 产品文档   │  📁 需求说明                           │
│   12 文件    │     📄 PRD-v2.pdf     ✅ 已解析  2.1MB  │
│ ○ 竞品调研   │  📁 会议纪要                           │
│ [+ 新建库]  │     📄 1012例会.docx  ⟳ 解析中          │
└─────────────┴──────────────────────────────────────┘
```

| 组件 | 职责 |
|---|---|
| `KnowledgeView.vue` | 左列库列表（卡片 + 新建库）+ 右列详情；右键/悬浮出重命名、删除 |
| 文件树 | 复用 TreeNode 交互；状态徽标：`⟳ 解析中` / `✅` / `⚠ 失败·重试`；行内操作：预览 / 重试 / 删除 |
| `stores/knowledge.ts` | 列表 / 树 / 上传队列 / 状态轮询 |
| InputArea 引用 | 技能 chips 旁「资料库」按钮 → 下拉多选（勾选）→ chips 回显 → 发送带 knowledgeIds |
| i18n | zh-CN / en 同步；双主题沿用 CSS 变量（石墨日间 / 暗色，冷雾蓝 accent） |

## 8. 边界与细节

- 树与索引写入一律「临时文件 + 原子替换」，避免半写损坏
- 删除文件夹连带删除子树全部文件的 chunks；删除库二次确认（显示文件数）
- 删除/重命名正在解析中的文件：队列内跳过该任务
- 工具为只读，不触发文件修改确认闸门；回答溯源依赖工具返回的结构化 JSON（fileName + heading），现有步骤流自动渲染工具卡片
- 多会话并发解析共享同一单线程队列（全局唯一 KnowledgeService 实例）

## 9. 里程碑

| 阶段 | 范围 | 验证 |
|---|---|---|
| M1 后端 | 存储 + 解析管线 + BM25 + 全部 API + KnowledgeSearchTool | curl 全接口 / 调试检索接口 |
| M2 前端 | 资料库视图 + 上传 + 轮询 + 预览 | type-check + 静态走查 |
| M3 集成 | 输入区引用 chips + Agent 工具注册 + 提示词注入 + 工具卡片溯源 | 会话内实测（用户自测） |
| M4 增强（另立项） | v1.5 向量 hybrid；v2 LLM 库导读 | — |
