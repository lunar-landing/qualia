# 技术方案：文件修改确认闸门（Human-in-the-Loop Approval）

> 状态：待实施
> 范围：WriteTool / ReplaceTool / DeleteTool 三个文件修改类工具
> 决策已确认：仅管显式文件工具（Bash 暂不纳入）；5 分钟无响应视为拒绝

## 一、背景与目标

Agent 执行本地文件修改操作（写 / 编辑 / 删除）时完全静默执行，用户无感知也无干预机会。本方案在工具执行前插入**人工确认闸门**：前端弹出确认卡片，用户点「确认」放行、点「取消」或超时则拒绝。

核心语义：**拒绝不是报错**——拒绝信息作为一条普通 Observation 喂回模型，模型自行调整方案或询问用户意图，会话不中断。

## 二、数据流

```
模型发起 Write/Replace/Delete
  → ReActAgent 闸门: tool.requiresApproval()?
      → SSE 推 approval_request → 前端渲染确认卡片（流暂停，输入框锁定）
      → 阻塞等待 CompletableFuture（5 分钟超时 = 拒绝）
  用户点击 → POST /api/chat/approval/{id} → future.complete
  → 允许: tool.execute() 拿真实结果
  → 拒绝: 不执行，"用户拒绝了本次操作..." 作为 Observation 喂回模型
```

## 三、文件改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `qualia-core/.../tool/FunctionTool.java` | 加 `requiresApproval()` 默认 false |
| 2 | `qualia-core/.../tool/impl/file/WriteTool.java` | 覆写返回 true |
| 3 | `qualia-core/.../tool/impl/file/ReplaceTool.java` | 覆写返回 true |
| 4 | `qualia-core/.../tool/impl/file/DeleteTool.java` | 覆写返回 true |
| 5 | `qualia-core/.../tool/approval/ToolApprovalRequest.java` | **新增**，审批请求数据 + 摘要生成 |
| 6 | `qualia-core/.../tool/approval/ToolApprovalHandler.java` | **新增**，审批处理器函数式接口 |
| 7 | `qualia-core/.../agent/spec/AgentResponse.java` | 加 `approval` 字段 |
| 8 | `qualia-core/.../agent/ReActAgent.java` | handler 字段 + setter + 闸门逻辑 |
| 9 | `qualia-code/.../service/ChatService.java` | handler 实现 + pendingApprovals + 注入 |
| 10 | `qualia-code/.../web/ChatController.java` | `POST /approval/{id}` 决策端点 |
| 11 | `qualia-code/.../static/index.html` | SSE 分支 + 确认卡片 + 决策回传 |
| 12 | `qualia-code/.../static/index.css` | 审批卡片样式 |

## 四、具体代码

### 4.1 FunctionTool.java — 加审批声明（+6 行）

```java
    /**
     * 该工具执行前是否需要用户人工确认（文件修改/删除等破坏性操作返回 true）
     * Agent 在执行前检查此标志，未配置审批处理器时自动跳过
     */
    public boolean requiresApproval() {
        return false;
    }
```

### 4.2 三个文件工具 — 各 +5 行覆写

```java
    // WriteTool.java / ReplaceTool.java / DeleteTool.java 各自类内追加：
    @Override
    public boolean requiresApproval() {
        return true;
    }
```

### 4.3 新增 `tool/approval/ToolApprovalRequest.java`

```java
package cn.lunarlanding.qualia.core.tool.approval;

import cn.lunarlanding.qualia.core.tool.ToolCall;
import lombok.Data;

/**
 * 工具审批请求数据（随 approval_request 事件推送前端渲染确认卡片）
 */
@Data
public class ToolApprovalRequest {

    /** 审批单号（前端决策回传凭据） */
    private String id;
    private String sessionId;
    private String toolName;
    /** 一句话摘要，前端直接展示 */
    private String summary;
    /** 明细（path + 内容预览），前端折叠展示 */
    private Detail detail;

    @Data
    public static class Detail {
        private String path;
        /** 内容预览（write 为新内容、replace 为旧→新对照，delete 为空） */
        private String preview;
    }

    public static ToolApprovalRequest of(String sessionId, ToolCall call) {
        ToolApprovalRequest req = new ToolApprovalRequest();
        req.setId("apr_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        req.setSessionId(sessionId);
        req.setToolName(call.toolName());
        Detail d = new Detail();
        String path = String.valueOf(call.arguments().getOrDefault("path", ""));
        d.setPath(path);
        switch (call.toolName()) {
            case "write_file" -> {
                String content = String.valueOf(call.arguments().getOrDefault("content", ""));
                int lines = content.isEmpty() ? 0 : content.split("\n", -1).length;
                req.setSummary("写入 " + path + "（" + lines + " 行）");
                d.setPreview(truncate(content, 500));
            }
            case "replace" -> {
                String oldText = String.valueOf(call.arguments().getOrDefault("old_text", ""));
                String newText = String.valueOf(call.arguments().getOrDefault("new_text", ""));
                req.setSummary("编辑 " + path);
                d.setPreview("旧:\n" + truncate(oldText, 300) + "\n新:\n" + truncate(newText, 300));
            }
            case "delete_file" -> req.setSummary("删除 " + path);
            default -> req.setSummary(call.toolName() + " " + path);
        }
        req.setDetail(d);
        return req;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n…（已截断）";
    }
}
```

