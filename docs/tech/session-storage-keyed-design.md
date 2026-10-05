# 会话存储键控化设计（qualia-code）

> 目标：会话数据（聊天记录 / 摘要 / 附件）从「工作区目录内」迁出，改为**全局目录双轨存储**——自由问答（默认，不绑定工作区）与绑定工作区（可选，按工作区键控）两域；工作区内只保留项目级配置（AGENT.md / skills）。并评估对 qualia-core 的影响。

## 1. 背景与现状问题

现状：会话数据存于 `{workspace}/.qualia/sessions/{sessionId}/`，路径在产品层被硬编码了三处：

| 位置 | 现状逻辑 |
| --- | --- |
| `ChatService.initialize()`（L146） | `memoryDir = workspacePath.resolve(".qualia").resolve("sessions")` |
| `ChatService.ensureMemory()`（L359） | 同上（与上重复，口径已分裂） |
| `AttachmentService.repositoryDir()` | `WebApplication.getCurrentWorkspace().resolve(".qualia").resolve("sessions")...`（第三处硬编码） |

问题：

1. **污染项目目录**：每个项目被迫出现 `.qualia/`，用户需自行 gitignore；聊天记录是用户个人状态，不是项目产物。
2. **附件误提交风险**：附件（最大 20MB）落在项目内，漏 ignore 即可能进 git / 发布包。
3. **生命周期错绑**：删除 / 清空项目 = 会话历史全丢。
4. **聚合扫描脆弱**：共存设计 P1 需遍历 `WorkspaceHistory.list()` 逐项目探测 `.qualia/sessions`，项目盘未挂载即跳过。
5. **路径口径分裂**：三处独立拼装路径，改一处漏两处。

先例：**qualia-claw 已完成同类迁移**（`ClawWorkspace.getMemoryDir()` → `~/.qualia/claw/agents/{agentId}/memory`），动机记录在案——记忆归属错位、工作区面板污染、清空工作区丢历史。业界惯例一致（Claude Code 存 `~/.claude/projects/{路径编码}/`，Cursor/Windsurf 存全局应用数据，均按项目路径键控）。

## 2. 目录布局设计

### 2.1 目标布局

```text
~/.qualia/code/
├── workspaces.json                 # 不变：最近工作区历史（UI 展示用）
└── workspaces/                     # 新增：按工作区键控的数据根
    └── {wsKey}/
        ├── meta.json               # {"path": "D:\\Projects\\qualia", "lastOpened": ...}
        └── sessions/
            └── {sessionId}/        # 单会话目录，内部布局不变
                ├── session.json    # 聊天记录
                ├── summaries.json  # 压缩摘要
                └── files/{附件id}/ # 附件（meta.json + payload）
```

关键不变量：**单会话目录内部布局完全不变**（会话域聚合原则保持：记录、摘要、附件同域，删会话即删目录），变的只是 sessions 根的位置。`JsonMemory` 无感知。

### 2.2 工作区键（wsKey）规则

```java
static String wsKey(Path workspace) {
    String normalized = workspace.toAbsolutePath().normalize().toString();
    if (IS_WINDOWS) normalized = normalized.toLowerCase();   // 与 WorkspaceHistory 大小写不敏感去重对齐
    String encoded = normalized.replaceAll("[^A-Za-z0-9-]", "-");  // D:\Projects\qualia → D--Projects-qualia
    String hash8 = sha256Hex(normalized).substring(0, 8);
    return encoded + "-" + hash8;                            // d--projects-qualia-3fa1b2c4
}
```

设计取舍：

| 方案 | 问题 |
| --- | --- |
| 纯路径编码 | `D:\a b` 与 `D:\a-b` 编码同串（碰撞）；超长路径受 Windows MAX_PATH 限制 |
| 纯哈希 | 不可读，排查 / 手工清理困难 |
| **编码 + 短哈希（采用）** | 人眼可识别项目；哈希保证唯一；超长时编码段可安全截断（唯一性由哈希兜底） |

