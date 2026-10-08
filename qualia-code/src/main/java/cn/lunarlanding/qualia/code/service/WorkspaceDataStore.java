package cn.lunarlanding.qualia.code.service;

import cn.lunarlanding.qualia.code.CodeAgentConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话存储路径权威：所有会话数据统一存放于全局桶 {@code ~/.qualia/code/sessions/{sessionId}/}，
 * 项目（工作区）目录零会话数据写入。
 *
 * <p>职责：
 * <ul>
 *   <li>全局桶与会话域路径的唯一拼装口径（替代散落各处的 workspacePath/.qualia 拼装）；</li>
 *   <li>会话绑定关系 binding.json 的读写（workspace 字段：null=派生工作区 / 路径=用户绑定的项目）；</li>
 *   <li>存量迁移：把各项目 {@code .qualia/sessions/{sid}} 惰性搬入全局桶（幂等），搬后清理项目内空 .qualia。</li>
 * </ul>
 *
 * <p>会话域内部布局：session.json（聊天记录）/ memory.json（压缩摘要）/ files/（附件）/
 * binding.json（绑定元数据）/ workspace/（派生工作区，仅未绑定时创建）。
 * 删除会话即删除整个会话域目录；绑定外部项目时项目目录不受任何影响。
 */
public final class WorkspaceDataStore {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceDataStore.class);

    /** 全局会话桶：~/.qualia/code/sessions */
    private static final Path GLOBAL_SESSIONS_DIR =
            CodeAgentConfig.GLOBAL_CONFIG_DIR.resolve("sessions");

    /** 已完成存量迁移的工作区（绝对路径字符串），避免每次初始化重复扫描 */
    private static final Set<String> MIGRATED = ConcurrentHashMap.newKeySet();

    private WorkspaceDataStore() {
    }

    /** 全局会话桶根目录 */
    public static Path globalSessionsDir() {
        return GLOBAL_SESSIONS_DIR;
    }

    /** 会话域目录：{全局桶}/{sessionId}（聊天记录、摘要、附件、绑定元数据同域聚合） */
    public static Path sessionDir(String sessionId) {
        return GLOBAL_SESSIONS_DIR.resolve(sessionId);
    }

    /** 绑定元数据文件：{会话域}/binding.json；文件不存在 = 未绑定（派生工作区） */
    public static Path bindingFile(String sessionId) {
        return sessionDir(sessionId).resolve("binding.json");
    }

    /**
     * 读取会话绑定的工作区路径。
     *
     * @return 绑定的项目路径；未绑定或读取失败返回 null（派生工作区语义）
     */
    public static Path readBinding(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        Path file = bindingFile(sessionId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            String workspace = json == null ? null : json.getString("workspace");
            return workspace == null || workspace.isBlank() ? null : Path.of(workspace);
        } catch (IOException e) {
            logger.warn("读取会话绑定失败: {} - {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * 写入会话绑定（仅用户显式绑定动作调用——信任边界，消息/模型不可触达）。
     *
     * @param workspace 绑定的项目路径；null 表示移除绑定（解绑为派生工作区）
     */
    public static void writeBinding(String sessionId, Path workspace) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return;
        }
        Path file = bindingFile(sessionId);
        try {
            if (workspace == null) {
                Files.deleteIfExists(file);
                return;
            }
            Files.createDirectories(file.getParent());
            JSONObject json = new JSONObject();
            json.put("workspace", workspace.toAbsolutePath().normalize().toString());
            Files.writeString(file, JSON.toJSONString(json, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("写入会话绑定失败: {} - {}", file, e.getMessage());
        }
    }

    /** 删除绑定文件（删除会话时联动，先于 memory.clearSession 调用以保证空目录可清理） */
    public static void deleteBinding(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]+")) {
            return;
        }
        try {
            Files.deleteIfExists(bindingFile(sessionId));
        } catch (IOException ignored) {
        }
    }

    /**
     * 存量迁移（惰性 · 幂等）：把 {@code {workspace}/.qualia/sessions/{sid}} 整目录搬入全局桶。
     *
     * <p>逐条目搬移不覆盖（目标已存在视为已迁移，仅跳过）；搬空的旧会话目录删除；
     * 迁移时补写会话归属 binding.json（已存在不覆盖）。
     * 单个会话失败（如文件被占用）不中断其他会话，失败会话留待下次启动重试（清除内存标记）。
     * 完成后清理项目内旧预建的空目录（空 sessions/skills/memory 与空 .qualia，用户自放内容原样保留）。
     * 每个工作区仅在首次使用时扫描一次（内存标记）。
     *
     * @param workspacePath 当前工作区；null 直接返回
     */
    public static void migrateProjectSessions(Path workspacePath) {
        if (workspacePath == null) {
            return;
        }
        String key = workspacePath.toAbsolutePath().normalize().toString();
        if (!MIGRATED.add(key)) {
            return;
        }
        boolean retry = false;
        try {
            Path legacySessions = workspacePath.resolve(".qualia").resolve("sessions");
            if (Files.isDirectory(legacySessions)) {
                Files.createDirectories(GLOBAL_SESSIONS_DIR);
                int moved = 0;
                try (var paths = Files.list(legacySessions)) {
                    for (Path dir : paths.filter(Files::isDirectory).toList()) {
                        String sid = dir.getFileName().toString();
                        if (!sid.matches("[A-Za-z0-9_-]+")) {
                            continue;
                        }
                        // 会话级隔离：单会话失败（如文件被应用占用）不中断其他会话
                        try {
                            Path target = GLOBAL_SESSIONS_DIR.resolve(sid);
                            Files.createDirectories(target);
                            // 迁移会话的归属即来源工作区：补写绑定（已存在不覆盖，保留后续可能的手工解绑）
                            if (!Files.exists(bindingFile(sid))) {
                                writeBinding(sid, workspacePath);
                            }
                            try (var entries = Files.list(dir)) {
                                for (Path entry : entries.toList()) {
                                    Path dest = target.resolve(entry.getFileName().toString());
                                    if (Files.exists(dest)) {
                                        continue; // 已存在不覆盖（可能已迁移过）
                                    }
                                    Files.move(entry, dest);
                                }
                            }
                            try (var entries = Files.list(dir)) {
                                if (entries.findAny().isEmpty()) {
                                    Files.deleteIfExists(dir);
                                    moved++;
                                }
                            }
                        } catch (IOException e) {
                            retry = true;
                            logger.warn("会话 [{}] 迁移中断，留待下次启动重试: {}", sid, e.getMessage());
                        }
                    }
                }
                if (moved > 0) {
                    logger.info("工作区 [{}] 的 {} 个会话已迁移到全局桶 {}", key, moved, GLOBAL_SESSIONS_DIR);
                }
            }
            cleanupProjectQualia(workspacePath);
        } catch (IOException e) {
            retry = true;
            logger.warn("工作区 [{}] 会话存量迁移失败: {}", key, e.getMessage());
        } finally {
            if (retry) {
                MIGRATED.remove(key); // 有会话未迁完，下次启动重试（迁移幂等，重试安全）
            }
        }
    }

    /**
     * 清理项目内旧预建的空目录：空 sessions/、skills/、memory/ 与空 .qualia/。
     * 仅删除空目录，用户自行放入的内容原样保留（旧版遗留 images 随非空 .qualia 天然保留）。
     */
    private static void cleanupProjectQualia(Path workspacePath) {
        try {
            Path qualiaDir = workspacePath.resolve(".qualia");
            if (!Files.isDirectory(qualiaDir)) {
                return;
            }
            for (String sub : new String[]{"sessions", "skills", "memory"}) {
                Path p = qualiaDir.resolve(sub);
                if (Files.isDirectory(p) && isEmpty(p)) {
                    Files.deleteIfExists(p);
                }
            }
            if (isEmpty(qualiaDir)) {
                Files.deleteIfExists(qualiaDir);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }
}
