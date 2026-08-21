package cn.lunarlanding.qualia.code.web;

import com.alibaba.fastjson.JSON;
import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;
import cn.lunarlanding.qualia.core.memory.MemoryMessage;
import cn.lunarlanding.qualia.code.WebApplication;
import cn.lunarlanding.qualia.code.service.ChatService;
import cn.lunarlanding.qualia.code.service.ChatService.SessionInfo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天 API
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /**
     * 每次现取单例，不缓存引用：切换 workspace 会重建 ChatService 实例，
     * 缓存会导致 Controller 持旧实例继续读旧工作区
     */
    private ChatService chatService() {
        return ChatService.getInstance(WebApplication.getCurrentWorkspace());
    }

    /**
     * 启动时未绑定工作区（等待前端强制选择）：读接口返回空、写接口拒绝，
     * 避免 ChatService 拿 null 路径初始化报 500
     */
    private boolean noWorkspace() {
        return WebApplication.getCurrentWorkspace() == null;
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<SessionInfo> createSession(@RequestBody(required = false) Map<String, String> body) {
        if (noWorkspace()) {
            return ResponseEntity.badRequest().build();
        }
        String title = body != null ? body.get("title") : null;
        SessionInfo session = chatService().createSession(title);
        return ResponseEntity.ok(session);
    }

    /**
     * 获取所有会话
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getSessions() {
        if (noWorkspace()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(chatService().getSessions());
    }

    /**
     * 近 N 日每日 token 用量统计
     */
    @GetMapping("/stats/tokens")
    public ResponseEntity<List<Map<String, Object>>> getTokenStats(
            @RequestParam(defaultValue = "30") int days) {
        if (noWorkspace()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(chatService().getDailyTokenStats(Math.min(Math.max(days, 1), 90)));
    }

    /**
     * 获取单个会话
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionInfo> getSession(@PathVariable String sessionId) {
        if (noWorkspace()) {
            return ResponseEntity.notFound().build();
        }
        List<SessionInfo> sessions = chatService().getSessions();
        return sessions.stream()
                .filter(s -> s.id.equals(sessionId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        if (noWorkspace()) {
            return ResponseEntity.badRequest().build();
        }
        boolean deleted = chatService().deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("success", deleted));
    }

    /**
     * 发送消息（SSE 流式响应）- GET方式，支持EventSource
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) String model) {

        System.out.println("[SSE] New connection: sessionId=" + sessionId + ", message=" + message + ", model=" + model);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 未选择工作区时直接回错（正常流程下前端强制弹窗拦截，此处为直接访问接口的兜底）
        if (noWorkspace()) {
            try {
                emitter.send(SseEmitter.event().data("发生错误: 尚未选择工作区"));
            } catch (IOException ignored) {
            }
            emitter.complete();
            return emitter;
        }

        // 活跃流记账：工作区切换前据此互斥，流终止（完成/出错）时销账
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
                
                // 使用流式方式获取AI响应
                Flux<AgentResponse> responseFlux = chatService().sendMessageFlux(sessionId, message);

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
        if (noWorkspace()) {
            return ResponseEntity.ok(List.of());
        }
        List<MemoryMessage> messages = chatService().getSessionHistory(sessionId);
        List<Map<String, Object>> result = messages.stream()
                .map(msg -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("role", msg.getRole().name().toLowerCase());
                    map.put("content", msg.getContent());
                    if (msg.getSteps() != null && !msg.getSteps().isEmpty()) {
                        map.put("steps", msg.getSteps());
                    }
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
