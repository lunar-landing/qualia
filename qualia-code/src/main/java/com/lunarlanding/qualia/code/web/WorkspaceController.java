package com.lunarlanding.qualia.code.web;

import com.lunarlanding.qualia.code.WebApplication;
import com.lunarlanding.qualia.code.service.ChatService;
import com.lunarlanding.qualia.code.service.WorkspaceHistory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区 API：查询当前工作区与历史、运行期切换
 *
 * 切换语义：更新 WebApplication 的路径引用 + 整体重建 ChatService，
 * 文件树/会话/技能等接口均实时读取当前路径，切换后自动指向新工作区。
 * 流式对话进行中时拒绝切换（前端按钮同步置灰，此处为兜底）。
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    /**
     * 当前工作区 + 最近打开列表
     *
     * current 为 null 表示启动时未绑定工作区，前端据此强制弹出选择弹窗
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getWorkspaceInfo() {
        Path current = WebApplication.getCurrentWorkspace();
        Map<String, Object> result = new HashMap<>();
        if (current != null) {
            result.put("current", Map.of(
                    "path", current.toAbsolutePath().toString(),
                    "name", current.getFileName() != null ? current.getFileName().toString() : current.toString()
            ));
        } else {
            result.put("current", null);
        }
        result.put("recent", WorkspaceHistory.list());
        result.put("streaming", ChatService.isStreaming());
        return ResponseEntity.ok(result);
    }

    /**
     * 浏览服务端目录（供前端目录选择器使用，只列子目录不列文件）
     *
     * path 为空时返回盘符列表 + 用户主目录快捷入口；
     * 否则返回该目录下的子目录（跳过隐藏目录）与上级路径。
     */
    @GetMapping("/browse")
    public ResponseEntity<Map<String, Object>> browse(@RequestParam(value = "path", required = false) String rawPath) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> dirs = new ArrayList<>();

        // 根入口：盘符 + 用户主目录
        if (rawPath == null || rawPath.isBlank()) {
            for (Path root : FileSystems.getDefault().getRootDirectories()) {
                dirs.add(Map.of("name", root.toString(), "path", root.toString()));
            }
            result.put("path", "");
            result.put("parent", null);
            result.put("dirs", dirs);
            result.put("home", System.getProperty("user.home"));
            return ResponseEntity.ok(result);
        }

        Path target;
        try {
            target = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "路径格式非法"));
        }
        if (!Files.isDirectory(target)) {
            return ResponseEntity.badRequest().body(Map.of("message", "目录不存在或不可访问"));
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
            for (Path child : stream) {
                try {
                    if (!Files.isDirectory(child) || Files.isHidden(child)) {
                        continue;
                    }
                    String name = child.getFileName().toString();
                    // Windows 下 Files.isHidden 对部分系统目录无效，补充按命名约定过滤
                    if (name.startsWith(".") || name.startsWith("$")) {
                        continue;
                    }
                    dirs.add(Map.of("name", name, "path", child.toString()));
                } catch (IOException | SecurityException ignore) {
                    // 单个子项无权限时跳过，不影响整体列表
                }
            }
        } catch (IOException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "无法读取该目录: " + e.getMessage()));
        }
        dirs.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));

        result.put("path", target.toString());
        // 盘符根目录的上级回到盘符列表（parent 置空串）
        result.put("parent", target.getParent() != null ? target.getParent().toString() : "");
        result.put("dirs", dirs);
        return ResponseEntity.ok(result);
    }

    /**
     * 切换工作区
     *
     * 请求体：{ path: 绝对路径, create: 目录不存在时是否创建 }
     * 目录不存在且未指定 create 时返回 code=NOT_FOUND，由前端给出「创建并打开」选项
     */
    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchWorkspace(@RequestBody Map<String, Object> body) {
        String rawPath = body.get("path") instanceof String s ? s.trim() : "";
        boolean create = Boolean.TRUE.equals(body.get("create"));

        if (rawPath.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "路径不能为空"));
        }

        Path target;
        try {
            target = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "路径格式非法"));
        }
        if (!target.isAbsolute()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请输入绝对路径"));
        }

        // 流式对话进行中禁止切换（重建服务会让进行中的对话上下文错乱）
        if (ChatService.isStreaming()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "code", "BUSY", "message", "有对话正在进行，请等待完成后再切换"));
        }

        if (Files.exists(target)) {
            if (!Files.isDirectory(target)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "该路径不是目录"));
            }
        } else if (create) {
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("success", false, "message", "创建目录失败: " + e.getMessage()));
            }
        } else {
            // 目录不存在：交给前端展示「创建并打开」选项
            return ResponseEntity.ok(Map.of("success", false, "code", "NOT_FOUND", "message", "目录不存在"));
        }

        Path current = WebApplication.getCurrentWorkspace();
        boolean same = current != null
                && current.toAbsolutePath().normalize().toString().equalsIgnoreCase(target.toString());

        if (!same) {
            WebApplication.setCurrentWorkspace(target);
            ChatService.switchWorkspace(target);
        }
        // 无论是否同一路径都刷新历史时间戳
        WorkspaceHistory.record(target);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "changed", !same,
                "workspace", Map.of(
                        "path", target.toString(),
                        "name", target.getFileName() != null ? target.getFileName().toString() : target.toString()
                )
        ));
    }
}
