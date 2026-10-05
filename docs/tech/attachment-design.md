# 统一附件方案（图片 + 文档）

> 状态：已评审定稿，实施中。
> 范围：一期本地同步解析。`read_attachment` 回看工具与扫描件 Mineru 兜底为二期，另行设计。

## 1. 目标

将聊天中的图片与文档附件（PDF / Word / 文本类）抽象为**统一的附件模型**：上传与发送解耦，前端上传文件换取回执（ID），发消息只携带 ID；后端凭 ID 取内容，图片走模型视觉通道，文档解析为文字注入。

| | 图片 | 文档 |
|---|---|---|
| 模型消费方式 | base64 直传视觉模型（image_url 内容块） | 解析正文以文字边界块注入 |
| 解析动作 | 无 | 本地解析（秒级） |
| 记忆形态 | `[图片: name]` 占位符 | `[附件: name]` 占位符 |
| 主模型要求 | 需视觉能力 | 纯文本模型即可 |

## 2. 流程

```
用户选文件/粘贴截图
  → POST /api/attachments（multipart 上传，按类型分流）
      图片：字节落盘，同步返回 ready
      文本类（txt/md/csv 等）：直读文本（UTF-8 失败回退 GBK），返回 ready
      PDF：PdfDocumentParser（PDFBox）解析，返回 ready
      Word（.docx）：WordDocumentParser（POI）解析，返回 ready
      扫描版 PDF / .doc / 超限：返回 failed + 原因
  → 前端显示附件 chip（图片缩略图 / 文档文件名），持回执待发
  → POST /api/chat/stream，body 携带 attachmentIds[]
  → 后端按 ID 从附件仓库加载，还原 core Attachment
      图片 → 磁盘字节现场编码 base64 一次
      文档 → 读解析文本
  → ReActAgent 组装：
      当轮消息：图片 → image_url 内容块；文档 → "--- 附件: name ---"边界块（≤50k 字符）
      会话记忆：统一只存占位符（base64 与正文均不入记忆）
  → 历史回显：[图片: x] 走现有缩略图逻辑；[附件: x] 渲染为文档 chip
```

## 3. 数据结构

### core：Attachment（取代 ChatImage）

```java
public record Attachment(String name, Type type, String dataUrl, String parsedContent) {
    public enum Type { IMAGE, DOCUMENT }
    public static Attachment image(String name, String dataUrl) { ... }
    public static Attachment document(String name, String parsedContent) { ... }
}
```

### 上传回执（AttachmentController 返回）

```json
{ "id": "uuid", "name": "report.pdf", "type": "document", "status": "ready" }
```

失败时：`{ "status": "failed", "error": "原因" }`。

### 发送消息 body（POST /api/chat/stream）

```json
{ "sessionId": "...", "message": "...", "model": "...", "attachmentIds": ["uuid"] }
```

原 `images` 字段废弃（前后端同批发布，不留兼容层）。

## 4. 附件仓库（磁盘）

```
{工作区}/.qualia/sessions/{sessionId}/attachments/{attachmentId}/
    meta.json    {id, name, type, contentType, size, parsedChars, createdAt}
    payload      图片 = 原始字节(payload.bin)；文档 = 解析文本(payload.md)
```

会话域优先布局：附件归属会话资源目录（`sessions/{sessionId}/`），生命周期与会话一致，未来会话级资源（导出、缓存等）同域存放。

- 同一附件可随多条消息发送；删除会话时整目录清理
- 旧图片直传方案的 `.qualia/images/` 落盘与 `GET /api/chat/images/**` 端点已随统一附件仓库废弃删除，历史图片经 `GET /api/attachments/{sessionId}/file/{name}` 回显

## 5. 限制

| 项 | 值 |
|---|---|
| 单次消息附件数 | ≤ 4 |
| 图片单文件 | ≤ 5MB（png/jpeg/gif/webp） |
| 文档单文件 | ≤ 20MB |
| 注入当轮的正文 | ≤ 50,000 字符（超出截断并标注） |
| 支持类型 | 图片 4 种；txt/md/markdown/csv/json/xml/yml/yaml/log/代码类；.pdf；.docx |

明确不支持（报错说明）：扫描版 PDF（无文字层）、.doc 老格式、其他二进制类型。

## 6. 改动清单

### qualia-core

| 文件 | 动作 |
|---|---|
| `core/model/chat/Attachment.java` | 新建（见 3） |
| `core/agent/Agent.java` | 三参 `callStream` 默认方法签名改 `List<Attachment>` |
| `core/agent/ReActAgent.java` | 三参覆写分流：记忆统一占位符；`initializeMessages` 图片→image_url part、文档→text 边界块 part（有图片时）或拼入纯文本消息（无图片时） |
| `core/model/chat/ChatImage.java` | 删除 |
| `core/parser/WordDocumentParser.java` | 新建，实现 DocumentParser（.docx → 正文 Document，表格单元格拼接） |
| `pom.xml` | 新增 poi-ooxml 依赖 |

### qualia-code

| 文件 | 动作 |
|---|---|
| `service/AttachmentService.java` | 新建：仓库读写、类型分流解析、按 ID 加载还原 Attachment、会话清理 |
| `web/AttachmentController.java` | 新建：POST /api/attachments（multipart，sessionId 参数校验防穿越）、GET /api/attachments/{id} |
| `web/ChatController.java` | body 改收 attachmentIds；删除 parseImages/saveImages/DATA_URL_EXT_PATTERN（图片 base64 编码职责移交 AttachmentService）；回显端点及其依赖（sanitizeImageName/IMAGE_MEDIA_TYPES/chatImagesDir）保留 |
| `service/ChatService.java` | 三参 sendMessageFlux 签名改 Attachment；deleteSession 联动清理附件目录 |

### 前端（static/）

| 位置 | 动作 |
|---|---|
| index.html 输入区 | 附件按钮与文件选择框（accept 扩展文档类型） |
| index.html `addImageFiles` → `addAttachmentFiles` | FileReader 转 base64 改为 FormData 上传拿回执 |
| index.html `renderImagePreview` → `renderAttachmentPreview` | 图片缩略图（本地 objectURL）+ 文档 chip 双形态 |
| index.html `sendMessage` | pendingImages → pendingAttachments（存回执），body 改 attachmentIds，用户气泡双形态渲染 |
| index.html `renderUserContent` | 追加 `[附件: x]` → 文档 chip 替换规则 |
| index.css | 附件 chip、失败态、历史 chip 样式（含浅色主题） |

## 7. 二期展望（不在本次范围）

- `read_attachment` 工具：模型按需读取历史附件正文（依赖 RuntimeContext 注入 sessionId，单独设计）
- 扫描件 Mineru 解析：启用异步状态（parsing → ready）与轮询端点，客户端需扩展文件直传能力
- 附件原件落盘下载、图片版 ReadImageTool
