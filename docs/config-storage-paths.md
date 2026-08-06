# Qualia 配置存储路径总览

Qualia Code 与 Qualia Claw 两个产品的所有用户级配置与状态统一收敛在 `~/.qualia/` 主目录下，按产品子目录完全隔离——code 用 `code/`、claw 用 `claw/`，模型配置、技能、会话记忆各存一份互不共享；桌面壳产生的锁文件、窗口状态、崩溃日志同样按产品分开，因此两个产品（含各自的桌面版）可以同时运行互不干扰。

## 目录全景

```text
~/.qualia/                          # 主目录（Windows: C:\Users\<用户>\.qualia）
├── code/                           # ══ Qualia Code 产品目录 ══
│   ├── config.json                 # 主配置：模型（含 apiKey）/MCP 服务器/禁用技能与工具
│   ├── workspaces.json             # 工作区打开历史（最近列表，桌面版启动时静默复用）
│   ├── skills/{技能名}/            # 全局技能（本产品所有会话共享）
│   ├── desktop.lock                # 桌面版单实例锁（与 claw 的锁隔离，可同开）
│   ├── desktop.json                # 桌面版窗口状态（尺寸/位置/最大化）
│   └── desktop-error.log           # 桌面版启动崩溃日志
├── claw/                           # ══ Qualia Claw 产品目录 ══
│   ├── config.json                 # 主配置：同 code 字段 + agents 智能体定义数组
│   ├── skills/{技能名}/            # 全局技能（所有智能体共享，按白名单引用）
│   ├── workspaces/{智能体名}/      # 系统托管的智能体工作区（只承载产出物，无工作区级配置）
│   ├── agents/{agentId}/memory/    # 会话记忆（按智能体 id 隔离，与工作区分离）
│   │   ├── {sessionId}.json        # 单会话消息历史
│   │   └── {sessionId}_summaries.json  # 会话压缩摘要
│   ├── desktop.lock                # 桌面版单实例锁（与 code 的锁隔离，可同开）
│   ├── desktop.json                # 桌面版窗口状态（尺寸/位置/最大化）
│   └── desktop-error.log           # 桌面版启动崩溃日志
```

## Qualia Code

| 路径 | 内容 | 读写方 |
|---|---|---|
| `~/.qualia/code/config.json` | `defaultModel`、`models[]`（name/provider/type/apiKey/model/baseUrl）、`mcpServers[]`、`disabledSkills[]`、`disabledTools[]` | `CodeAgentConfig.load()`，Web 设置面板经 `/api/config` 读写 |
| `~/.qualia/code/workspaces.json` | 最近打开的工作区列表（路径去重、上限截断） | `WorkspaceHistory`：启动绑定与运行期切换成功时各记录一次 |
| `~/.qualia/code/skills/{技能名}/` | 全局技能包（SKILL.md + 脚本/文档） | `DirectorySkillLoader`，技能管理界面经 `/api/config/skills` 增删 |
| `{工作区}/.qualia/memory/` | 会话记忆（`{sessionId}.json` 与 `_summaries.json`） | `JsonMemory`，跟随用户选择的项目工作区，记忆与项目绑定 |
| `{工作区}/.qualia/AGENT.md` | 工作区级 system prompt | `HarnessAgent` 初始化时读取 |
| `{工作区}/.qualia/skills/` | 工作区级技能 | `HarnessAgent`，与全局技能同名时项目级优先 |
| `~/.qualia/code/desktop.lock` / `desktop.json` / `desktop-error.log` | 桌面版单实例锁 / 窗口状态 / 崩溃日志 | `qualia-code-desktop` 的 `DesktopLauncher` / `MainWindow` |

## Qualia Claw

| 路径 | 内容 | 读写方 |
|---|---|---|
| `~/.qualia/claw/config.json` | code 全部字段 + `agents[]`（id/name/emoji/role/workspacePath/model/skills 白名单/mcpServers 白名单/createdAt）；白名单字段缺失表示引用全部（存量语义） | `ClawConfig.load()` / `saveAgents()`，智能体编辑经 `/api/agents` |
| `~/.qualia/claw/skills/{技能名}/` | 全局技能（所有智能体共享，按智能体白名单引用） | 同 code |
| `~/.qualia/claw/workspaces/{名称}/` | 系统托管的智能体工作区：名称做非法字符清洗作目录名，创建时绝对路径写死进 definition，改名不搬目录；只承载产出物，工作区级 AGENT.md 与 skills 已禁用（人设由 role 字段接管，技能由全局目录 + 白名单管理） | `AgentRegistry.create()` |
| `~/.qualia/claw/agents/{agentId}/memory/` | 会话记忆，**按智能体 id 隔离、与工作区分离**：清空工作区不丢历史会话 | `ClawWorkspace.getMemoryDir()` → `JsonMemory` |
| `~/.qualia/claw/desktop.lock` / `desktop.json` / `desktop-error.log` | 桌面版单实例锁 / 窗口状态 / 崩溃日志 | `qualia-claw-desktop` 的 `ClawDesktopLauncher` / `MainWindow` |

## config.json 字段结构

```jsonc
{
  "defaultModel": "模型名",
  "models": [
    { "name": "显示名", "provider": "dashscope", "type": "按量或 token-plan",
      "apiKey": "sk-...（支持 ${ENV_VAR} 环境变量引用）",
      "model": "实际模型标识", "baseUrl": "兼容端点，可省略" }
  ],
  "mcpServers": [
    { "name": "服务名", "transport": "streamable-http | http-sse",
      "enabled": true, "url": "...", "headers": { "k": "v" } }
  ],
  "disabledSkills": ["技能名"],
  "disabledTools": ["工具名"],

  // 仅 claw：智能体定义数组
  "agents": [
    { "id": "uuid", "name": "显示名", "emoji": "🦞", "role": "职能描述",
      "workspacePath": "绝对路径", "model": "可省略=用 defaultModel",
      "skills": ["白名单，缺失=引用全部"],
      "mcpServers": ["白名单，缺失=引用全部"],
      "createdAt": 0 }
  ]
}
```
