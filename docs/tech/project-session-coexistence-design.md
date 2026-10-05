# 项目会话共存设计（qualia-code）

> 目标：对标主流产品的「项目会话共存」——多个项目的会话在侧边栏聚合可见、点击可继续，切换项目不打断其他项目正在运行的对话流。

## 1. 现状与差距

| 维度 | 现状 | 与共存的差距 |
| --- | --- | --- |
| 会话存储 | `{workspace}/.qualia/sessions/{id}/session.json`，天然按项目物理隔离 | 无（零迁移） |
| ChatService | 全局单例，绑定焦点工作区；`switchWorkspace` 整体重建实例 | 切项目即丢实例，旧会话列表消失 |
| 流式互斥 | `ChatService.ACTIVE_STREAMS` 全局计数，有流时切换工作区返回 409 BUSY | 一次只能跑一个项目，切项目必须等流结束 |
| 项目列表 | `WorkspaceHistory`（recent list）已有 | 仅用于切换弹窗，未参与会话聚合 |
| 前端状态 | chat store 的 `messagesBySession` 按 sessionId 隔离 | 天然支持多会话并行，前端无需大改 |

结论：存储层已按项目隔离，缺的只有两件事——**聚合视图**和**运行并行**。

## 2. 总体方案（两阶段递进）

- **P1 聚合视图**：侧边栏会话列表按项目分组展示全部项目的会话；点击其他项目的会话自动切换焦点项目后加载。不解除流互斥，改动最小。
- **P2 运行共存**：ChatService 由单例改为按项目多实例，流计数实例化，解除切换互斥；切走后后台项目的流继续跑，事件照常入 store。

## 3. 后端改造

### 3.1 改造点总览

| 位置 | P1 | P2 |
| --- | --- | --- |
| `ChatService` | 新增静态 `listSessions(Path)`（只扫 session.json，不建 Agent） | 单例 → `ChatServiceRegistry` 多实例；`ACTIVE_STREAMS` 改为实例字段 |
| `ChatController` | 新增 `GET /api/chat/sessions/all` | stream 接口加可选 `workspace` 参数，按参数取实例 |
| `WorkspaceController` | `/switch` 不变（仍互斥） | 移除 BUSY 409；`switchWorkspace` 退化为「切换焦点」，不再重建实例 |

### 3.2 P1：聚合会话接口

项目来源 = `WorkspaceHistory.list()`，只读扫描各项目 `.qualia/sessions`，不触发 Agent/MCP 初始化：

```java
// ChatController
@GetMapping("/sessions/all")
public ResponseEntity<List<Map<String, Object>>> allSessions() {
    List<Map<String, Object>> groups = new ArrayList<>();
    for (WorkspaceRef ref : WorkspaceHistory.list()) {
        Path ws = Path.of(ref.path());
        if (!Files.isDirectory(ws.resolve(".qualia").resolve("sessions"))) continue;
        groups.add(Map.of(
            "workspacePath", ref.path(),
            "workspaceName", ref.name(),
            "sessions", ChatService.listSessions(ws)   // 静态只读：id/title/createdAt
        ));
    }
    return ResponseEntity.ok(groups);
}
```

```java
// ChatService：从 getSessions 抽出静态只读版本（复用 readCustomTitle 逻辑）
public static List<SessionInfo> listSessions(Path workspace) {
    Path dir = workspace.resolve(".qualia").resolve("sessions");
    // 遍历 {id}/session.json，标题取 custom title，否则首条消息截断 20 字
}
```

### 3.3 P2：多实例注册表

```java
public final class ChatServiceRegistry {
    private static final Map<Path, ChatService> SERVICES = new ConcurrentHashMap<>();
    private static final int MAX_INSTANCES = 8;

    public static ChatService get(Path workspace) {
        evictIfNeeded();
        return SERVICES.computeIfAbsent(
            workspace.toAbsolutePath().normalize(), ChatService::create);
    }

    /** 超限时回收「无活跃流且最久未用」的实例（关 MCP 连接），有流的实例永不回收 */
    private static void evictIfNeeded() { /* ... */ }
}
```

