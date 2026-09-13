package cn.lunarlanding.qualia.code.web;

import cn.lunarlanding.qualia.code.WebApplication;
import cn.lunarlanding.qualia.code.service.AttachmentService;
import cn.lunarlanding.qualia.code.service.AttachmentService.AttachmentInfo;
import cn.lunarlanding.qualia.code.service.AttachmentService.ImagePayload;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

/**
 * 附件 API：上传解析与状态查询。
 *
 * <p>上传与发消息解耦：前端先上传换取回执 ID，发消息时只携带 attachmentIds。</p>
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService = AttachmentService.getInstance();

    /**
     * 上传附件（单文件），按类型分流解析后返回回执。
     *
     * @param sessionId 附件归属会话（仓库按会话隔离）
     */
    @PostMapping
    public ResponseEntity<AttachmentInfo> upload(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("file") MultipartFile file) {
        if (WebApplication.getCurrentWorkspace() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (sessionId == null || sessionId.isEmpty() || file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            AttachmentInfo info = attachmentService.store(sessionId, file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(AttachmentInfo.fail(
                    file.getOriginalFilename(), "读取上传内容失败: " + e.getMessage()));
        }
    }

    /**
     * 按文件名访问会话中的图片附件：供前端历史消息渲染 [图片: xxx] 占位符时回显。
     * 校验 sessionId 与文件名（防路径穿越），允许浏览器短缓存。
     */
    @GetMapping("/{sessionId}/file/{filename}")
    public ResponseEntity<byte[]> image(
            @PathVariable String sessionId,
            @PathVariable String filename) {
        if (WebApplication.getCurrentWorkspace() == null) {
            return ResponseEntity.badRequest().build();
        }
        ImagePayload payload = attachmentService.findImageByName(sessionId, filename.trim());
        if (payload == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(payload.bytes());
    }

    /** 查询附件状态（一期同步解析即传即就绪，为二期异步解析预留） */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> status(
            @RequestParam("sessionId") String sessionId,
            @PathVariable String id) {
        if (WebApplication.getCurrentWorkspace() == null) {
            return ResponseEntity.badRequest().build();
        }
        AttachmentInfo info = attachmentService.get(sessionId, id);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "id", info.id(),
                "name", info.name(),
                "type", info.type(),
                "status", info.status()));
    }
}
