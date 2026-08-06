package com.lunarlanding.qualia.claw.web;

import com.lunarlanding.qualia.claw.ClawAgentDefinition;
import com.lunarlanding.qualia.claw.service.AgentRegistry;
import com.lunarlanding.qualia.claw.service.ClawAgentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体 API：列表 / 创建 / 编辑 / 删除
 *
 * 每个智能体是一个独立工位（绑定自己的工作区），
 * 创建/编辑/删除实时持久化到 ~/.qualia/claw/config.json
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRegistry registry = AgentRegistry.getInstance();

    /**
     * 智能体列表（附带各自的流式对话状态）
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAgents() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClawAgentDefinition def : registry.list()) {
            result.add(toView(def));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 创建智能体（工作区由后端固定在 ~/.qualia/claw/workspaces/{名称} 自动生成）
     *
     * 请求体：{ name, emoji, role, model, skills[], mcpServers[] }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAgent(@RequestBody Map<String, Object> body) {
        try {
            ClawAgentDefinition def = registry.create(str(body, "name"), str(body, "emoji"),
                    str(body, "role"), str(body, "model"),
                    strList(body, "skills"), strList(body, "mcpServers"));
            return ResponseEntity.ok(Map.of("success", true, "agent", toView(def)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 更新智能体（名称/表情/角色/模型/技能与 MCP 引用白名单，缺省字段保持不变；工作区固定不可改）
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAgent(@PathVariable String id,
                                                           @RequestBody Map<String, Object> body) {
        try {
            ClawAgentDefinition def = registry.update(id,
                    str(body, "name"), str(body, "emoji"), str(body, "role"), str(body, "model"),
                    strList(body, "skills"), strList(body, "mcpServers"));
            return ResponseEntity.ok(Map.of("success", true, "agent", toView(def)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "code", "BUSY", "message", e.getMessage()));
        }
    }

    /**
     * 删除智能体（只移除工位定义，不删除工作区文件与历史会话）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAgent(@PathVariable String id) {
        try {
            boolean deleted = registry.delete(id);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "code", "BUSY", "message", e.getMessage()));
        }
    }

    /**
     * 定义 + 运行态（streaming）合成前端视图
     */
    private Map<String, Object> toView(ClawAgentDefinition def) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", def.getId());
        view.put("name", def.getName());
        view.put("emoji", def.getEmoji());
        view.put("role", def.getRole());
        view.put("workspacePath", def.getWorkspacePath());
        view.put("model", def.getModel());
        view.put("skills", def.getSkills());
        view.put("mcpServers", def.getMcpServers());
        view.put("createdAt", def.getCreatedAt());
        ClawAgentService service = registry.getService(def.getId());
        view.put("streaming", service != null && service.isStreaming());
        return view;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v instanceof String s ? s : null;
    }

    /**
     * 解析字符串数组字段；字段缺失返回 null（区别于空数组：null = 引用全部/保持不变）
     */
    private static List<String> strList(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        if (!(v instanceof List<?> list)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s && !s.isBlank()) {
                result.add(s.trim());
            }
        }
        return result;
    }
}