- `ChatService` 删除 `static instance` 与 `switchWorkspace()`，流记账改为实例字段 `AtomicInteger activeStreams`。
- `WorkspaceController /switch`：删除 409 分支与 `ChatService.switchWorkspace` 调用，仅 `setCurrentWorkspace` + `WorkspaceHistory.record`。
- stream 端点透传项目：

```java
@GetMapping("/stream")
public SseEmitter streamChat(@RequestParam String sessionId, @RequestParam String message,
                             @RequestParam(required = false) String model,
                             @RequestParam(required = false) String workspace) {
    return startStream(resolve(workspace), sessionId, message, model, null);
}

private ChatService resolve(String workspace) {
    Path ws = workspace != null ? Path.of(workspace) : WebApplication.getCurrentWorkspace();
    return ChatServiceRegistry.get(ws);
}
```

其余 Controller（文件树/技能/配置等）维持「读焦点项目」语义不变，无需改动。

## 4. 前端改造（qualia-code-ui）

| 文件 | 改动 |
| --- | --- |
| `api/chat.ts` | 新增 `listAllSessions(): Promise<WorkspaceSessionGroup[]>`；`streamChat` 请求带上会话所属 `workspace` |
| `types/chat.ts` | 新增 `WorkspaceSessionGroup { workspacePath; workspaceName; sessions: ChatSession[] }`，`ChatSession` 不动 |
| `stores/session.ts` | 新增 `groups` 状态与 `loadAllSessions()`；保留 `sessions` 计算属性 = 当前焦点项目组，兼容现有引用 |
| `stores/chat.ts` | `send` 时携带当前焦点项目路径；`messagesBySession` 结构不动，跨项目流照常写入 |
| `components/ConversationList.vue` | 双层分组：项目组（组头 = 项目名 + 路径，可折叠）→ 现有时间分组 → 会话行；组头 spinner 复用 `chat.isProcessing` |
| `stores/workspace.ts` | `switch()` 删除 BUSY 分支处理；`afterSwitch` 保持（文件树等会话域数据重置） |

交互规则：

1. 点击**焦点项目**会话：行为不变。
2. 点击**其他项目**会话：先 `switchWorkspace`（P2 后不再 409）→ `afterSwitch` → 加载该会话历史。
3. 切走焦点项目时，其进行中的 SSE 连接不断开（EventSource 与焦点无关），事件继续写入 `messagesBySession`，侧栏该项目组头显示 spinner、会话行保留流光晕。
4. 新建会话 / 发送消息：始终作用于当前焦点项目（现有行为）。

## 5. API 变更表

| 接口 | 变更 | 兼容性 |
| --- | --- | --- |
| `GET /api/chat/sessions` | 保留，语义 = 焦点项目 | 不变 |
| `GET /api/chat/sessions/all` | 新增，返回按项目分组的全量会话 | 纯新增 |
| `GET/POST /api/chat/stream` | 新增可选参数 `workspace`，缺省 = 焦点项目 | 向后兼容 |
| `POST /api/workspace/switch` | P2 移除 409 BUSY；`streaming` 字段改为「有流的项目数」 | 前端同步移除置灰逻辑 |

## 6. 边界与兼容

- **存量数据零迁移**：聚合靠目录扫描，旧会话自动出现在对应项目组下。
- **资源上限**：每项目一份 Agent + MCP 连接 + 模型缓存，注册表上限 8 实例、LRU 回收无流实例；全局配置与技能目录为只读共享，无冲突。
- **并发语义**：同一项目内多会话并行的行为与现状一致，无回归；跨项目互斥由「切项目必须等流」变为「互不打扰」。
- **附件**：回执仓库按 sessionId 全局存取，与项目无关，不受影响。
- **token 统计**：`/stats/tokens` 仍只算焦点项目，跨项目聚合统计列为后续可选项（P2.5）。
- **桌面壳 / CLI**：不感知此变更（消费的接口语义未破坏）。

## 7. 落地顺序

1. P1：`listSessions(Path)` + `/sessions/all` + 前端双层分组（一个后端接口 + 列表渲染改造）。
2. P2：`ChatServiceRegistry` 多实例 + 实例级流计数 + `/switch` 解除互斥 + stream 带 `workspace`。
3. P2.5（可选）：跨项目 token 聚合统计、项目组折叠状态持久化、无流实例 LRU 回收调优。
