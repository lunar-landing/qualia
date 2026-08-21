package cn.lunarlanding.qualia.code.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.lunarlanding.qualia.code.CodeAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区打开历史（用户级，存于 ~/.qualia/code/workspaces.json）
 *
 * 历史天然跨工作区，因此与模型/MCP 配置、全局技能一样归入用户级产品目录，
 * 而不是某个工作区内的 .qualia。启动绑定与运行期切换成功后各记录一次。
 */
public final class WorkspaceHistory {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceHistory.class);

    private static final Path HISTORY_FILE =
            CodeAgentConfig.GLOBAL_CONFIG_DIR.resolve("workspaces.json");

    /** 最近列表上限 */
    private static final int MAX_RECENT = 10;

    private WorkspaceHistory() {
    }

    /**
     * 记录一次工作区打开：插入头部、按路径去重（Windows 不区分大小写）、截断上限
     */
    public static synchronized void record(Path workspace) {
        try {
            String path = workspace.toAbsolutePath().normalize().toString();
            JSONArray recent = readRecent();

            JSONArray updated = new JSONArray();
            JSONObject entry = new JSONObject();
            entry.put("path", path);
            entry.put("lastOpened", Instant.now().toString());
            updated.add(entry);

            for (int i = 0; i < recent.size() && updated.size() < MAX_RECENT; i++) {
                JSONObject item = recent.getJSONObject(i);
                String itemPath = item.getString("path");
                if (itemPath == null || itemPath.equalsIgnoreCase(path)) {
                    continue;
                }
                updated.add(item);
            }

            JSONObject root = new JSONObject();
            root.put("recent", updated);
            Files.createDirectories(HISTORY_FILE.getParent());
            Files.writeString(HISTORY_FILE, JSON.toJSONString(root, true), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 历史记录失败不影响主流程
            logger.warn("写入工作区历史失败: {}", e.getMessage());
        }
    }

    /**
     * 读取最近列表（附带 name 与 exists 标记，供前端直接展示）
     */
    public static synchronized List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        JSONArray recent = readRecent();
        for (int i = 0; i < recent.size(); i++) {
            JSONObject item = recent.getJSONObject(i);
            String path = item.getString("path");
            if (path == null || path.isBlank()) {
                continue;
            }
            Path p = Path.of(path);
            Map<String, Object> entry = new HashMap<>();
            entry.put("path", path);
            entry.put("name", p.getFileName() != null ? p.getFileName().toString() : path);
            entry.put("lastOpened", item.getString("lastOpened"));
            entry.put("exists", Files.isDirectory(p));
            result.add(entry);
        }
        return result;
    }

    /**
     * 最近一条仍然存在的目录（供启动时静默复用，避免每次弹选择框）；无则返回 null
     */
    public static Path latestValid() {
        for (Map<String, Object> entry : list()) {
            if (Boolean.TRUE.equals(entry.get("exists"))) {
                return Path.of((String) entry.get("path"));
            }
        }
        return null;
    }

    private static JSONArray readRecent() {
        // 旧版历史文件先迁移到产品目录（幂等）
        CodeAgentConfig.migrateLegacyConfigIfNeeded();
        try {
            if (Files.exists(HISTORY_FILE)) {
                JSONObject root = JSON.parseObject(Files.readString(HISTORY_FILE, StandardCharsets.UTF_8));
                JSONArray recent = root != null ? root.getJSONArray("recent") : null;
                if (recent != null) {
                    return recent;
                }
            }
        } catch (Exception e) {
            logger.warn("读取工作区历史失败: {}", e.getMessage());
        }
        return new JSONArray();
    }
}
