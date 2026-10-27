package cn.lunarlanding.qualia.code.service;

import cn.lunarlanding.qualia.code.CodeAgentConfig;
import cn.lunarlanding.qualia.core.agent.CallOptions;
import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;
import cn.lunarlanding.qualia.core.memory.MemoryMessage;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 定时任务服务：一条固定提示词 + cron 周期，到点由后台调度器复用对话管道执行。
 *
 * <p>执行 = 每轮新建独立会话（标题取任务提示词截断）后，以只读模式
 * （{@link CallOptions#readOnly()}）在该会话发起一轮普通对话，结果自然落进该会话历史；
 * 执行完成回读会话末条 user/assistant 消息 id 与会话 id 写进执行记录，前端按 id
 * 精确匹配渲染「定时」标注——qualia-core 零改动。任务与会话解耦：会话由运行时派生。</p>
 *
 * <p>互斥语义：全局单执行通道（对齐 ChatController 的 {@code isStreaming} 防护），用户正在
 * 聊天或上一轮未跑完时到点任务顺延到下一周期，错过不补跑。</p>
 *
 * <p>存储：{@code ~/.qualia/code/schedules.json}（fastjson 落盘，对齐 MaterialService 风格），
 * 每任务保留最近 {@value #MAX_RUNS} 条执行记录。cron 解析用 Spring 自带
 * {@link CronExpression}（零新依赖），每天/工作日/每周N 由前端折算成 cron，高级模式直填。</p>
 */
public class ScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleService.class);

    /**
     * 存储文件。必须在 INSTANCE 之前声明：单例构造会立即 load() 读取此字段，
     * 若声明在 INSTANCE 之后，<clinit> 按文本序执行时 load 读到 null（静态前向引用），
     * 首次访问即 ExceptionInInitializerError，之后每次请求都是 NoClassDefFoundError。
     */
    private static final Path STORE_FILE = CodeAgentConfig.GLOBAL_CONFIG_DIR.resolve("schedules.json");

    private static final ScheduleService INSTANCE = new ScheduleService();

    public static ScheduleService getInstance() {
        return INSTANCE;
    }

    private ScheduleService() {
        load();
        startupRecalibrate();
        startScanner();
    }

    /** 每任务执行记录保留条数 */
    private static final int MAX_RUNS = 20;

    /** 扫描周期（秒）：到点判定精度 */
    private static final int SCAN_INTERVAL_SECONDS = 20;

    /** 单轮执行上限（防流挂死占通道）：超时记失败并放弃该轮 */
    private static final long RUN_TIMEOUT_MINUTES = 30;

    // ===== 视图模型（public 字段 + fastjson 直序列化，风格对齐 WorkspaceHistory 等轻量存储） =====

    /** 定时任务定义 */
    public static class ScheduleDef {
        public String id;
        /** 任务提示词（到点作为用户消息发起一轮对话） */
        public String prompt;
        /** 标准 6 段 cron（秒 分 时 日 月 周，Spring CronExpression 方言） */
        public String cron;
        /** 周期展示文本（如「每天 09:30」，前端创建时给定） */
        public String cronText;
        public boolean enabled = true;
        public long createdAt;
        public Long lastRunAt;
        /** ok / error / running */
        public String lastStatus;
        public Long nextRunAt;
        public List<ScheduleRun> runs = new ArrayList<>();
    }

    /** 单次执行记录（sessionId = 本轮新建的运行会话；userMsgId/assistantMsgId 供前端按消息 id 精确匹配渲染标注） */
    public static class ScheduleRun {
        public long runAt;
        /** ok / error */
        public String status;
        public long durationMs;
        /** AI 回复首行摘要（截断，仅展示） */
        public String summary;
        public String sessionId;
        public String userMsgId;
        public String assistantMsgId;
        public String error;
    }

    /** 内存态任务表（读写均在 synchronized(this) 内；引用发布供无锁快照读） */
    private volatile List<ScheduleDef> defs = new ArrayList<>();

    /** 正在后台执行的任务 id（防扫描重入；手动执行同样登记） */
    private final Set<String> runningIds = new HashSet<>();

    /** 到点扫描线程（daemon：随进程退出，无需优雅关闭） */
    private java.util.concurrent.ScheduledExecutorService scanner;

    /** 执行线程池：扫描循环只负责触发，一轮对话耗时与扫描解耦 */
    private final ExecutorService execPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "schedule-run");
        t.setDaemon(true);
        return t;
    });

    // ===== 查询 =====

    /** 全部任务（含执行记录；副本，外部改不动内存态） */
    public List<ScheduleDef> list() {
        List<ScheduleDef> snapshot = defs;
        List<ScheduleDef> out = new ArrayList<>(snapshot);
        out.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return out;
    }

    public ScheduleDef get(String id) {
        return find(id);
    }

    private ScheduleDef find(String id) {
        List<ScheduleDef> snapshot = defs;
        for (ScheduleDef def : snapshot) {
            if (def.id.equals(id)) return def;
        }
        return null;
    }

    // ===== 写操作（Controller 唯一入口） =====

    /**
     * 创建任务。cron 非法抛 {@link IllegalArgumentException}——由 Controller 转 400。
     */
    public synchronized ScheduleDef create(String prompt, String cron, String cronText) {
        String trimmed = prompt == null ? "" : prompt.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        long next = parseAndNext(cron);
        ScheduleDef def = new ScheduleDef();
        def.id = UUID.randomUUID().toString();
        def.prompt = trimmed;
        def.cron = cron.trim();
        def.cronText = cronText == null || cronText.isBlank() ? def.cron : cronText.trim();
        def.createdAt = System.currentTimeMillis();
        def.nextRunAt = next;
        defs.add(def);
        save();
        logger.info("定时任务已创建: cron={}, next={}", def.cron, def.nextRunAt);
        return def;
    }

    /**
     * 更新启停/周期：改 cron 重算下次运行；重新启用同样以 now 重算（暂停期间错过的轮次不补跑）
     */
    public synchronized ScheduleDef update(String id, Boolean enabled, String cron, String cronText) {
        ScheduleDef def = find(id);
        if (def == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (cron != null && !cron.isBlank()) {
            def.nextRunAt = parseAndNext(cron);
            def.cron = cron.trim();
        }
        if (cronText != null && !cronText.isBlank()) {
            def.cronText = cronText.trim();
        }
        if (enabled != null) {
            boolean was = def.enabled;
            def.enabled = enabled;
            if (!was && enabled) {
                def.nextRunAt = nextRunTime(def.cron);
            }
        }
        save();
        return def;
    }

    public synchronized boolean delete(String id) {
        boolean removed = defs.removeIf(d -> d.id.equals(id));
        if (removed) save();
        return removed;
    }

    /**
     * 手动立即执行一轮：不打乱周期（nextRunAt 保持不动），忙时抛 {@link IllegalStateException}
     * 由 Controller 转 409。
     */
    public void runNow(String id) {
        ScheduleDef def = get(id);
        if (def == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        startRun(def, false);
    }

    // ===== 调度与执行 =====

    /** 启动扫描循环：到点判定 → 互斥检查 → 后台执行 */
    private void startScanner() {
        scanner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "schedule-scanner");
            t.setDaemon(true);
            return t;
        });
        scanner.scheduleAtFixedRate(this::tick, SCAN_INTERVAL_SECONDS, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** 单次扫描：错过不补跑——过期的 nextRunAt 只触发一次，触发即推到未来 */
    private void tick() {
        long now = System.currentTimeMillis();
        for (ScheduleDef def : list()) {
            if (!def.enabled || def.nextRunAt == null || def.nextRunAt > now) continue;
            if (runningIds.contains(def.id)) continue;
            try {
                startRun(def, true);
            } catch (Exception e) {
                logger.warn("定时任务 {} 触发失败: {}", def.id, e.getMessage());
            }
        }
    }

    /**
     * 发起一轮执行：登记 running → 推记录与下次时间 → 交执行池。
     * 用户对话占用执行通道（{@code isStreaming}）且为周期触发时顺延下一周期并跳过本轮；
     * 手动触发则直接拒绝（不静默顺延，让用户明确得知忙）。
     */
    private synchronized void startRun(ScheduleDef def, boolean scheduled) {
        if (runningIds.contains(def.id)) return;
        if (ChatService.isStreaming()) {
            if (scheduled) {
                // 用户正在聊天：顺延到下一周期，不记执行记录（错过不补跑）
                def.nextRunAt = nextRunTime(def.cron);
                save();
                logger.info("定时任务 {} 因对话进行中顺延至 {}", def.id, def.nextRunAt);
                return;
            }
            throw new IllegalStateException("已有对话正在进行，请等待完成后再执行");
        }
        runningIds.add(def.id);
        long now = System.currentTimeMillis();
        ScheduleRun run = new ScheduleRun();
        run.runAt = now;
        run.status = "running";
        if (scheduled) {
            def.nextRunAt = nextRunTime(def.cron);
        }
        def.lastRunAt = now;
        def.lastStatus = "running";
        def.runs.add(0, run);
        if (def.runs.size() > MAX_RUNS) {
            def.runs.subList(MAX_RUNS, def.runs.size()).clear();
        }
        save();
        execPool.submit(() -> {
            try {
                execute(def, run);
            } finally {
                runningIds.remove(def.id);
            }
        });
    }

    /**
     * 执行一轮对话：新建独立会话（标题取任务提示词截断）→ require 激活 → 只读模式发提示词 →
     * 后台消费 Flux 至终态 → 回读会话末条 user/assistant 消息 id 写进记录 → 落盘。
     */
    private void execute(ScheduleDef def, ScheduleRun run) {
        long start = run.runAt;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        StringBuilder answer = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        try {
            // 每轮新建会话：一次运行一个独立落地，互不污染；sessionId 记进执行记录供前端跳转
            String sessionId = ChatService.getInstance().createSession(runTitle(def)).id;
            run.sessionId = sessionId;
            ChatService.require(sessionId);
            ChatService.beginStream();
            Flux<AgentResponse> flux = ChatService.getInstance()
                    .sendMessageFlux(sessionId, def.prompt, null, CallOptions.readOnly());
            Disposable disposable = flux.subscribe(
                    r -> {
                        if (r.getAnswer() != null && !r.getAnswer().isEmpty()) {
                            answer.append(r.getAnswer());
                        }
                        if (r.getErrorMessage() != null && failure.get() == null) {
                            failure.set(new IllegalStateException(r.getErrorMessage()));
                        }
                    },
                    e -> {
                        failure.set(e);
                        done.countDown();
                    },
                    done::countDown);
            if (!done.await(RUN_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                disposable.dispose();
                throw new IllegalStateException("执行超时（" + RUN_TIMEOUT_MINUTES + " 分钟）");
            }
            run.status = failure.get() == null ? "ok" : "error";
            run.error = failure.get() == null ? null : String.valueOf(failure.get().getMessage());
            fillMessageRefs(run, sessionId, answer.toString());
        } catch (Exception e) {
            run.status = "error";
            run.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            interruptIfNeeded(e);
        } finally {
            // 记账销账对称：await 超时/dispose 路径 Flux 不再回调，此处统一兜底（幂等）
            ChatService.endStream();
            run.durationMs = Math.max(0, System.currentTimeMillis() - start);
            persistRunResult(def.id, run);
        }
    }

    /** 回读会话历史：末条 USER = 本轮触发消息，末条 ASSISTANT = 本轮回复（摘要取首行截断） */
    private void fillMessageRefs(ScheduleRun run, String sessionId, String fallbackAnswer) {
        try {
            List<MemoryMessage> history = ChatService.getInstance().getSessionHistory(sessionId);
            for (int i = history.size() - 1; i >= 0; i--) {
                MemoryMessage m = history.get(i);
                if (m.getRole() == MemoryMessage.Role.USER) {
                    run.userMsgId = m.getId();
                    break;
                }
            }
            for (int i = history.size() - 1; i >= 0; i--) {
                MemoryMessage m = history.get(i);
                if (m.getRole() == MemoryMessage.Role.ASSISTANT) {
                    run.assistantMsgId = m.getId();
                    run.summary = summarize(m.getContent() != null ? m.getContent() : fallbackAnswer);
                    return;
                }
            }
        } catch (Exception e) {
            logger.warn("定时任务回读会话消息失败: {}", e.getMessage());
        }
        run.summary = summarize(fallbackAnswer);
    }

    private void interruptIfNeeded(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    /** 运行会话标题：任务提示词截 30 字 */
    private static String runTitle(ScheduleDef def) {
        String p = def.prompt.strip();
        return p.length() > 30 ? p.substring(0, 30) + "…" : p;
    }

    /** 摘要：去 markdown 标记的首行，截 80 字符 */
    private static String summarize(String text) {
        if (text == null) return null;
        String line = text.strip().lines().findFirst().orElse("");
        line = line.replaceFirst("^#{1,6}\\s*", "").replaceFirst("^[*>\\-\\d.\\s]+", "").trim();
        return line.length() > 80 ? line.substring(0, 80) + "…" : line;
    }

    /** 执行结果写回内存态并落盘（执行线程回调入口，synchronized 保护） */
    private synchronized void persistRunResult(String id, ScheduleRun run) {
        ScheduleDef def = find(id);
        if (def == null) return; // 执行期间任务被删除：丢弃记录
        run.status = "running".equals(run.status) ? "error" : run.status;
        def.lastRunAt = run.runAt;
        def.lastStatus = run.status;
        for (ScheduleRun r : def.runs) {
            if (r.runAt == run.runAt) {
                r.status = run.status;
                r.durationMs = run.durationMs;
                r.summary = run.summary;
                r.sessionId = run.sessionId;
                r.userMsgId = run.userMsgId;
                r.assistantMsgId = run.assistantMsgId;
                r.error = run.error;
                break;
            }
        }
        save();
        logger.info("定时任务 {} 执行{}（{} ms）", id, "ok".equals(run.status) ? "成功" : "失败", run.durationMs);
    }

    // ===== cron =====

    /** 校验并计算首个触发时间（now 之后的下一次），非法抛 IllegalArgumentException */
    private static long parseAndNext(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("cron 不能为空");
        }
        CronExpression expr;
        try {
            expr = CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("cron 表达式非法: " + e.getMessage());
        }
        LocalDateTime next = expr.next(LocalDateTime.now());
        if (next == null) {
            throw new IllegalArgumentException("cron 表达式无未来触发时间");
        }
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static long nextRunTime(String cron) {
        try {
            return parseAndNext(cron);
        } catch (IllegalArgumentException e) {
            // 存量脏数据兜底：禁用任务避免扫描风暴
            logger.warn("cron 解析失败，任务顺延 1 小时: {}", e.getMessage());
            return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
        }
    }

    // ===== 持久化 =====

    /** 启动校准：过期/缺失的 nextRunAt 以 now 重算未来（错过不补跑），禁用任务只补缺失字段 */
    private void startupRecalibrate() {
        boolean dirty = false;
        for (ScheduleDef def : defs) {
            if (def.nextRunAt == null || (def.enabled && def.nextRunAt <= System.currentTimeMillis())) {
                def.nextRunAt = nextRunTime(def.cron);
                dirty = true;
            }
        }
        if (dirty) save();
    }

    private void load() {
        if (!Files.isRegularFile(STORE_FILE)) {
            return;
        }
        try {
            String text = Files.readString(STORE_FILE, StandardCharsets.UTF_8);
            if (text.isBlank()) return;
            List<ScheduleDef> loaded = JSON.parseArray(text, ScheduleDef.class);
            defs = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
            // 手改文件/旧版本兜底：runs 缺失时补空表，避免执行路径 NPE
            for (ScheduleDef def : defs) {
                if (def.runs == null) def.runs = new ArrayList<>();
            }
            logger.info("已加载 {} 个定时任务", defs.size());
        } catch (Exception e) {
            logger.warn("读取定时任务存储失败（以空表启动）: {}", e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(STORE_FILE.getParent());
            Path tmp = STORE_FILE.resolveSibling(STORE_FILE.getFileName() + ".tmp");
            Files.writeString(tmp, JSON.toJSONString(defs, true), StandardCharsets.UTF_8);
            Files.move(tmp, STORE_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warn("定时任务存储写入失败: {}", e.getMessage());
        }
    }
}
