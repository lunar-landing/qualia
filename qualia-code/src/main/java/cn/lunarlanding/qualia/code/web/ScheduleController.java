package cn.lunarlanding.qualia.code.web;

import cn.lunarlanding.qualia.code.service.ScheduleService;
import cn.lunarlanding.qualia.code.service.ScheduleService.ScheduleDef;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务 API：列表 / 创建 / 启停与改周期 / 删除 / 手动执行。
 * 忙时（对话进行中）手动执行返回 409，由前端提示。
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    /** 全部任务（含最近执行记录，前端任务中心与会话标注共用） */
    @GetMapping
    public ResponseEntity<List<ScheduleDef>> list() {
        return ResponseEntity.ok(ScheduleService.getInstance().list());
    }

    /**
     * 创建任务（body: {@code {prompt, cron, cronText?}}；运行会话由调度器每轮新建）。
     * cron 非法 → 400。
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        try {
            ScheduleDef def = ScheduleService.getInstance().create(
                    body.get("prompt"), body.get("cron"), body.get("cronText"));
            return ResponseEntity.ok(def);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 更新启停/周期（body: {@code {enabled?, cron?, cronText?}}，均可选；改周期以 now 重算下次运行） */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Boolean enabled = body.get("enabled") == null ? null
                : Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        String cron = str(body.get("cron"));
        String cronText = str(body.get("cronText"));
        try {
            return ResponseEntity.ok(ScheduleService.getInstance().update(id, enabled, cron, cronText));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除任务 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean removed = ScheduleService.getInstance().delete(id);
        return ResponseEntity.ok(Map.of("success", removed));
    }

    /** 手动立即执行一轮（不改变周期；对话进行中 → 409） */
    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, Object>> runNow(@PathVariable String id) {
        try {
            ScheduleService.getInstance().runNow(id);
            return ResponseEntity.accepted().body(Map.of("success", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
