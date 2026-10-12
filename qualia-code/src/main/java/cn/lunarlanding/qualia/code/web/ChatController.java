package cn.lunarlanding.qualia.code.web;

import com.alibaba.fastjson.JSON;
import cn.lunarlanding.qualia.core.agent.CallOptions;
import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;
import cn.lunarlanding.qualia.core.memory.MemoryMessage;
import cn.lunarlanding.qualia.core.model.chat.Attachment;
import cn.lunarlanding.qualia.code.service.AttachmentService;
import cn.lunarlanding.qualia.code.service.ChatService;
import cn.lunarlanding.qualia.code.service.ChatService.SessionInfo;
import cn.lunarlanding.qualia.code.service.WorkspaceDataStore;
import cn.lunarlanding.qualia.code.service.WorkspaceHistory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天 API
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /**
     * 每次现取单例，不缓存引用：会话激活（require）会按绑定解析 root 并可能重建实例，
     * 缓存会导致 Controller 持旧实例继续读旧工作区
     */
    private ChatService chatService() {
        return ChatService.getInstance();
    }

    /**
     * 创建新会话（默认工作区起步，是否绑定由用户随后显式选择）
     */
    @PostMapping("/sessions")
    public ResponseEntity<SessionInfo> createSession(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        SessionInfo session = chatService().createSession(title);
        return ResponseEntity.ok(session);
    }

    /**
     * 获取全部会话（跨项目历史库，归属见 workspace 字段，分组交给前端）
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getSessions() {
        return ResponseEntity.ok(chatService().getSessions());
    }

    /**
     * 会话上下文视图数据（输入框底部只读状态条：估算占用 / 消息数 / 上轮用量 / 窗口百分比）
     */
    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> getContext(@RequestParam String sessionId) {
        return ResponseEntity.ok(chatService().getContextInfo(sessionId));
    }

    /**
     * 近 N 日每日 token 用量统计
     */
    @GetMapping("/stats/tokens")
    public ResponseEntity<List<Map<String, Object>>> getTokenStats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(chatService().getDailyTokenStats(Math.min(Math.max(days, 1), 90)));
    }

    /**
     * 获取单个会话
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionInfo> getSession(@PathVariable String sessionId) {
        SessionInfo session = chatService().getSessionInfo(sessionId);
        return session == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(session);
    }

    /**
     * 绑定 / 解绑当前会话的工作区（binding.json 运行期唯一写入入口，信任边界）
     *
     * <p>body: {@code {"workspace": "D:\\Projects\\demo"}} 绑定；缺省、空串或 null 即解绑回默认工作区；
     * {@code create: true} 时目录不存在会先创建（对齐旧 /switch 的创建并打开）。
     * 流式对话进行中返回 409 互斥；目录不存在且未带 create 返回 400。</p>
     */
    @PostMapping("/sessions/{sessionId}/bind")
    public ResponseEntity<Map<String, Object>> bind(@PathVariable String sessionId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return ResponseEntity.badRequest().body(Map.of("error", "会话标识非法"));
        }
        if (ChatService.isStreaming()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "有对话正在进行，请等待完成后再变更绑定"));
        }
        Object rawWs = body == null ? null : body.get("workspace");
        String ws = rawWs instanceof String ? (String) rawWs : null;
        // create 可能是 boolean 字面量或字符串，统一字符串化后解析（null → "null" → false）
        boolean create = Boolean.parseBoolean(String.valueOf(body == null ? null : body.get("create")));
        Path target = null;
        if (ws != null && !ws.isBlank()) {
            try {
                target = Path.of(ws).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "路径格式非法"));
            }
            if (!Files.isDirectory(target)) {
                if (!create) {
                    return ResponseEntity.badRequest().body(Map.of("error", "目录不存在"));
                }
                try {
                    Files.createDirectories(target);
                } catch (IOException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "目录创建失败：" + e.getMessage()));
                }
            }
        }
        // 锁定校验：已产生对话的会话工作区状态冻结——绑定/换绑/解绑均拒绝（无消息时可随意调整）
        if (!chatService().getSessionHistory(sessionId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "会话已产生对话，工作区已锁定，不能更改"));
        }
        if (target != null) WorkspaceHistory.record(target); // 最近打开列表（对齐旧 /switch）
        WorkspaceDataStore.writeBinding(sessionId, target); // null = 删 binding.json
        ChatService.require(sessionId);   // root 变化时重建实例 + currentWorkspace 镜像跟随
        SessionInfo session = chatService().getSessionInfo(sessionId);
        if (session == null) {
            // 新会话（尚无消息）buildSessionInfo 返回 null：构造轻量条目保证 workspace 回传
            //（对齐 createSession 的轻量构造，否则前端拿到空 session 无法回显）
            session = new SessionInfo(sessionId, "新会话", System.currentTimeMillis());
            session.workspace = target == null ? null : target.toString();
        }
        return ResponseEntity.ok(Map.of("success", true, "session", session));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        boolean deleted = chatService().deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("success", deleted));
    }

    /**
     * 重命名会话（body: {"title": "..."}，空标题恢复自动标题）
     */
    @PutMapping("/sessions/{sessionId}/title")
    public ResponseEntity<Map<String, Object>> renameSession(@PathVariable String sessionId,
                                                             @RequestBody(required = false) Map<String, String> body) {
        chatService().renameSession(sessionId, body != null ? body.get("title") : null);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 发送消息（SSE 流式响应）- GET方式，支持EventSource
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) String model,
            @RequestParam(required = false, defaultValue = "false") boolean readOnly) {

        System.out.println("[SSE] New connection: sessionId=" + sessionId + ", message=" + message
                + ", model=" + model + ", readOnly=" + readOnly);
        return startStream(sessionId, message, model, null, readOnly);
    }

    /** 单次消息最多携带附件数 */
    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 4;

    /**
     * 发送消息（SSE 流式响应）- POST方式，支持携带附件（图片直传视觉模型，文档解析注入）
     *
     * <p>上传与发送解耦：前端先调 POST /api/attachments 拿回执 ID，发送时只携带 attachmentIds。
     * body: {@code {sessionId, message, model?, attachmentIds?: [uuid], readOnly?: boolean}}。</p>
     */
    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatPost(@RequestBody Map<String, Object> body) {
        String sessionId = body.get("sessionId") == null ? null : body.get("sessionId").toString();
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        String model = body.get("model") == null ? null : body.get("model").toString();
        // 只读问答模式：仅查询类工具可用（Boolean / 字符串 "true" 均识别）
        boolean readOnly = "true".equalsIgnoreCase(String.valueOf(body.get("readOnly")));

        System.out.println("[SSE] New POST connection: sessionId=" + sessionId + ", message=" + message
                + ", model=" + model + ", readOnly=" + readOnly);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        if (sessionId == null || sessionId.isEmpty()) {
            return failFast(emitter, "sessionId 不能为空");
        }
        Object rawIds = body.get("attachmentIds");
        boolean hasIds = rawIds instanceof List<?> list && !list.isEmpty();
        if (message.isEmpty() && !hasIds) {
            return failFast(emitter, "消息内容与附件不能同时为空");
        }
        List<Attachment> attachments = loadAttachments(sessionId, rawIds);
        if (hasIds && (attachments == null || attachments.isEmpty())) {
            return failFast(emitter, "附件数量超出限制（最多4个）或加载失败，请重新上传");
        }
        return startStream(sessionId, message, model,
                attachments == null || attachments.isEmpty() ? null : attachments, readOnly);
    }

    /**
     * 按回执 ID 从附件仓库加载附件，还原为 core 层 Attachment。
     * 无附件请求返回 null（走纯文本链路）；数量超限返回空列表（由调用方报错）。
     */
    private List<Attachment> loadAttachments(String sessionId, Object rawIds) {
        if (!(rawIds instanceof List<?>) || ((List<?>) rawIds).isEmpty()) {
            return null;
        }
        List<?> ids = (List<?>) rawIds;
        if (ids.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            return List.of();
        }
        List<String> idList = ids.stream().map(String::valueOf).collect(Collectors.toList());
        return AttachmentService.getInstance().load(sessionId, idList);
    }

    /** 参数校验失败时推送错误并结束流 */
    private SseEmitter failFast(SseEmitter emitter, String reason) {
        try {
            emitter.send(SseEmitter.event().data("发生错误: " + reason));
        } catch (IOException ignored) {
        }
        emitter.complete();
        return emitter;
    }

    /**
     * 公共推流入口：绑定 SSE 回调、切换模型（可选）、订阅响应流并逐条推送
     *
     * @param attachments 随消息直传的附件列表，可为 null
     */
    private SseEmitter startStream(String sessionId, String message, String model, List<Attachment> attachments, boolean readOnly) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 并发流防护：全局同时只允许一条流（重建实例会让进行中的对话上下文错乱，此处兜底）
        if (ChatService.isStreaming()) {
            return failFast(emitter, "已有对话正在进行，请等待完成后再发送");
        }

        // 会话激活：解析绑定 root（未绑定 → 派生沙箱）并确保 Agent 实例与之匹配
        ChatService.require(sessionId);

        // 活跃流记账：绑定变更前据此互斥，流终止（完成/出错）时销账
        ChatService.beginStream();

        // 设置回调
        emitter.onCompletion(() -> System.out.println("[SSE] Connection completed"));
        emitter.onTimeout(() -> {
            System.out.println("[SSE] Connection timeout");
            emitter.complete();
        });
        emitter.onError(ex -> System.out.println("[SSE] Connection error: " + ex.getMessage()));

        // 在单独的线程中处理聊天逻辑
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // 如果指定了模型，切换模型
                if (model != null && !model.isEmpty()) {
                    chatService().switchModel(model);
                }

                // 使用流式方式获取AI响应（带附件时走附件重载；readOnly 组装为会话选项透传）
                CallOptions options = readOnly ? CallOptions.readOnly() : null;
                Flux<AgentResponse> responseFlux = attachments != null && !attachments.isEmpty()
                        ? chatService().sendMessageFlux(sessionId, message, attachments, options)
                        : chatService().sendMessageFlux(sessionId, message, null, options);

                // 订阅流式响应并实时发送
                responseFlux.subscribe(
                    agentResponse -> {
                        try {
                            String data = JSON.toJSONString(agentResponse);
                            System.out.println("[SSE] Sending: " + data);
                            emitter.send(SseEmitter.event().data(data));
                        } catch (IOException e) {
                            System.err.println("[SSE] Send error: " + e.getMessage());
                        }
                    },
                    error -> {
                        System.err.println("[SSE] Stream error: " + error.getMessage());
                        ChatService.endStream();
                        try {
                            String errorData = JSON.toJSONString(Map.of("error", error.getMessage()));
                            emitter.send(SseEmitter.event().data(errorData));
                        } catch (IOException ex) {
                            // ignore
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        System.out.println("[SSE] Stream completed");
                        ChatService.endStream();
                        try {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                        } catch (IOException e) {
                            // ignore
                        }
                        emitter.complete();
                    }
                );
            } catch (Exception e) {
                System.err.println("[SSE] Error: " + e.getMessage());
                ChatService.endStream();
                try {
                    emitter.send(SseEmitter.event().data("发生错误: " + e.getMessage()));
                } catch (IOException ex) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });
    
        // 设置超时和完成回调
        emitter.onTimeout(() -> {
            System.out.println("[SSE] Timeout");
            emitter.complete();
        });
            
        return emitter;
    }

    /**
     * 获取会话消息历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable String sessionId) {
        List<MemoryMessage> messages = chatService().getSessionHistory(sessionId);
        List<Map<String, Object>> result = messages.stream()
                .map(msg -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("role", msg.getRole().name().toLowerCase());
                    map.put("content", msg.getContent());
                    if (msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
                        map.put("attachments", msg.getAttachments());
                    }
                    if (msg.getSteps() != null && !msg.getSteps().isEmpty()) {
                        map.put("steps", msg.getSteps());
                    }
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