`meta.json` 记录原始路径：键反查、聚合视图展示、孤儿目录识别（path 已不存在的键目录可提示清理）。

**注意**：`workspaces.json`（WorkspaceHistory）职责不变——只做「最近打开」的 UI 历史；`workspaces/` 目录是数据归属，两者不做合并（历史可被用户清空，数据目录不能跟着删）。

### 2.3 双轨存储：自由问答（默认）与绑定工作区（可选）

**原则：新建会话默认不绑定工作区（自由问答，开箱即用）；用户可主动绑定某个工作区。会话按归属分轨存储，归属由目录位置表达。**

| 域 | 何时产生 | 存储路径 |
| --- | --- | --- |
| **A 自由问答**（默认） | 新建会话不指定 workspace | `~/.qualia/code/sessions/{sessionId}/` |
| **B 绑定工作区** | 新建会话显式指定 workspace | `~/.qualia/code/workspaces/{wsKey}/sessions/{sessionId}/` |

```text
~/.qualia/code/
├── sessions/                                  # 域 A：自由问答（产品级，不键控）
│   └── {sessionId}/                           # 单会话目录布局与现状一致
└── workspaces/{wsKey}/sessions/{sessionId}/   # 域 B：绑定会话（按工作区键）
```

配套语义：

- **归属表达**：目录位置即归属，session 数据不加工作区字段；会话列表接口合并两域返回——自由会话打 `free` 标，绑定会话带 `workspacePath`（前端按组展示）。
- **消息路由**：创建（`POST /api/chat/sessions`）与发消息（`stream`）接口均带可选 `workspace` 参数且保持一致：null → 域 A，非 null → 域 B 键控目录。后端不猜测归属。
- **运行时双 Agent**：
  - 域 B：按工作区实例化 Agent（文件工具 + 工作区技能/AGENT.md + 全局技能），对齐共存 P2 的按项目多实例方向；
  - 域 A：惰性创建自由 Agent——**禁用全部文件类工具**（无工作区根，读写无意义且有风险），保留全局技能 + 全局 AGENT.md，memory 指向 `~/.qualia/code/sessions/`。
- **启动语义简化**：不再需要默认工作区解析与强制弹窗——null 焦点是正常稳态（自由问答不需要工作区）；`currentWorkspace` 退化为纯「文件浏览焦点」，按需选择。
- **存量迁移不受影响**：现有会话全部产生于绑定上下文，全部迁入对应键控目录；域 A 为新增空目录，无迁移。
- **token 统计**：按域聚合（自由域 + 当前查看的项目域）。
- **删除会话**：按域删各自单会话目录，逻辑不变。

## 3. 产品层路径职责收口

新增唯一权威类 `WorkspaceDataStore`（qualia-code `service` 包）：

```java
public final class WorkspaceDataStore {
    /** 域 A（自由问答）会话根：{GLOBAL_CONFIG_DIR}/sessions —— 产品级，不键控 */
    public static Path freeSessionsDir() { ... }

    /** 域 B（绑定）会话根：{GLOBAL_CONFIG_DIR}/workspaces/{wsKey}/sessions —— 三处硬编码的唯一替代口径 */
    public static Path sessionsDir(Path workspace) { migrateIfLegacy(workspace); return ...; }

    /** 单会话目录（附件等复用）：workspace == null → 域 A，非 null → 域 B（按域路由的唯一入口） */
    public static Path sessionDir(Path workspace, String sessionId) { ... }

    /** 工作区键派生（含归一化 + Windows 小写 + 编码 + 哈希） */
    static String wsKey(Path workspace) { ... }

    /** 幂等迁移：工作区内旧会话 → 全局键控目录（见第 4 节） */
    static void migrateIfLegacy(Path workspace) { ... }
}
```

接入点改造：

