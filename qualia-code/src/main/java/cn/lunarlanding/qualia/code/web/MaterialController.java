package cn.lunarlanding.qualia.code.web;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.code.service.MaterialService.Article;
import cn.lunarlanding.qualia.code.service.MaterialService.MaterialInfo;
import cn.lunarlanding.qualia.code.service.MaterialService.OriginalPayload;
import cn.lunarlanding.qualia.code.service.MaterialService.TreeNode;
import cn.lunarlanding.qualia.code.service.MaterialService.UploadResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 资料 API：本地 Wiki 式资料库的库管理、文章树、上传与阅读。
 *
 * <p>一期范围：人工上传 + 查看（整篇 Markdown 阅读与源码编辑）；
 * 智能体工具化阅读/写作为二期，不在此暴露。</p>
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService service = MaterialService.getInstance();

    // ===== 库 CRUD =====

    /** 库列表（含文章数 / 总字数 / 更新时间统计） */
    @GetMapping
    public ResponseEntity<List<MaterialInfo>> list() {
        return ResponseEntity.ok(service.list());
    }

    /** 创建资料库 {name, description} */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(service.create(body.get("name"), body.get("description")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return internalError("创建资料库失败", e);
        }
    }

    /** 更新库名称/描述（缺省字段保持不变） */
    @PatchMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(service.update(id, body.get("name"), body.get("description")));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("更新资料库失败", e);
        }
    }

    /** 删除资料库（物理删除，前端 confirm） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable String id) {
        try {
            service.delete(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("删除资料库失败", e);
        }
    }

    // ===== 文章树与文件夹 =====

    /** 文章树（文件夹在前；文章带字数与首段摘要） */
    @GetMapping("/{id}/tree")
    public ResponseEntity<Object> tree(@PathVariable String id) {
        try {
            List<TreeNode> tree = service.tree(id);
            return ResponseEntity.ok(Map.of("root", "", "children", tree));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("读取文章树失败", e);
        }
    }

    /** 新建文件夹 {name, parentPath?}，返回库内相对路径 */
    @PostMapping("/{id}/folders")
    public ResponseEntity<Object> createFolder(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String path = service.createFolder(id, body.get("parentPath"), body.get("name"));
            return ResponseEntity.ok(Map.of("path", path));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("新建文件夹失败", e);
        }
    }

    // ===== 上传（同步转换） =====

    /**
     * 上传文件（可多选）到指定文件夹：md/txt 直接入库；pdf/docx 抽取全文转整篇文章并保留原件。
     * 同步转换，返回每个文件的回执（ok=false 时 error 为可读原因），无异步状态轮询。
     */
    @PostMapping("/{id}/files")
    public ResponseEntity<Object> upload(@PathVariable String id,
                                         @RequestParam(value = "parentPath", required = false) String parentPath,
                                         @RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "未选择文件"));
        }
        try {
            List<UploadResult> results = service.upload(id, parentPath, files);
            return ResponseEntity.ok(Map.of("results", results));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("上传资料失败", e);
        }
    }

    // ===== 文章读写 =====

    /** 读文章 ?path=需求/PRD.md（正文剥离 frontmatter，元信息平铺） */
    @GetMapping("/{id}/article")
    public ResponseEntity<Object> article(@PathVariable String id, @RequestParam String path) {
        try {
            Article article = service.article(id, path);
            return ResponseEntity.ok(article);
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("读取文章失败", e);
        }
    }

    /** 新建空白文章 {name, parentPath?}，返回库内相对路径 */
    @PostMapping("/{id}/article")
    public ResponseEntity<Object> createArticle(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String path = service.createArticle(id, body.get("parentPath"), body.get("name"));
            return ResponseEntity.ok(Map.of("path", path));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("新建文章失败", e);
        }
    }

    /** 保存文章正文 {content}（frontmatter 元信息由后端保留并刷新 updatedAt） */
    @PutMapping("/{id}/article")
    public ResponseEntity<Object> saveArticle(@PathVariable String id,
                                              @RequestParam String path,
                                              @RequestBody Map<String, String> body) {
        try {
            service.saveArticle(id, path, body.getOrDefault("content", ""));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("保存文章失败", e);
        }
    }

    /** 重命名节点（文章或文件夹）{name} */
    @PatchMapping("/{id}/node")
    public ResponseEntity<Object> renameNode(@PathVariable String id,
                                             @RequestParam String path,
                                             @RequestBody Map<String, String> body) {
        try {
            service.renameNode(id, path, body.get("name"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("重命名失败", e);
        }
    }

    /** 删除节点（文件夹连带子树） */
    @DeleteMapping("/{id}/node")
    public ResponseEntity<Object> deleteNode(@PathVariable String id, @RequestParam String path) {
        try {
            service.deleteNode(id, path);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return notFoundOrBadRequest(e);
        } catch (Exception e) {
            return internalError("删除失败", e);
        }
    }

    // ===== 原件 =====

    /** 下载/预览上传原件（pdf 内联预览，其余附件下载） */
    @GetMapping("/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable String id, @RequestParam String path) {
        try {
            OriginalPayload payload = service.original(id, path);
            boolean inline = payload.contentType().startsWith("application/pdf");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(payload.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            (inline ? "inline" : "attachment") + "; filename*=UTF-8''"
                                    + java.net.URLEncoder.encode(payload.fileName(), java.nio.charset.StandardCharsets.UTF_8))
                    .body(payload.data());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== 错误映射 =====

    /** 参数类异常：库不存在转 404，其余转 400 并带可读信息 */
    private ResponseEntity<Object> notFoundOrBadRequest(IllegalArgumentException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.startsWith("资料库不存在")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
    }

    private ResponseEntity<Object> internalError(String log, Exception e) {
        org.slf4j.LoggerFactory.getLogger(MaterialController.class).error(log, e);
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", log + ": " + e.getMessage()));
    }
}
