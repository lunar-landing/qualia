package com.lunarlanding.qualia.claw.web;

import com.alibaba.fastjson.JSON;
import com.lunarlanding.qualia.claw.service.AgentRegistry;
import com.lunarlanding.qualia.claw.service.ClawAgentService;
import com.lunarlanding.qualia.claw.service.ClawAgentService.SessionInfo;
import com.lunarlanding.qualia.core.agent.spec.AgentResponse;
import com.lunarlanding.qualia.core.memory.MemoryMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天 API（智能体维度）：所有路由均挂在 /api/agents/{agentId}/chat 下，
 * 每个智能体的会话、历史、统计互相隔离
 */
@RestController
@RequestMapping("/api/agents/{agentId}/chat")
public class ChatController {

    private final AgentRegistry registry = AgentRegistry.getInstance();

    /**
     * 按路径取智能体服务，不存在返回 null（各接口自行兜底 404/空态）
     */
    private ClawAgentService service(String agentId) {
        return registry.getService(agentId);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<SessionInfo> createSession(@PathVariable String agentId,
                                                     @RequestBody(required = false) Map<String, String> body) {
        ClawAgentService svc = service(agentId);
        if (svc == null) {
            return ResponseEntity.badRequest().build();
        }
        String title = body != null ? body.get("title") : null;
        return ResponseEntity.ok(svc.createSession(title));
    }

    /**
     * 获取所有会话
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getSessions(@PathVariable String agentId) {
        ClawAgentService svc = service(agentId);
        if (svc == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(svc.getSessions());
    }

    /**
     * 近 N 日每日 token 用量统计
     */
    @GetMapping("/stats/tokens")
    public ResponseEntity<List<Map<String, Object>>> getTokenStats(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "30") int days) {
        ClawAgentService svc = service(agentId);
        if (svc == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(svc.getDailyTokenStats(Math.min(Math.max(days, 1), 90)));
    }

    /**
     * 获取单个会话
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionInfo> getSession(@PathVariable String agentId,
                                                  @PathVariable String sessionId) {
        ClawAgentService svc = service(agentId);
        if (svc == null) {
            return ResponseEntity.notFound().build();
        }
        return svc.getSessions().stream()
                .filter(s -> s.id.equals(sessionId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String agentId,
                                                             @PathVariable String sessionId) {
        ClawAgentService svc = service(agentId);
        if (svc == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean deleted = svc.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("success", deleted));
    }

    /**
     * 发送消息（SSE 流式响应）- GET方式，支持EventSource
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @PathVariable String agentId,
            @RequestParam String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) String model) {

        System.out.println("[SSE] New connection: agentId=" + agentId + ", sessionId=" + sessionId + ", message=" + message + ", model=" + model);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 智能体不存在时直接回错
        ClawAgentService svc = service(agentId);
        if (svc == null) {
            try {
                emitter.send(SseEmitter.event().data("发生错误: 智能体不存在"));
            } catch (IOException ignored) {
            }
            emitter.complete();
            return emitter;
        }

        // 活跃流记账：删除/编辑智能体前据此互斥，流终止（完成/出错）时销账
        svc.beginStream();

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
                    svc.switchModel(model);
                }

                // 使用流式方式获取AI响应
                Flux<AgentResponse> responseFlux = svc.sendMessageFlux(sessionId, message);

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
                        svc.endStream();
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
                        svc.endStream();
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
                svc.endStream();
                try {
                    emitter.send(SseEmitter.event().data("发生错误: " + e.getMessage()));
                } catch (IOException ex) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取会话消息历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable String agentId,
                                                                 @PathVariable String sessionId) {
        ClawAgentService svc = service(agentId);
        if (svc == null) {
            return ResponseEntity.ok(List.of());
        }
        List<MemoryMessage> messages = svc.getSessionHistory(sessionId);
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
