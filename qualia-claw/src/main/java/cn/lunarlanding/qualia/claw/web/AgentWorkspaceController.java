package cn.lunarlanding.qualia.claw.web;

import cn.lunarlanding.qualia.claw.ClawAgentDefinition;
import cn.lunarlanding.qualia.claw.service.AgentRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体工作区文件 API：只读浏览，严格限制在该智能体自己的工作区内
 *
 * 相对路径一律基于工作区根目录解析并规范化，防止 ../ 越权访问；
 * 仅提供「列目录」与「读文本内容」两类能力，不做任何写操作。
 */
@RestController
@RequestMapping("/api/agents/{agentId}/workspace")
public class AgentWorkspaceController {

    /** 文件预览大小上限（字节），超限降级提示 */
    private static final long MAX_PREVIEW_SIZE = 512 * 1024;

    /** 二进制嗅探采样长度（字节） */
    private static final int BINARY_SNIFF_LEN = 8192;

    private final AgentRegistry registry = AgentRegistry.getInstance();

    /**
     * 列出相对路径下的目录与文件（目录在前，按名称忽略大小写排序）
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles(@PathVariable String agentId,
                                                         @RequestParam(value = "path", required = false) String relPath) {
        ClawAgentDefinition def = registry.getDefinition(agentId);
        if (def == null) {
            return ResponseEntity.notFound().build();
        }
        Path root = Path.of(def.getWorkspacePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return ResponseEntity.badRequest().body(Map.of("message", "工作区目录不存在"));
        }
        Path target = resolveInside(root, relPath);
        if (target == null || !Files.isDirectory(target)) {
            return ResponseEntity.badRequest().body(Map.of("message", "目录不存在或路径越界"));
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                // Windows 系统特殊目录直接跳过
                if (name.startsWith("$")) {
                    continue;
                }
                boolean dir = Files.isDirectory(child);
                Map<String, Object> item = new HashMap<>();
                item.put("name", name);
                item.put("path", relativize(root, child));
                item.put("dir", dir);
                if (!dir) {
                    try {
                        item.put("size", Files.size(child));
                    } catch (IOException e) {
                        item.put("size", -1);
                    }
                }
                entries.add(item);
            }
        } catch (IOException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "无法读取目录: " + e.getMessage()));
        }
        entries.sort((a, b) -> {
            boolean da = (Boolean) a.get("dir");
            boolean db = (Boolean) b.get("dir");
            if (da != db) {
                return da ? -1 : 1;
            }
            return String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name")));
        });

        Map<String, Object> result = new HashMap<>();
        result.put("path", target.equals(root) ? "" : relativize(root, target));
        result.put("workspacePath", root.toString());
        result.put("entries", entries);
        return ResponseEntity.ok(result);
    }

    /**
     * 读取文本文件内容（超限或二进制文件降级返回 supported=false）
     */
    @GetMapping("/file")
    public ResponseEntity<Map<String, Object>> readFile(@PathVariable String agentId,
                                                        @RequestParam("path") String relPath) {
        ClawAgentDefinition def = registry.getDefinition(agentId);
        if (def == null) {
            return ResponseEntity.notFound().build();
        }
        Path root = Path.of(def.getWorkspacePath()).toAbsolutePath().normalize();
        Path target = resolveInside(root, relPath);
        if (target == null || !Files.isRegularFile(target)) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件不存在或路径越界"));
        }

        long size;
        try {
            size = Files.size(target);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "无法读取文件: " + e.getMessage()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("name", target.getFileName().toString());
        result.put("path", relativize(root, target));
        result.put("size", size);
        try {
            result.put("modified", Files.getLastModifiedTime(target).toMillis());
        } catch (IOException e) {
            result.put("modified", 0);
        }

        if (size > MAX_PREVIEW_SIZE) {
            result.put("supported", false);
            result.put("message", "文件超过预览大小上限（512 KB），请使用本地编辑器打开");
            return ResponseEntity.ok(result);
        }
        try {
            if (looksBinary(target)) {
                result.put("supported", false);
                result.put("message", "二进制文件暂不支持在线预览");
                return ResponseEntity.ok(result);
            }
            result.put("supported", true);
            result.put("content", Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "读取文件失败: " + e.getMessage()));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 相对路径解析：规范化后必须仍在工作区根目录内，否则返回 null
     */
    private Path resolveInside(Path root, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            return root;
        }
        Path p = root.resolve(relPath).toAbsolutePath().normalize();
        return p.startsWith(root) ? p : null;
    }

    /** 相对路径统一用 / 分隔，前端无需处理平台差异 */
    private String relativize(Path root, Path child) {
        return root.relativize(child).toString().replace('\\', '/');
    }

    /** 嗅探前 8KB：含 NUL 字节视为二进制 */
    private boolean looksBinary(Path file) throws IOException {
        byte[] buf = new byte[BINARY_SNIFF_LEN];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(buf, 0, buf.length);
        }
        for (int i = 0; i < read; i++) {
            if (buf[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