| 文件 | 改动 |
| --- | --- |
| `ChatService.initialize()` | `new LocalWorkspace(workspacePath)` → 新增 `CodeWorkspace extends LocalWorkspace`，覆写 `getMemoryDir()` 返回 `WorkspaceDataStore.sessionsDir(workspacePath)`（对齐 claw 的 ClawWorkspace 模式）；删除 L146 手工赋值 |
| `ChatService.ensureMemory()` | 删除路径拼装，`memoryDir = new CodeWorkspace(workspacePath).getMemoryDir()`，`JsonMemory` 构造不变 |
| `ChatService` 自由域 | 新增惰性 `freeAgent`：`new JsonMemory(freeSessionsDir())`，`disableTools` 移除全部文件类工具（Read/Write/Replace/Delete/Glob/Grep/Bash），保留全局技能 + 全局 AGENT.md；仅在该域首个消息时创建 |
| `ChatController` | `createSession` / `stream` 增加可选 `workspace` 参数（null = 自由域，非 null = 按键路由）；`getSessions` 合并两域返回并打标；无工作区时 chat 接口不再拒绝（自由域可用），文件类接口维持焦点要求 |
| `AttachmentService.repositoryDir()` | 改调 `WorkspaceDataStore.sessionDir(workspace, sessionId)`（workspace 随请求透传，null = 自由域），删除硬编码 |
| `CodeAgentConfig.initWorkspace()` | 删除（停止预建 `.qualia/memory`、`.qualia/skills`；loader 对缺失目录本就容错，claw 已验证） |
| `WebApplication` / 前端 | `currentWorkspace` 保持可空语义但不再强制弹窗（自由问答开箱即用）；删除 `__qualiaNoWorkspace` 强制流，picker 降级普通入口；侧边栏新增「自由问答」组 |
| `WorkspaceController /switch` | 逻辑不变（仅切文件浏览焦点 + 记历史）；迁移由 `sessionsDir` 首次访问惰性触发，切换零成本 |

## 4. 存量数据迁移（幂等）

时机：`WorkspaceDataStore.sessionsDir()` 首次访问时惰性执行（打开即迁，未打开的工作区不动）——与 claw `migrateLegacyMemory` 同款模式。

步骤（按序，覆盖三代布局）：

1. **旧旧版**：`{ws}/.qualia/memory/*` 存在 → 整体搬入 `{key}/sessions/`（接手原本由 `JsonMemory.migrateLegacyMemoryDir` 承担的迁移——该逻辑依赖「父目录下有 memory/」，迁移到全局后 parent 变了会天然 no-op，故必须在产品层提前完成）。
2. **旧版**：`{ws}/.qualia/sessions/*` 存在 → 逐会话目录搬入 `{key}/sessions/`，**逐项搬移不覆盖**（目标已存在 = 已迁移过，跳过）。
3. 搬空后删除源目录；`{ws}/.qualia` 若因此只剩 AGENT.md / skills 则保留（仍是项目级配置），完全为空则删除。
4. 迁移失败（权限 / 文件占用）：记 warn 日志，**本次直接使用新目录继续运行**（数据不销毁，留在原地，下次打开重试）——与 claw 行为一致。

迁移完成后旧路径不再回读（与 claw 一致，不做双读双写；双写必然漂移，不做）。

## 5. 对 qualia-core 的影响分析

**结论：零破坏性改动，qualia-core 源码无需修改。** 逐点依据：

| core 关注点 | 影响 | 说明 |
| --- | --- | --- |
| `Workspace.getMemoryDir()` 默认实现（`{root}/.qualia/sessions`） | **不改** | 默认值面向「把 core 当库直接嵌入」的调用方，会话随目录走是合理默认；覆写机制已存在且被 claw（`ClawWorkspace`）验证 |
| `HarnessAgent` | **不改** | 仅经 `workspace.getMemoryDir()` 取路径建 `JsonMemory`（L59/L76），对存储位置无感知 |
| `JsonMemory` | **不改** | 只认构造入参的 memoryDir，天然支持任意位置；内部两级旧迁移（memory/ 目录、扁平 json）在新位置天然 no-op，无副作用 |
| `Workspace.getSkillsDir()/getAgentFile()` | **不改** | 工作区级技能与 AGENT.md 保留在工作区（项目级配置语义），默认路径继续有效 |
| null 语义 | **无需引入** | 记忆目录是必需品（HarnessAgent 必建 JsonMemory），不像 skills/agentFile 有「返回 null 表示禁用」的语义；未绑定工作区由自由域兜底（独立 freeAgent + 产品级 `sessions/` 目录），core 不感知 |
| 唯一文档级微调（可选） | 类注释 | `Workspace.getMemoryDir()` javadoc 可补一句「记忆目录可与根目录分离，产品可按身份 / 键控覆写（参见 ClawWorkspace）」；`JsonMemory` 类注释去掉 `workspace/.qualia/sessions` 的强绑定表述。纯注释，无行为影响 |

