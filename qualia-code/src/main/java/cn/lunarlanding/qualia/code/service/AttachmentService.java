package cn.lunarlanding.qualia.code.service;

import cn.lunarlanding.qualia.code.WebApplication;
import cn.lunarlanding.qualia.core.model.chat.Attachment;
import cn.lunarlanding.qualia.core.parser.Document;
import cn.lunarlanding.qualia.core.parser.DocumentParser;
import cn.lunarlanding.qualia.core.parser.PdfDocumentParser;
import cn.lunarlanding.qualia.core.parser.WordDocumentParser;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 附件服务：上传解析与仓库管理。
 *
 * <p>附件按会话隔离落盘（{@code .qualia/sessions/{sessionId}/files/{id}/}），
 * 一个附件一个目录（meta.json 元信息 + payload 内容文件）。
 * 图片存原始字节（发送时现场编码 base64），文档存解析文本。
 * 附件与消息解耦：上传换取回执 ID，发消息只携带 ID。</p>
 */
public class AttachmentService {

    private static final AttachmentService INSTANCE = new AttachmentService();

    public static AttachmentService getInstance() {
        return INSTANCE;
    }

    private AttachmentService() {
    }

    /** 图片单文件上限（与聊天直传通道一致） */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 文档单文件上限 */
    private static final long MAX_DOCUMENT_SIZE = 20L * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "csv", "json", "xml", "yml", "yaml", "log",
            "properties", "sql", "html", "js", "ts", "java", "py", "go", "c", "cpp", "h", "sh", "bat");

    /** 上传回执：id + 名称 + 类型 + 状态（failed 时附 error） */
    public record AttachmentInfo(String id, String name, String type, String status, String error) {
        public static AttachmentInfo ok(String id, String name, String type) {
            return new AttachmentInfo(id, name, type, "ready", null);
        }

        public static AttachmentInfo fail(String name, String error) {
            return new AttachmentInfo(null, name, null, "failed", error);
        }
    }

    /** 附件仓库目录：{workspace}/.qualia/sessions/{sessionId}/files（会话域内，与聊天记录同目录聚合） */
    private Path repositoryDir(String sessionId) {
        Path sessionDir = WebApplication.getCurrentWorkspace()
                .resolve(".qualia").resolve("sessions").resolve(sessionId);
        migrateLegacyAttachmentsDir(sessionDir);
        return sessionDir.resolve("files");
    }

    /**
     * 旧版附件目录名迁移（幂等）：{sessionId}/attachments → {sessionId}/files。
     * 旧目录不存在时无操作；目标已存在（已迁移过）时逐项搬移不覆盖，搬空后删除旧目录。
     */
    private void migrateLegacyAttachmentsDir(Path sessionDir) {
        Path legacy = sessionDir.resolve("attachments");
        if (!Files.isDirectory(legacy)) {
            return;
        }
        Path target = sessionDir.resolve("files");
        try {
            if (Files.exists(target)) {
                // 目标已存在（极少见，可能上次迁移中断）：逐项搬移不覆盖
                try (var paths = Files.list(legacy)) {
                    for (Path entry : paths.toList()) {
                        Path dest = target.resolve(entry.getFileName().toString());
                        if (!Files.exists(dest)) {
                            Files.move(entry, dest);
                        }
                    }
                }
            } else {
                Files.move(legacy, target);
            }
            try (var paths = Files.list(legacy)) {
                if (paths.findAny().isEmpty()) {
                    Files.deleteIfExists(legacy);
                    System.out.println("[Attachment] 旧版附件目录已迁移: " + legacy + " -> " + target);
                }
            }
        } catch (IOException e) {
            // 迁移失败不阻断业务，下次访问重试（同盘 move 基本不会失败）
            System.err.println("[Attachment] 附件目录迁移失败（将在下次访问重试）: " + legacy + " - " + e.getMessage());
        }
    }

    /**
     * 存储并解析上传的附件，返回回执。
     * 解析失败或类型不支持时返回 failed 回执，不落盘。
     */
    public AttachmentInfo store(String sessionId, String fileName, byte[] bytes) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return AttachmentInfo.fail(fileName, "会话标识非法");
        }
        if (bytes == null || bytes.length == 0) {
            return AttachmentInfo.fail(fileName, "文件内容为空");
        }
        String safeName = sanitizeFileName(fileName);
        if (safeName == null) {
            return AttachmentInfo.fail(fileName, "文件名非法");
        }
        String extension = extensionOf(safeName);

        try {
            String type;
            String payloadFileName;
            if (IMAGE_EXTENSIONS.contains(extension)) {
                if (bytes.length > MAX_IMAGE_SIZE) {
                    return AttachmentInfo.fail(safeName, "图片超过 5MB 限制");
                }
                type = "image";
                payloadFileName = "payload.bin";
            } else {
                if (bytes.length > MAX_DOCUMENT_SIZE) {
                    return AttachmentInfo.fail(safeName, "文档超过 20MB 限制");
                }
                type = "document";
                payloadFileName = "payload.md";
            }

            String payloadText = null;
            byte[] payloadBytes;
            if (type.equals("image")) {
                payloadBytes = bytes;
            } else if (TEXT_EXTENSIONS.contains(extension)) {
                payloadText = DocumentParser.decodeText(bytes);
                payloadBytes = payloadText.getBytes(StandardCharsets.UTF_8);
            } else if (extension.equals("pdf")) {
                payloadText = joinDocuments(new PdfDocumentParser().parse(bytes));
                payloadBytes = payloadText.getBytes(StandardCharsets.UTF_8);
            } else if (extension.equals("docx")) {
                payloadText = joinDocuments(new WordDocumentParser().parse(bytes));
                payloadBytes = payloadText.getBytes(StandardCharsets.UTF_8);
            } else {
                return AttachmentInfo.fail(safeName, "不支持的文件类型: ." + extension);
            }

            String id = UUID.randomUUID().toString();
            Path dir = repositoryDir(sessionId).resolve(id);
            Files.createDirectories(dir);
            Files.write(dir.resolve(payloadFileName), payloadBytes);

            JSONObject meta = new JSONObject();
            meta.put("id", id);
            meta.put("name", safeName);
            meta.put("type", type);
            meta.put("size", bytes.length);
            meta.put("parsedChars", payloadText == null ? 0 : payloadText.length());
            meta.put("createdAt", System.currentTimeMillis());
            Files.write(dir.resolve("meta.json"), meta.toJSONString().getBytes(StandardCharsets.UTF_8));

            return AttachmentInfo.ok(id, safeName, type);
        } catch (Exception e) {
            return AttachmentInfo.fail(safeName, "解析失败: " + e.getMessage());
        }
    }

    /** 查询附件状态（meta 不存在时返回 null） */
    public AttachmentInfo get(String sessionId, String id) {
        if (sessionId == null || id == null
                || !sessionId.matches("[A-Za-z0-9_-]+") || !id.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        Path metaFile = repositoryDir(sessionId).resolve(id).resolve("meta.json");
        if (!Files.isRegularFile(metaFile)) {
            return null;
        }
        try {
            JSONObject meta = JSON.parseObject(Files.readString(metaFile));
            return new AttachmentInfo(meta.getString("id"), meta.getString("name"),
                    meta.getString("type"), "ready", null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 按 ID 列表从仓库加载附件，还原为 core 层 Attachment：
     * 图片读字节现场编码 base64 一次，文档读解析文本。
     * 单个附件缺失或损坏时跳过（记日志），不影响其余。
     */
    public List<Attachment> load(String sessionId, List<String> ids) {
        List<Attachment> attachments = new ArrayList<>();
        if (sessionId == null || ids == null || ids.isEmpty()) {
            return attachments;
        }
        for (String id : ids) {
            try {
                Path dir = repositoryDir(sessionId).resolve(id);
                JSONObject meta = JSON.parseObject(Files.readString(dir.resolve("meta.json")));
                String name = meta.getString("name");
                if ("image".equals(meta.getString("type"))) {
                    byte[] bytes = Files.readAllBytes(dir.resolve("payload.bin"));
                    String mime = imageMimeType(name);
                    attachments.add(Attachment.image(name,
                            "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes)));
                } else {
                    attachments.add(Attachment.document(name, Files.readString(dir.resolve("payload.md"))));
                }
            } catch (Exception e) {
                System.err.println("[Attachment] 附件加载失败，已跳过: " + id + " - " + e.getMessage());
            }
        }
        return attachments;
    }

    /** 图片回显载荷：原始字节 + Content-Type */
    public record ImagePayload(byte[] bytes, String contentType) {
    }

    /**
     * 按文件名查找会话中的图片附件（供历史消息 [图片: xxx] 占位符回显）。
     * 优先扫描附件仓库各目录的 meta.json（名称精确匹配且类型为图片）；
     * 找不到时回退旧版图片直传方案的落盘目录 .qualia/images/{sessionId}/，
     * 保证历史会话的图片仍可回显；均找不到返回 null。
     */
    public ImagePayload findImageByName(String sessionId, String fileName) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")
                || fileName == null || fileName.isBlank()) {
            return null;
        }
        Path dir = repositoryDir(sessionId);
        if (Files.isDirectory(dir)) {
            try (var entries = Files.list(dir)) {
                for (Path entry : entries.sorted().toList()) {
                    Path metaFile = entry.resolve("meta.json");
                    if (!Files.isRegularFile(metaFile)) {
                        continue;
                    }
                    try {
                        JSONObject meta = JSON.parseObject(Files.readString(metaFile));
                        if ("image".equals(meta.getString("type"))
                                && fileName.equals(meta.getString("name"))) {
                            byte[] bytes = Files.readAllBytes(entry.resolve("payload.bin"));
                            return new ImagePayload(bytes, imageMimeType(fileName));
                        }
                    } catch (Exception ignored) {
                        // 单个 meta 损坏不影响其余附件查找
                    }
                }
            } catch (IOException e) {
                System.err.println("[Attachment] 图片查找失败: " + sessionId + "/" + fileName + " - " + e.getMessage());
            }
        }
        return findLegacyImage(sessionId, fileName);
    }

    /** 旧版图片直传方案的落盘目录（.qualia/images/{sessionId}，仅历史会话兼容回显用） */
    private Path legacyImagesDir(String sessionId) {
        return WebApplication.getCurrentWorkspace()
                .resolve(".qualia").resolve("images").resolve(sessionId);
    }

    /** 回退查找旧版落盘目录中的图片；文件名经清洗防路径穿越，不存在返回 null */
    private ImagePayload findLegacyImage(String sessionId, String fileName) {
        String safe = sanitizeFileName(fileName);
        if (safe == null) {
            return null;
        }
        Path file = legacyImagesDir(sessionId).resolve(safe);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return new ImagePayload(Files.readAllBytes(file), imageMimeType(safe));
        } catch (IOException e) {
            System.err.println("[Attachment] 旧目录图片读取失败: " + sessionId + "/" + safe + " - " + e.getMessage());
            return null;
        }
    }

    /** 清理会话的全部附件（删除会话时联动调用）；会话资源目录清空后一并移除空壳 */
    public void clearSession(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return;
        }
        Path dir = repositoryDir(sessionId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            System.err.println("[Attachment] 会话附件清理失败: " + sessionId + " - " + e.getMessage());
        }
        // files 已空时移除会话资源目录（非空说明还有聊天记录等其他会话资源，保留）
        try {
            Files.deleteIfExists(dir.getParent());
        } catch (IOException ignored) {
        }
    }

    /** 拼接分段解析结果（页间空行分隔） */
    private String joinDocuments(List<Document> documents) {
        StringBuilder text = new StringBuilder();
        for (Document document : documents) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(document.getContent() == null ? "" : document.getContent());
        }
        return text.toString();
    }

    /** 按文件扩展名推断图片 MIME 类型（存盘字节与扩展名一致） */
    private String imageMimeType(String fileName) {
        return switch (extensionOf(fileName)) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 清洗文件名：截取末段、替换文件系统非法字符、防路径穿越，保留中文与空格 */
    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return null;
        }
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(cleaned.length() - 80);
        }
        return cleaned;
    }
}