> 注意：case 分支的工具名以各工具实际注册 name 为准，实现时先 grep 确认 WriteTool / ReplaceTool / DeleteTool 的 `name` 字段值再对齐。

### 4.4 新增 `tool/approval/ToolApprovalHandler.java`

```java
package cn.lunarlanding.qualia.core.tool.approval;

/**
 * 工具审批处理器：阻塞等待用户决策；返回 false 表示拒绝（含超时）
 */
@FunctionalInterface
public interface ToolApprovalHandler {
    boolean awaitDecision(ToolApprovalRequest request);
}
```

### 4.5 AgentResponse.java — 加字段

```java
    /**
     * 审批请求（responseType = "approval_request" 时非空）
     */
    private cn.lunarlanding.qualia.core.tool.approval.ToolApprovalRequest approval;
```

（若类上已有 lombok `@Data` 则只加字段；否则按类内现有 getter/setter 风格补一对访问器）

### 4.6 ReActAgent.java — 字段 + 闸门

**字段区**（与其他字段并列）：

```java
    /** 工具审批处理器（null = 跳过审批，CLI 场景行为不变） */
    private volatile ToolApprovalHandler approvalHandler;

    public void setApprovalHandler(ToolApprovalHandler approvalHandler) {
        this.approvalHandler = approvalHandler;
    }
```

**闸门**（`runIteration` 工具循环内，替换原 `String toolResult = tool.execute(toolRequest.arguments());` 一行）：

```java
                // 执行工具（破坏性操作先过人工确认闸门）
                String toolResult;
                if (tool.requiresApproval() && approvalHandler != null) {
                    ToolApprovalRequest approvalReq = ToolApprovalRequest.of(sessionId, toolRequest);
                    AgentResponse approvalResponse = new AgentResponse();
                    approvalResponse.setResponseType("approval_request");
                    approvalResponse.setApproval(approvalReq);
                    emitter.next(approvalResponse);

                    boolean allowed = approvalHandler.awaitDecision(approvalReq);
                    if (allowed) {
                        toolResult = tool.execute(toolRequest.arguments());
                    } else {
                        logger.info("[ReActAgent] 工具 {} 被用户拒绝，作为观察反馈", toolRequest.toolName());
                        toolResult = "用户拒绝了对 " + approvalReq.getDetail().getPath()
                                + " 的 " + toolRequest.toolName()
                                + " 操作。请勿重试同一操作，询问用户意图或调整方案。";
                    }
                } else {
                    toolResult = tool.execute(toolRequest.arguments());
                }
```

### 4.7 ChatService.java — handler 实现 + 注入

**字段区**：

```java
    /** 待决策审批表：approvalId → Future（多会话并行安全，键控互不干扰） */
    private final Map<String, java.util.concurrent.CompletableFuture<Boolean>> pendingApprovals
            = new java.util.concurrent.ConcurrentHashMap<>();

    /** 审批超时（分钟）：超时视为拒绝 */
    private static final long APPROVAL_TIMEOUT_MINUTES = 5;
```

**initialize()**（`agent = new HarnessAgent(...)` 之后、`disableTools()` 之前）：

```java
        agent.setApprovalHandler(this::awaitApproval);
```

**新增方法**：