反向澄清：本次全部代码落在 qualia-code 产品层；core 作为库的默认行为不变，其他嵌入方（含未来产品）不受影响。这也符合既有的分层惯例——claw 的记忆迁移同样只在产品层完成（ClawWorkspace 覆写），core 保持通用默认。

## 6. 对「项目会话共存」设计（project-session-coexistence-design.md）的影响

| 项 | 原设计 | 键控化后 |
| --- | --- | --- |
| P1 `/sessions/all` | 遍历 `WorkspaceHistory.list()` + 逐项目 `Files.isDirectory({ws}/.qualia/sessions)` 探测 | 改为扫描 `~/.qualia/code/workspaces/*/meta.json`，一次目录遍历；项目盘未挂载也能列出（组灰态不可续聊） |
| 分组依据 | 工作区路径探测 | `meta.json.path` |
| P2 `ChatServiceRegistry` | 按 `Path` 键控多实例 | 不冲突：注册表键 = 工作区 Path，存储目录 = 同源派生 `{key}` |
| 「存量数据零迁移」表述 | 依赖会话留在工作区 | 需更新为「惰性幂等迁移」（见第 4 节） |

两份设计正交且互相简化：键控化让共存 P1 的聚合从「逐项目探测」退化为「单树扫描」。

## 7. 边界与权衡（明示接受的代价）

- **项目移动 / 改名**：键失配产生孤儿会话。现状下 `WorkspaceHistory` 同样按路径失配（移动即视作新工作区），非回归；后续可加「meta.json.path 失效 → 提示重新关联旧键目录」作为维护项。
- **subst / symlink**：不同归一化路径 = 不同键，与会话分叉。与现状一致，接受。
- **多设备同步**：会话是本地数据不随项目走——现状实际使用亦如此（无人把 .qualia 提交进 git），非回归。
- **Windows 大小写**：键派生统一小写，与 WorkspaceHistory 去重规则对齐，不会因打开时大小写不同产生双目录。
- **删除会话 / 附件清理**：语义不变（删会话目录）；新增可选维护项「清理 path 失效的键目录」。

## 8. 落地清单

1. `WorkspaceDataStore`（键派生 + 双域 sessionsDir/freeSessionsDir + 幂等迁移）+ 新增 `CodeWorkspace`。
2. `ChatService` 两处路径赋值收口、`AttachmentService` 改造、删除 `CodeAgentConfig.initWorkspace()`。
3. 双轨接口与运行时：`createSession`/`stream` 可选 `workspace` 参数按域路由；自由 Agent（禁文件工具）惰性创建；删除前端强制弹窗，picker 降级普通入口，侧边栏新增「自由问答」组。
4. 共存设计 P1 `/sessions/all` 改为单树扫描 + 自由域合并（可与共存 P1 一并落地）。
5. 文档同步：本文档、`config-storage-paths.md`（Qualia Code 布局表 + 目录全景）、`project-session-coexistence-design.md`（第 1、6 节表述）。
6. 编译验证：`mvn -pl qualia-code -am compile`；手工验证：无工作区直接自由问答、主动绑定工作区新建会话并落对应键控目录、旧项目首次打开迁移、切换工作区、附件上传回读（两域）、删除会话（两域）。
