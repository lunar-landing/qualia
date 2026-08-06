package com.lunarlanding.qualia.claw.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * 目录浏览 API：供创建/编辑智能体时选择工作区目录（只列子目录不列文件）
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceBrowseController {

    /**
     * 浏览服务端目录
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
}