```java
    /**
     * 审批闸门实现：登记 Future 并阻塞等待前端决策，5 分钟超时视为拒绝
     */
    private boolean awaitApproval(ToolApprovalRequest req) {
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        pendingApprovals.put(req.getId(), future);
        try {
            return future.get(APPROVAL_TIMEOUT_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            logger.info("[ChatService] 审批 {} 超时（{} 分钟），视为拒绝", req.getId(), APPROVAL_TIMEOUT_MINUTES);
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            pendingApprovals.remove(req.getId());
        }
    }

    /** 取出待决策审批（Controller 决策回调用），不存在/已过期返回 null */
    public java.util.concurrent.CompletableFuture<Boolean> pollPendingApproval(String approvalId) {
        return pendingApprovals.get(approvalId);
    }
```

### 4.8 ChatController.java — 决策端点（+16 行）

```java
    /**
     * 工具审批决策（body: {"approved": true|false}）
     */
    @PostMapping("/approval/{approvalId}")
    public ResponseEntity<Map<String, Object>> decideApproval(@PathVariable String approvalId,
                                                              @RequestBody Map<String, Boolean> body) {
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        java.util.concurrent.CompletableFuture<Boolean> future = chatService().pollPendingApproval(approvalId);
        if (future == null || future.isDone()) {
            return ResponseEntity.ok(Map.of("success", false, "reason", "审批不存在或已过期"));
        }
        future.complete(approved);
        return ResponseEntity.ok(Map.of("success", true));
    }
```

（挂在现有 `/api/chat` 前缀下）

### 4.9 前端 index.html — SSE 分发分支

在现有 `responseType === 'step'` 分支后追加：

```js
                        } else if (data.responseType === 'approval_request' && data.approval) {
                            // 工具审批请求：渲染确认卡片并锁定输入
                            if (sid === currentSessionId) {
                                typing.style.display = 'none';
                                if (!state.activityEl) state.activityEl = createActivityMsg(state.msgId);
                                renderApprovalCard(state.activityEl, data.approval);
                            }
                            setApprovalWaiting(true);
```

### 4.10 前端 index.html — 新增三个函数（主脚本内）

```js
                // ===== 工具审批确认卡片 =====
                function renderApprovalCard(container, approval) {
                    const card = document.createElement('div');
                    card.className = 'approval-card';
                    card.id = 'apr-' + approval.id;
                    const iconMap = { write_file: 'fa-file-pen', replace: 'fa-file-code', delete_file: 'fa-trash-can' };
                    card.innerHTML = `
                        <div class="approval-head">
                            <i class="fas ${iconMap[approval.toolName] || 'fa-file-shield'}"></i>
                            <span class="approval-title">请求${approval.toolName === 'delete_file' ? '删除文件' : '修改文件'}</span>
                        </div>
                        <div class="approval-summary">${escapeHtml(approval.summary)}</div>
                        ${approval.detail && approval.detail.preview ? `
                        <details class="approval-detail">
                            <summary>查看内容</summary>
                            <pre>${escapeHtml(approval.detail.preview)}</pre>
                        </details>` : ''}
                        <div class="approval-actions">
                            <button class="approval-btn confirm">确认执行</button>
                            <button class="approval-btn cancel">取消</button>
                        </div>
                        <div class="approval-result" style="display:none"></div>
                    `;
                    card.querySelector('.confirm').addEventListener('click', () => decideApproval(card, approval.id, true));
                    card.querySelector('.cancel').addEventListener('click', () => decideApproval(card, approval.id, false));
                    container.appendChild(card);
                    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }

                async function decideApproval(card, approvalId, approved) {
                    const buttons = card.querySelectorAll('.approval-btn');
                    buttons.forEach(b => b.disabled = true);
                    let ok = false;
                    try {
                        const resp = await fetch(`/api/chat/approval/${approvalId}`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ approved })
                        });
                        ok = (await resp.json()).success;
                    } catch (e) { /* 网络失败按过期处理 */ }
                    const result = card.querySelector('.approval-result');
                    result.style.display = 'block';
                    result.textContent = !ok ? '审批已过期' : (approved ? '已确认执行' : '已取消');
                    if (ok && approved) card.classList.add('approved');
                    else card.classList.add('rejected');
                    buttons.forEach(b => b.remove());
                    const waiting = card.closest('.msg')
                        ?.querySelectorAll('.approval-card:not(.approved):not(.rejected)').length > 0;
                    setApprovalWaiting(waiting);
                }

                function setApprovalWaiting(waiting) {
                    const input = document.getElementById('messageInput');
                    const sendBtn = document.getElementById('sendBtn');
                    if (input) input.disabled = waiting;
                    if (sendBtn) sendBtn.disabled = waiting;
                }
```

> 注意：`messageInput` / `sendBtn` 以页面实际元素 id 为准，实现时先确认。

