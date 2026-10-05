package cn.lunarlanding.qualia.code.service;

import cn.lunarlanding.qualia.code.CodeAgentConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 技能市场服务：对接 skills.sh 社区市场（Agent Skills 开放注册表，anthropics/openai/vercel
 * 官方技能均收录于此）。
 * 搜索 —— 代理 skills.sh 免认证搜索 API，归一化条目并标记本地是否已安装；
 * 安装 —— 技能源为 GitHub 仓库内的技能目录包（SKILL.md + 脚本/附属文档），下载仓库 zipball
 *         后定位 {skillId}/SKILL.md 所在目录，复制到全局技能目录 ~/.qualia/code/skills/{skillId}/，
 *         DirectorySkillLoader 按目录读取，新对话即时生效。
 */
@Service
public class SkillMarketService {

    private static final Logger logger = LoggerFactory.getLogger(SkillMarketService.class);

    /** skills.sh 模糊搜索 API（免认证，返回 id/name/skillId/source/installs） */
    private static final String SEARCH_API = "https://skills.sh/api/search?q=";

    /** GitHub zipball 下载地址（HEAD 自动解析默认分支；codeload 走 CDN，不受 API 限流约束） */
    private static final String ZIPBALL_URL = "https://codeload.github.com/%s/zip/HEAD";

    /** GitHub 仓库标识（owner/repo）：owner 仅字母数字与连字符，据此过滤 open.feishu.cn 等外部来源 */
    private static final Pattern REPO_PATTERN = Pattern.compile("^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$");

    /** 标识段合法性（owner/repo/skillId）：首字符须为字母数字，杜绝路径穿越 */
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 搜索技能市场条目；installed 标记本地全局技能目录中是否已存在同名技能
     */
    public List<Map<String, Object>> search(String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(SEARCH_API + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("技能市场搜索失败: HTTP " + resp.statusCode());
        }
        JSONArray arr = JSON.parseObject(resp.body()).getJSONArray("skills");
        List<Map<String, Object>> result = new ArrayList<>();
        if (arr == null) {
            return result;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject it = arr.getJSONObject(i);
            String skillId = it.getString("skillId");
            boolean installable = isGitHubRepo(it.getString("source")) && isValidSegment(skillId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", it.getString("id"));
            item.put("name", it.getString("name"));
            item.put("skillId", skillId);
            item.put("source", it.getString("source"));
            item.put("installs", it.getIntValue("installs"));
            item.put("installable", installable);
            item.put("installed", installable && Files.exists(CodeAgentConfig.GLOBAL_SKILLS_DIR.resolve(skillId)));
            result.add(item);
        }
        return result;
    }

    /**
     * 从市场安装技能到全局技能目录，返回技能名
     */
    public String install(String id) throws IOException, InterruptedException {
        String[] parts = parseId(id);
        String repo = parts[0] + "/" + parts[1];
        String skillId = parts[2];
        Path targetDir = CodeAgentConfig.GLOBAL_SKILLS_DIR.resolve(skillId);
        if (Files.exists(targetDir)) {
            throw new IOException("技能已安装: " + skillId);
        }

        Path zip = Files.createTempFile("qualia-skill-", ".zip");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(ZIPBALL_URL, repo)))
                    .timeout(Duration.ofSeconds(60))
                    .GET().build();
            HttpResponse<Path> resp = http.send(request, HttpResponse.BodyHandlers.ofFile(zip));
            if (resp.statusCode() != 200) {
                throw new IOException("下载技能源失败: HTTP " + resp.statusCode());
            }
            extractSkill(zip, skillId, targetDir);
            logger.info("技能市场安装成功: {} -> {}", id, targetDir);
            return skillId;
        } catch (IOException e) {
            // 失败清理半成品目录，避免留下无法加载的残缺技能
            deleteQuietly(targetDir);
            throw e;
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    /**
     * 从 zipball 中定位 {skillId}/SKILL.md 所在目录并整体解压到目标目录（兼容 SKILL.md/skill.md）
     */
    private void extractSkill(Path zip, String skillId, Path targetDir) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            String dirPrefix = locateSkillDir(zf, skillId);
            if (dirPrefix == null) {
                throw new IOException("仓库中未找到技能目录 " + skillId + "（缺少 SKILL.md）");
            }
            boolean any = false;
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(dirPrefix)) {
                    continue;
                }
                Path rel = Path.of(e.getName().substring(dirPrefix.length()));
                Path dest = targetDir.resolve(rel).normalize();
                if (!dest.startsWith(targetDir.normalize())) {
                    continue; // 防压缩包内异常路径穿越
                }
                Files.createDirectories(dest.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                any = true;
            }
            if (!any) {
                throw new IOException("技能目录为空: " + skillId);
            }
        }
    }

    /** 在 zip 条目中查找以 /{skillId}/SKILL.md 结尾的路径，返回其技能目录前缀 */
    private String locateSkillDir(ZipFile zf, String skillId) {
        for (String file : new String[]{"SKILL.md", "skill.md"}) {
            String suffix = "/" + skillId + "/" + file;
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getName().endsWith(suffix)) {
                    return e.getName().substring(0, e.getName().length() - file.length());
                }
            }
        }
        return null;
    }

    /** 校验并拆分 owner/repo/skillId 三段式标识 */
    private String[] parseId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("参数 id 不能为空");
        }
        String[] parts = id.split("/");
        if (parts.length != 3 || !isGitHubRepo(parts[0] + "/" + parts[1])) {
            throw new IllegalArgumentException("无效的技能标识: " + id);
        }
        for (String p : parts) {
            if (!isValidSegment(p)) {
                throw new IllegalArgumentException("无效的技能标识: " + id);
            }
        }
        return parts;
    }

    private boolean isGitHubRepo(String source) {
        return source != null && REPO_PATTERN.matcher(source).matches();
    }

    private boolean isValidSegment(String segment) {
        return segment != null && SEGMENT_PATTERN.matcher(segment).matches();
    }

    private void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var entries = Files.list(dir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                deleteQuietly(entry);
            }
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
        }
    }
}
