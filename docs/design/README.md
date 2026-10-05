# UI 设计稿（docs/design）

UI 视觉设计的 mockup 存放目录。全部为**自包含零依赖 HTML**，浏览器直接打开即可查看，多数支持日间/夜间主题切换与方案对比。

技术方案文档已移至 [`docs/tech/`](../tech/README.md)；README 配图在 [`docs/images/`](../images/)。

## 索引

### 全局主题与外观

| 文件 | 说明 |
| --- | --- |
| `dark-theme-mockup.html` / `theme-dark-mockup.html` | 夜间主题方案 |
| `theme-light-mockup.html` / `light-theme-mockup.html` / `light-theme-mockup-v2.html` | 日间主题迭代 |
| `light-theme-near-white.html` / `light-theme-gray.html` / `light-theme-cool-mist.html` / `light-theme-graphite-final.html` | 日间主题选型：近白 / 灰 / 冷雾 / 石墨工程风（定稿） |
| `theme-switcher-mockup.html` | 主题切换器交互 |

### 终端视图（ChatTerminal）

| 文件 | 说明 |
| --- | --- |
| `terminal-chat-theme-mockup.html` | 终端形态聊天总稿（t1~t7 截图对应此稿主题变体） |
| `terminal-layout-mockup.html` / `terminal-sidebar-mockup.html` | 终端布局 / 侧栏 |
| `term-hero-mockup.html` | 终端欢迎页 Hero（neofetch 隐喻信息卡） |
| `term-input-redesign-mockup.html` / `term-input-redesign-mockup-v2.html` | REPL 输入区重设计 |
| `term-table-mockup.html` / `table-mockup.html` / `table-mockup-v2.html` | 终端表格排版 |
| `term-code-lang-mockup.html` / `code-wrap-mockup.html` / `code-wrap-mockup-v2.html` / `code-wrap-mockup-v3.html` / `code-wrap-redesign-mockup.html` | 代码块语言徽标与换行方案 |
| `t-banner-ascii-block-mockup.html` / `t-banner-pixel-duo-mockup.html` / `t-banner-pixel-led-mockup.html` | 终端 ASCII / 像素 banner |

### 聊天消息与流

| 文件 | 说明 |
| --- | --- |
| `blockquote-mockup.html` | 引用块样式 |
| `multi-pane-chat-mockup.html` | 多栏聊天 |
| `act-flow-redesign-mockup.html` / `-v2.html` / `-v3.html` | Act 执行流可视化迭代 |

### 输入区

| 文件 | 说明 |
| --- | --- |
| `input-area-mockup.html` / `input-area-mockup-v2.html` | 输入区方案 |
| `queued-messages-mockup.html` | 待发送队列 · 消息流展示版（已废弃，被输入区方案取代） |
| `queued-messages-inputarea-mockup.html` | 待发送队列 · 输入框顶部三版本对比（V1 紧凑 / V2 卡片 / V3 折叠） |
| `queued-messages-inputarea-v2.html` | V1 精修 + 行内编辑 |
| `queued-messages-inputarea-v3.html` | **定稿**：极简纯文字行 + `#n` 编号（已实现，见 QueuedPanel.vue） |
| `chat-mode-toggle-mockup.html` / `chat-mode-switch-style-mockup.html` | 智能体/问答模式切换 |
| `model-list-redesign-mockup.html` | 模型列表 |

### 侧边栏与导航

| 文件 | 说明 |
| --- | --- |
| `side-rail-icons-mockup.html` / `side-rail-refine-mockup.html` | 侧栏图标导航 |
| `sidebar-view-toggle-mockup.html` / `sidebar-coexistence-styles.html` | 侧栏视图切换 / 共存样式 |
| `session-search-mockup.html` | 会话搜索 |
| `session-status-indicator-mockup.html` | 会话状态指示 |

### 工作区与工具面板

| 文件 | 说明 |
| --- | --- |
| `workspace-browser-redesign.html` | 工作区文件浏览 |
| `changed-files-mockup.html` / `changed-files-native.html` / `changed-files-card-styles.html` / `changed-files-card-styles-v2.html` / `changed-files-minimal.html` / `changed-files-minimal-v2.html` | 变更文件卡片样式迭代 |
| `ws-switch-quick-dropdown-prototype.html` | 工作区快速切换下拉 |
| `tool-list-redesign-mockup.html` | 工具列表 |
| `mcp-verify-mockup.html` | MCP 连接验证 |
| `web-search-list-mockup.html` | 网络搜索结果列表 |
| `settings-dialog-redesign-mockup.html` | 设置弹窗 |

## 配图（PNG）

`v1-default.png`、`t1-full.png` ~ `t7-right-expanded.png`：终端主题截图（对应 `terminal-chat-theme-mockup.html` 的变体与滚动状态）；`verify-chat-history-1-bubble-dark.png`：历史回显验证截图。