### 4.11 前端 index.css — 审批卡片样式（追加）

```css
/* ===== 工具审批确认卡片 ===== */
.approval-card {
    margin: 9px 0;
    padding: 12px 14px;
    border: 1px solid var(--border-color);
    border-radius: 10px;
    background: var(--bg-hover);
}
.approval-card.approved { border-color: rgba(74, 222, 128, 0.45); }
.approval-card.rejected { opacity: 0.65; }
.approval-head {
    display: flex; align-items: center; gap: 7px;
    font-size: 12.5px; font-weight: 600; color: var(--text-primary);
    margin-bottom: 7px;
}
.approval-head i { color: var(--warning, #f59e0b); }
.approval-summary { font-size: 12.5px; color: var(--text-secondary); margin-bottom: 8px; }
.approval-detail { margin-bottom: 8px; }
.approval-detail summary { font-size: 11.5px; color: var(--text-muted); cursor: pointer; }
.approval-detail pre {
    margin: 6px 0 0; padding: 8px 10px;
    background: var(--bg-codeblock); border-radius: 7px;
    font-size: 11px; max-height: 220px; overflow: auto;
    white-space: pre-wrap; word-break: break-all;
}
.approval-actions { display: flex; gap: 8px; }
.approval-btn {
    padding: 6px 16px; border-radius: 7px; border: none;
    font-size: 12px; font-weight: 600; cursor: pointer;
    transition: filter 0.15s, opacity 0.15s;
}
.approval-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.approval-btn.confirm { background: var(--th-lv3, #4ade80); color: #0d1117; }
.approval-btn.confirm:hover:not(:disabled) { filter: brightness(1.08); }
.approval-btn.cancel { background: var(--bg-active); color: var(--text-primary); }
.approval-btn.cancel:hover:not(:disabled) { filter: brightness(1.1); }
.approval-result { font-size: 12px; color: var(--text-secondary); }
.approval-card.approved .approval-result { color: var(--th-lv3, #4ade80); }
```

## 五、SSE 协议

新增事件类型 `approval_request`（AgentResponse 新字段，既有事件不受影响）：

```json
{
  "responseType": "approval_request",
  "approval": {
    "id": "apr_a1b2c3d4e5f6",
    "sessionId": "s-xxx",
    "toolName": "write_file",
    "summary": "写入 src/App.java（42 行）",
    "detail": { "path": "src/App.java", "preview": "内容前 500 字符…" }
  }
}
```

决策回传：`POST /api/chat/approval/{id}`，body `{"approved": true|false}`。

## 六、边界情况

| 场景 | 行为 |
|---|---|
| 确认执行 | 工具真实执行，Observation 为真实结果 |
| 取消 | 不执行，模型收到拒绝观察自行调整方案 |
| 5 分钟不响应 | Future 超时 → 拒绝，流恢复；迟到决策返回「审批不存在或已过期」 |
| 用户关页 / 断线 | 同超时路径，5 分钟后自动拒绝 |
| 多会话并行 | pendingApprovals 按 approvalId 键控；handler 无实例状态，不触碰单实例字段污染问题 |
| 一次响应多个工具调用 | for 循环内逐个过闸门，后续卡片排队等待前一个决策 |
| approve 后执行失败 | 真实错误文本作为 Observation，与无闸门时一致 |
| claw / CLI | handler 未注入（null）→ 闸门整体跳过，行为零变化 |
| 历史回放 | 确认卡片为内存型交互不持久化；历史里该轮仍是 ACTION/OBSERVATION 步骤对 |

## 七、实施顺序与验证

**顺序**：core 四件套（清单 1-7）→ ReActAgent 闸门（8）→ 服务层 / 端点（9-10）→ 前端（11-12）→ `mvn compile -pl qualia-core,qualia-code` 全量编译同步 target。

**验证清单**：

- [ ] 让模型改一个文件 → 卡片出现 → 点确认 → 文件内容真实变更
- [ ] 点取消 → 模型回复调整方案类内容，文件未变
- [ ] 超时路径：临时把 `APPROVAL_TIMEOUT_MINUTES` 调小验证自动拒绝，验证后改回 5
- [ ] 双会话并行各挂各的卡片，互不串扰
- [ ] 模型一次写两个文件 → 两张卡片顺序决策
- [ ] claw CLI 跑一轮写文件 → 无卡片直接执行（回归）
- [ ] 重开历史会话回放 → 该轮显示普通步骤对，无残留卡片
