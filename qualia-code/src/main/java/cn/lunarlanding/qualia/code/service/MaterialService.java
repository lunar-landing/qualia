package cn.lunarlanding.qualia.code.service;

import cn.lunarlanding.qualia.code.CodeAgentConfig;
import cn.lunarlanding.qualia.core.parser.Document;
import cn.lunarlanding.qualia.core.parser.DocumentParseException;
import cn.lunarlanding.qualia.core.parser.MarkdownDocumentParser;
import cn.lunarlanding.qualia.core.parser.PdfDocumentParser;
import cn.lunarlanding.qualia.core.parser.WordDocumentParser;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 资料服务：本地 Wiki 式资料库的存储与文章管理。
 *
 * <p>存储于 {@code ~/.qualia/code/materials/{id}/}，纯文件系统、无派生索引：
 * {@code kb.json} 库元信息、{@code articles/**.md} 文章正文（目录层级即 Wiki 层级）、
 * {@code originals/**} 上传原件（pdf/docx 转文章后留档溯源）。
 * 上传同步转换（pdf/docx 抽取全文转 Markdown，不切块、不截断），落盘即完成，无异步解析队列。</p>
 *
 * <p>文章 = Markdown 文件 + 轻量 frontmatter（title/author/updatedAt/source，系统维护）；
 * 路径即 ID：文章以库内相对路径寻址。一期仅人工上传与查看，智能体读写为二期。</p>
 */
public class MaterialService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialService.class);

    private static final MaterialService INSTANCE = new MaterialService();

    public static MaterialService getInstance() {
        return INSTANCE;
    }

    private MaterialService() {
    }

    /** 上传单文件上限（对齐附件大文件档位） */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** frontmatter 时间戳格式（人类可读，解析失败按未知处理） */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 目录/文件名排序：中文按拼音、忽略大小写，文件夹恒在文件前 */
    private static final Comparator<String> NAME_ORDER =
            Comparator.comparing((String s) -> s, Collator.getInstance(Locale.CHINA));

    // ===== 视图记录 =====

    /** 库列表项（含统计：文章数与总字数） */
    public record MaterialInfo(String id, String name, String description,
                               long createdAt, long updatedAt, int articleCount, long totalChars) {
    }

    /** 文章树节点：文件夹或文章（path 为库内相对路径，'/' 分隔） */
    public record TreeNode(String type, String name, String path, long updatedAt,
                           Integer words, String summary, List<TreeNode> children) {
    }

    /** 文章正文（content 为剥离 frontmatter 后的正文，供阅读与编辑） */
    public record Article(String path, String title, String author, String source,
                          long updatedAt, int words, String content) {
    }

    /** 单文件上传回执（同步转换，ok=false 时 error 给出可读原因） */
    public record UploadResult(String name, boolean ok, String path, String title,
                               int words, String original, String error) {
    }

    /** 智能体写入回执：created=新建（false=覆盖已有），path=库内相对路径，words=正文字数 */
    public record WriteResult(boolean created, String path, int words) {
    }

    // ===== 库 CRUD =====

    /** 资料库存储根目录 */
    private Path root() {
        return CodeAgentConfig.GLOBAL_CONFIG_DIR.resolve("materials");
    }

    private Path kbDir(String id) {
        return root().resolve(id);
    }

    /** 校验库存在并返回其目录，不存在抛出携带可读信息的异常（Controller 转 404） */
    private Path requireKb(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9-]{8,64}") || !Files.isDirectory(kbDir(id))) {
            throw new IllegalArgumentException("资料库不存在: " + id);
        }
        return kbDir(id);
    }

    /** 库列表（按更新时间倒序）；首访自动建根目录 */
    public List<MaterialInfo> list() {
        List<MaterialInfo> result = new ArrayList<>();
        try {
            Files.createDirectories(root());
            try (Stream<Path> dirs = Files.list(root())) {
                dirs.filter(Files::isDirectory).forEach(dir -> {
                    JSONObject meta = readKbMeta(dir);
                    if (meta == null) {
                        return;
                    }
                    Stats stats = scanStats(dir.resolve("articles"));
                    long updatedAt = meta.containsKey("updatedAt") ? meta.getLongValue("updatedAt") : stats.latest;
                    result.add(new MaterialInfo(dir.getFileName().toString(),
                            meta.getString("name"), meta.getString("description"),
                            meta.getLongValue("createdAt"), updatedAt, stats.files, stats.chars));
                });
            }
        } catch (IOException e) {
            logger.error("读取资料库列表失败", e);
        }
        result.sort(Comparator.comparingLong(MaterialInfo::updatedAt).reversed());
        return result;
    }

    /** 创建资料库 */
    public MaterialInfo create(String name, String description) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("资料库名称不能为空");
        }
        String id = UUID.randomUUID().toString();
        Path dir = kbDir(id);
        Files.createDirectories(dir.resolve("articles"));
        long now = System.currentTimeMillis();
        JSONObject meta = new JSONObject();
        meta.put("id", id);
        meta.put("name", name.trim());
        meta.put("description", description == null ? "" : description.trim());
        meta.put("createdAt", now);
        meta.put("updatedAt", now);
        writeKbMeta(dir, meta);
        return new MaterialInfo(id, meta.getString("name"), meta.getString("description"), now, now, 0, 0);
    }

    /** 更新库名称/描述（null 字段保持不变） */
    public MaterialInfo update(String id, String name, String description) throws IOException {
        Path dir = requireKb(id);
        JSONObject meta = readKbMeta(dir);
        if (meta == null) {
            throw new IllegalArgumentException("资料库元信息损坏: " + id);
        }
        if (name != null && !name.isBlank()) {
            meta.put("name", name.trim());
        }
        if (description != null) {
            meta.put("description", description.trim());
        }
        meta.put("updatedAt", System.currentTimeMillis());
        writeKbMeta(dir, meta);
        Stats stats = scanStats(dir.resolve("articles"));
        return new MaterialInfo(id, meta.getString("name"), meta.getString("description"),
                meta.getLongValue("createdAt"), meta.getLongValue("updatedAt"), stats.files, stats.chars);
    }

    /** 删除资料库（物理递归删除，前端二次确认） */
    public void delete(String id) throws IOException {
        requireKb(id);
        deleteRecursively(kbDir(id));
    }

    private JSONObject readKbMeta(Path dir) {
        Path file = dir.resolve("kb.json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("读取库元信息失败: {}", file, e);
            return null;
        }
    }

    private void writeKbMeta(Path dir, JSONObject meta) throws IOException {
        atomicWrite(dir.resolve("kb.json"), meta.toJSONString());
    }

    // ===== 文章树 =====

    /** 文章树（文件夹在前、按名称排序；文章带字数与首段摘要） */
    public List<TreeNode> tree(String id) throws IOException {
        Path dir = requireKb(id).resolve("articles");
        Files.createDirectories(dir);
        return buildLevel(dir, dir, "");
    }

    private List<TreeNode> buildLevel(Path articlesRoot, Path dir, String relPrefix) throws IOException {
        List<TreeNode> folders = new ArrayList<>();
        List<TreeNode> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.sorted(Comparator.comparing(p -> p.getFileName().toString(), NAME_ORDER)).toList()) {
                String name = child.getFileName().toString();
                if (name.endsWith(".md")) {
                    String rel = relPrefix + name;
                    String raw = Files.readString(child, StandardCharsets.UTF_8);
                    Map<String, String> meta = new LinkedHashMap<>();
                    String body = stripFrontmatter(raw, meta);
                    files.add(new TreeNode("file", displayTitle(meta, name), rel, lastModified(child),
                            countWords(body), extractSummary(body), null));
                } else if (Files.isDirectory(child)) {
                    String rel = relPrefix + name + "/";
                    folders.add(new TreeNode("folder", name, rel, lastModified(child), null, null,
                            buildLevel(articlesRoot, child, rel)));
                }
            }
        }
        List<TreeNode> result = new ArrayList<>(folders);
        result.addAll(files);
        return result;
    }

    // ===== 文章读写 =====

    /** 读文章（path 为库内相对路径，含 .md 后缀） */
    public Article article(String id, String path) throws IOException {
        Path file = resolveArticleFile(id, path);
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> meta = new LinkedHashMap<>();
        String body = stripFrontmatter(raw, meta);
        return new Article(normalizeRel(path), displayTitle(meta, file.getFileName().toString()),
                meta.getOrDefault("author", "user"), meta.get("source"),
                lastModified(file), countWords(body), body);
    }

    /** 新建空白文章（frontmatter + 空 body），返回库内相对路径 */
    public String createArticle(String id, String parentPath, String name) throws IOException {
        Path dir = resolveArticleDir(id, parentPath);
        String fileName = sanitizeName(name) + ".md";
        Path target = uniqueChild(dir, fileName);
        String title = stripMdExtension(target.getFileName().toString());
        String now = LocalDateTime.now().format(TS);
        String raw = "---\ntitle: " + title + "\nauthor: user\nupdatedAt: " + now + "\n---\n\n";
        atomicWrite(target, raw);
        return relativizeArticle(target, id);
    }

    /** 保存文章正文（frontmatter 元信息保留，仅刷新 updatedAt） */
    public void saveArticle(String id, String path, String content) throws IOException {
        Path file = resolveArticleFile(id, path);
        Map<String, String> meta = new LinkedHashMap<>();
        stripFrontmatter(Files.readString(file, StandardCharsets.UTF_8), meta);
        meta.put("updatedAt", LocalDateTime.now().format(TS));
        atomicWrite(file, buildFrontmatter(meta, content == null ? "" : content));
    }

    /**
     * 智能体写入文章（material_write）：path 相对 articles/，逐段清洗防越界（'.'/'..' 拒绝），
     * 父目录自动创建；已存在则保留 frontmatter 仅更新正文与 updatedAt，
     * 新建注入系统元信息（author=agent）。返回新建/覆盖回执。
     */
    public WriteResult writeArticle(String id, String path, String content) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        StringBuilder joined = new StringBuilder();
        for (String seg : path.split("/")) {
            if (seg.isBlank()) {
                continue;
            }
            String s = sanitizeName(seg);
            if (s.equals(".") || s.equals("..")) {
                throw new IllegalArgumentException("非法路径: " + path);
            }
            if (joined.length() > 0) {
                joined.append('/');
            }
            joined.append(s);
        }
        String clean = joined.toString();
        if (!clean.endsWith(".md")) {
            clean = clean + ".md";
        }
        Path articles = requireKb(id).resolve("articles");
        Files.createDirectories(articles);
        Path file = articles.resolve(clean).normalize();
        if (!file.startsWith(articles) || file.equals(articles)) {
            throw new IllegalArgumentException("非法路径: " + path);
        }
        String body = content == null ? "" : content;
        Map<String, String> meta = new LinkedHashMap<>();
        boolean created = !Files.exists(file);
        if (created) {
            meta.put("title", stripMdExtension(file.getFileName().toString()));
            meta.put("author", "agent");
        } else {
            stripFrontmatter(Files.readString(file, StandardCharsets.UTF_8), meta);
        }
        meta.put("updatedAt", LocalDateTime.now().format(TS));
        atomicWrite(file, buildFrontmatter(meta, body));
        return new WriteResult(created, relativizeArticle(file, id), countWords(body));
    }

    /** 重命名节点（文章或文件夹）；文章同步刷新 frontmatter.title */
    public void renameNode(String id, String path, String newName) throws IOException {
        Path source = resolveNode(id, path);
        String clean = sanitizeName(newName);
        Path target = source.getParent().resolve(Files.isDirectory(source) ? clean : clean + ".md");
        if (Files.exists(target)) {
            throw new IllegalArgumentException("同名条目已存在: " + clean);
        }
        Files.move(source, target);
        if (Files.isRegularFile(target)) {
            Map<String, String> meta = new LinkedHashMap<>();
            String body = stripFrontmatter(Files.readString(target, StandardCharsets.UTF_8), meta);
            meta.put("title", clean);
            meta.put("updatedAt", LocalDateTime.now().format(TS));
            atomicWrite(target, buildFrontmatter(meta, body));
        }
    }

    /** 删除节点（文件夹连带子树） */
    public void deleteNode(String id, String path) throws IOException {
        Path node = resolveNode(id, path);
        deleteRecursively(node);
    }

    // ===== 新建文件夹 =====

    public String createFolder(String id, String parentPath, String name) throws IOException {
        Path dir = resolveArticleDir(id, parentPath);
        Path target = uniqueChild(dir, sanitizeName(name));
        Files.createDirectories(target);
        return relativizeArticle(target, id);
    }

    // ===== 上传转换（同步，落盘即完成） =====

    /**
     * 上传文件到指定文件夹：md/txt 直接入库；pdf/docx 抽取全文转整篇文章（原件存 originals/）。
     * 同名文章自动追加序号，不静默覆盖。
     */
    public List<UploadResult> upload(String id, String parentPath, MultipartFile[] files) throws IOException {
        Path dir = requireKb(id).resolve("articles");
        Path targetDir = (parentPath == null || parentPath.isBlank()) ? dir : resolveArticleDir(id, parentPath);
        List<UploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadOne(id, targetDir, file));
        }
        return results;
    }

    private UploadResult uploadOne(String id, Path targetDir, MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            if (file.isEmpty()) {
                return new UploadResult(name, false, null, null, 0, null, "文件为空");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                return new UploadResult(name, false, null, null, 0, null, "超过 50MB 上限");
            }
            byte[] data = file.getBytes();
            String base = sanitizeName(stripExtension(name));
            String raw;
            String original = null;
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
                raw = normalizeUploadedMarkdown(data, base);
            } else if (lower.endsWith(".txt")) {
                String text = new String(data, StandardCharsets.UTF_8);
                raw = buildFrontmatter(newMeta(base, "user", null), text);
            } else if (lower.endsWith(".pdf")) {
                raw = documentsToMarkdown(new PdfDocumentParser().parse(data), base, "user");
                original = storeOriginal(id, name, data);
            } else if (lower.endsWith(".docx")) {
                raw = documentsToMarkdown(new WordDocumentParser().parse(data), base, "user");
                original = storeOriginal(id, name, data);
            } else {
                return new UploadResult(name, false, null, null, 0, null, "不支持的格式（md / txt / pdf / docx）");
            }
            Path target = uniqueChild(targetDir, base + ".md");
            atomicWrite(target, raw);
            Map<String, String> meta = new LinkedHashMap<>();
            String body = stripFrontmatter(raw, meta);
            // 回执 path 与树/文章接口同契约：相对 articles 的库内路径（original 保持相对库根，供 /original 接口使用）
            return new UploadResult(name, true, relativizeArticle(target, id),
                    meta.getOrDefault("title", base), countWords(body), original, null);
        } catch (DocumentParseException e) {
            logger.warn("资料转换失败: {} - {}", name, e.getMessage());
            return new UploadResult(name, false, null, null, 0, null, e.getMessage());
        } catch (Exception e) {
            logger.error("资料上传失败: " + name, e);
            return new UploadResult(name, false, null, null, 0, null, "上传失败: " + e.getMessage());
        }
    }

    /** md 直接入库：已有 frontmatter 则保留，否则补系统 frontmatter（标题取首个 # 标题，缺省文件名） */
    private String normalizeUploadedMarkdown(byte[] data, String base) {
        String content = new String(data, StandardCharsets.UTF_8);
        if (content.stripLeading().startsWith("---")) {
            return content;
        }
        Map<String, String> meta = newMeta(base, "user", null);
        String title = extractMdTitle(content);
        if (title != null) {
            meta.put("title", title);
        }
        return buildFrontmatter(meta, content);
    }

    /** 解析页文档列表拼为整篇 Markdown（页间以水平线分页） */
    private String documentsToMarkdown(List<Document> documents, String base, String author) {
        StringBuilder body = new StringBuilder();
        for (Document document : documents) {
            if (body.length() > 0) {
                body.append("\n\n---\n\n");
            }
            body.append(document.getContent() == null ? "" : document.getContent().strip());
        }
        return buildFrontmatter(newMeta(base, author, null), body.toString());
    }

    /** 原件存入库内 originals/（重名自动追加序号），返回库内相对路径 */
    private String storeOriginal(String id, String name, byte[] data) throws IOException {
        Path dir = kbDir(id).resolve("originals");
        Files.createDirectories(dir);
        Path target = uniqueChild(dir, sanitizeName(stripExtension(name)) + extOf(name));
        Files.write(target, data);
        return relativize(target, id);
    }

    /** 读原件字节（供「原件」按钮下载/预览） */
    public record OriginalPayload(String fileName, String contentType, byte[] data) {
    }

    public OriginalPayload original(String id, String path) throws IOException {
        Path dir = kbDir(id).resolve("originals");
        Path file = dir.resolve(path == null ? "" : path);
        Path normalized = file.normalize();
        if (!normalized.startsWith(dir) || !Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("原件不存在: " + path);
        }
        String lower = normalized.getFileName().toString().toLowerCase(Locale.ROOT);
        String type = lower.endsWith(".pdf") ? "application/pdf"
                : lower.endsWith(".docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/octet-stream";
        return new OriginalPayload(normalized.getFileName().toString(), type, Files.readAllBytes(normalized));
    }

    // ===== frontmatter 与文本工具 =====

    /** 新文章元信息（LinkedHashMap 保证 frontmatter 字段序稳定） */
    private Map<String, String> newMeta(String title, String author, String source) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("title", title);
        meta.put("author", author);
        meta.put("updatedAt", LocalDateTime.now().format(TS));
        if (source != null) {
            meta.put("source", source);
        }
        return meta;
    }

    /** 剥离 frontmatter，键值写入 meta（无 frontmatter 时原文返回） */
    private String stripFrontmatter(String raw, Map<String, String> meta) {
        if (!raw.startsWith("---")) {
            return raw;
        }
        int end = raw.indexOf("\n---", 3);
        if (end < 0) {
            return raw;
        }
        for (String line : raw.substring(3, end).split("\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                meta.put(line.substring(0, idx).strip(), line.substring(idx + 1).strip());
            }
        }
        return raw.substring(raw.indexOf('\n', end + 1) + 1);
    }

    /** 组装 frontmatter + 正文（meta 缺 title 时以 fallback 补齐） */
    private String buildFrontmatter(Map<String, String> meta, String body) {
        StringBuilder sb = new StringBuilder("---\n");
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue() == null ? "" : entry.getValue()).append('\n');
        }
        sb.append("---\n\n");
        if (body != null && !body.isEmpty()) {
            sb.append(body.startsWith("\n") ? body.substring(1) : body);
            if (!body.endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** 展示标题：frontmatter.title 优先，否则文件名去扩展 */
    private String displayTitle(Map<String, String> meta, String fileName) {
        String title = meta.get("title");
        return title == null || title.isBlank() ? stripMdExtension(fileName) : title;
    }

    /** 字数：去空白后的字符数（中英文同权重，够用作阅读预期） */
    private int countWords(String body) {
        return body.replaceAll("\\s+", "").length();
    }

    /** 首段摘要：跳过标题/引用/列表前缀与空行，截 80 字 */
    private String extractSummary(String body) {
        for (String line : body.split("\n")) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("---") || text.startsWith("|")) {
                continue;
            }
            text = text.replaceFirst("^[>\\-*+\\d.)\\s]+", "");
            if (text.isEmpty()) {
                continue;
            }
            return text.length() > 80 ? text.substring(0, 80) : text;
        }
        return "";
    }

    /** 提取首个 Markdown 一级/二级标题（上传 md 补 frontmatter 时用） */
    private String extractMdTitle(String content) {
        for (String line : content.split("\n")) {
            String text = line.strip();
            if (text.startsWith("# ")) {
                return text.substring(2).strip();
            }
        }
        return null;
    }

    // ===== 路径与文件工具 =====

    /** 解析文章文件（带 .md 后缀），越界/不存在抛出异常 */
    private Path resolveArticleFile(String id, String path) throws IOException {
        Path file = resolveNode(id, path);
        if (!path.endsWith(".md") || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文章不存在: " + path);
        }
        return file;
    }

    /** 解析 articles/ 下节点（文章或文件夹），防路径越界 */
    private Path resolveNode(String id, String path) throws IOException {
        Path articles = requireKb(id).resolve("articles");
        Files.createDirectories(articles);
        Path node = articles.resolve(path == null ? "" : path).normalize();
        if (!node.startsWith(articles) || node.equals(articles) || !Files.exists(node)) {
            throw new IllegalArgumentException("路径不存在: " + path);
        }
        return node;
    }

    /** 解析文章目标文件夹（parentPath 为空 = 根），防越界 */
    private Path resolveArticleDir(String id, String parentPath) throws IOException {
        Path articles = requireKb(id).resolve("articles");
        Files.createDirectories(articles);
        if (parentPath == null || parentPath.isBlank()) {
            return articles;
        }
        Path dir = articles.resolve(parentPath).normalize();
        if (!dir.startsWith(articles) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("文件夹不存在: " + parentPath);
        }
        return dir;
    }

    /** 库内相对路径（'/' 分隔） */
    private String relativize(Path file, String id) {
        return root().resolve(id).relativize(file).toString().replace('\\', '/');
    }

    /** 文章区相对路径（相对 articles/，与树与文章接口的路径契约一致） */
    private String relativizeArticle(Path file, String id) {
        return root().resolve(id).resolve("articles").relativize(file).toString().replace('\\', '/');
    }

    private String normalizeRel(String path) {
        return path.replace('\\', '/');
    }

    /** 文件名清洗：去路径分隔与非法字符，空则回退「未命名」 */
    private String sanitizeName(String name) {
        String clean = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return clean.isEmpty() ? "未命名" : clean;
    }

    /** 目标重名时追加序号 (2)(3)…，不静默覆盖 */
    private Path uniqueChild(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        if (!Files.exists(target)) {
            return target;
        }
        String base = stripExtension(fileName);
        String ext = fileName.substring(base.length());
        for (int i = 2; ; i++) {
            Path candidate = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx <= 0 ? name : name.substring(0, idx);
    }

    private String stripMdExtension(String name) {
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private String extOf(String name) {
        int idx = name.lastIndexOf('.');
        return idx <= 0 ? "" : name.substring(idx);
    }

    private long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** 库统计：递归计文章数、总字数与最新更新时间 */
    private Stats scanStats(Path articles) {
        Stats stats = new Stats();
        if (!Files.isDirectory(articles)) {
            return stats;
        }
        try (Stream<Path> stream = Files.walk(articles)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md")).forEach(p -> {
                try {
                    Map<String, String> meta = new LinkedHashMap<>();
                    String body = stripFrontmatter(Files.readString(p, StandardCharsets.UTF_8), meta);
                    stats.files++;
                    stats.chars += countWords(body);
                    stats.latest = Math.max(stats.latest, lastModified(p));
                } catch (IOException ignored) {
                    // 单文件读取失败不阻塞统计
                }
            });
        } catch (IOException e) {
            logger.warn("统计资料库失败: {}", articles, e);
        }
        return stats;
    }

    private static class Stats {
        int files;
        long chars;
        long latest;
    }

    /** 原子写：临时文件 + 替换，避免半写损坏 */
    private void atomicWrite(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    logger.warn("删除失败: {}", p, e);
                }
            });
        }
    }

    /** 当前时间戳（毫秒），供 Controller 组装响应复用 */
    public static long now() {
        return Instant.now().toEpochMilli();
    }
}
