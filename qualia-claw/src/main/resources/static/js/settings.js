/**
 * SettingsDialog —— 全局设置弹窗组件（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 职责：
 *   1. 居中弹窗承载全局的展示型设置，与工作区无关
 *   2. 工具管理已下沉到智能体编辑弹窗（智能体级关联，js/agent-panel.js）；
 *      技能管理、MCP 管理、模型管理在主界面（js/skill-panel.js、js/mcp-panel.js、js/model-panel.js）
 *   3. 仅展示：配置文件路径 + 夜间模式切换（即时生效，无需保存按钮）
 *
 * 对外 API：
 *   window.openSettings()   打开弹窗（首次打开时拉取配置文件路径）
 *   window.closeSettings()  关闭弹窗
 *   （内部事件处理统一挂在 window.QSettings 命名空间下，供生成的 DOM 引用）
 */
(function () {
    'use strict';

    // ===== 样式注入 =====
    const CSS = `
        /* ===== 设置弹窗（全局展示型设置）===== */
        .settings-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.55);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
        }
        .settings-overlay.open {
            display: flex;
        }
        .settings-dialog {
            width: min(520px, calc(100vw - 48px));
            background: var(--bg-surface);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            box-shadow: var(--shadow);
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .settings-dialog-head {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 13px 16px;
            border-bottom: 1px solid var(--border-color);
        }
        .settings-dialog-head h4 {
            font-size: 12.5px;
            font-weight: 600;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 7px;
        }
        .settings-dialog-head h4 i {
            color: var(--text-muted);
            font-size: 11.5px;
        }
        .settings-dialog-head button {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 12.5px;
            padding: 4px 7px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .settings-dialog-head button:hover {
            background: var(--bg-hover);
            color: var(--text-primary);
        }
        .settings-body {
            padding: 15px 16px;
            display: flex;
            flex-direction: column;
            gap: 18px;
        }
        .set-empty {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 9px;
            padding: 26px 0;
            color: var(--text-muted);
            font-size: 11.5px;
        }
        .set-empty i {
            font-size: 20px;
            opacity: 0.6;
        }
        .set-section-title {
            font-size: 10.5px;
            font-weight: 650;
            color: var(--text-muted);
            letter-spacing: 0.3px;
            margin-bottom: 8px;
        }
        /* 配置文件路径展示 */
        .set-path-row {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 9px 11px;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background: var(--bg-input);
        }
        .set-path-row > i {
            color: var(--text-muted);
            font-size: 11px;
            flex-shrink: 0;
        }
        .set-path {
            flex: 1;
            min-width: 0;
            font-family: 'JetBrains Mono', Consolas, monospace;
            font-size: 11px;
            color: var(--text-secondary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .set-copy-btn {
            flex-shrink: 0;
            border: 1px solid var(--border-color);
            background: var(--bg-app);
            color: var(--text-muted);
            font-size: 10.5px;
            font-family: inherit;
            padding: 3px 9px;
            border-radius: 6px;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 5px;
            transition: all 0.15s ease;
        }
        .set-copy-btn:hover {
            color: var(--text-primary);
            border-color: var(--border-active);
        }
        .set-copy-btn.ok {
            color: var(--success);
            border-color: var(--success);
        }
        .set-hint {
            font-size: 10px;
            color: var(--text-muted);
            margin-top: 6px;
            line-height: 1.6;
        }
        /* 夜间模式行 */
        .set-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            padding: 10px 12px;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background: var(--bg-input);
        }
        .set-row-name {
            font-size: 12px;
            font-weight: 600;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 7px;
        }
        .set-row-name i {
            color: var(--accent-light);
            font-size: 11px;
        }
        .set-row-desc {
            font-size: 10.5px;
            color: var(--text-muted);
            margin-top: 3px;
        }
        /* 启用/禁用开关 */
        .toggle-switch {
            position: relative;
            width: 40px;
            height: 22px;
            flex-shrink: 0;
        }
        .toggle-switch input {
            opacity: 0;
            width: 0;
            height: 0;
        }
        .toggle-slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: var(--text-muted);
            transition: background-color 0.2s;
            border-radius: 4px;
        }
        .toggle-slider:before {
            position: absolute;
            content: "";
            height: 18px;
            width: 18px;
            left: 2px;
            bottom: 2px;
            background-color: var(--white);
            transition: transform 0.2s;
            border-radius: 3px;
        }
        .toggle-switch input:checked + .toggle-slider {
            background-color: var(--accent);
        }
        .toggle-switch input:checked + .toggle-slider:before {
            transform: translateX(18px);
        }
    `;

    // ===== 内部工具函数 =====
    function esc(s) {
        const div = document.createElement('div');
        div.textContent = String(s == null ? '' : s);
        return div.innerHTML;
    }

    // ===== 状态 =====
    let configFile = '';
    let loading = false;
    let loaded = false;

    // ===== DOM 创建 =====
    function mount() {
        const style = document.createElement('style');
        style.id = 'settings-dialog-style';
        style.textContent = CSS;
        document.head.appendChild(style);

        const overlay = document.createElement('div');
        overlay.className = 'settings-overlay';
        overlay.id = 'settingsOverlay';
        overlay.innerHTML = `
            <div class="settings-dialog">
                <div class="settings-dialog-head">
                    <h4><i class="fas fa-sliders-h"></i> 系统设置</h4>
                    <button title="关闭" onclick="closeSettings()"><i class="fas fa-times"></i></button>
                </div>
                <div class="settings-body" id="settingsBody">
                    <div class="set-empty"><i class="fas fa-sliders-h"></i><span>加载中...</span></div>
                </div>
            </div>`;
        document.body.appendChild(overlay);

        // 点击遮罩或按 Esc 关闭
        overlay.addEventListener('click', function (e) {
            if (e.target === this) closeSettings();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') closeSettings();
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', mount);
    } else {
        mount();
    }

    // ===== 打开 / 关闭 =====
    window.openSettings = function () {
        document.getElementById('settingsOverlay').classList.add('open');
        if (!loaded && !loading) load();
        else render(); // 夜间模式状态可能在弹窗外被顶栏按钮改变，每次打开重新回显
    };

    window.closeSettings = function () {
        document.getElementById('settingsOverlay').classList.remove('open');
    };

    // ===== 数据加载 =====
    async function load() {
        loading = true;
        try {
            const res = await fetch('/api/config');
            const cfg = await res.json();
            configFile = cfg.configFile || '';
            loaded = true;
            render();
        } catch (e) {
            document.getElementById('settingsBody').innerHTML =
                '<div class="set-empty"><i class="fas fa-exclamation-circle"></i><span>配置加载失败</span></div>';
        } finally {
            loading = false;
        }
    }

    // ===== 渲染 =====
    function render() {
        const body = document.getElementById('settingsBody');
        if (!body) return;
        // 夜间模式 = 非日间主题（顶栏主题按钮与本地存储由 index.html 的 toggleTheme 统一管理）
        const isDark = !document.body.classList.contains('light-theme');
        body.innerHTML = `
            <div class="set-section">
                <div class="set-section-title">配置文件</div>
                <div class="set-path-row">
                    <i class="fas fa-file-code"></i>
                    <code class="set-path" title="${esc(configFile)}">${esc(configFile || '未知')}</code>
                    <button class="set-copy-btn" onclick="QSettings.copyPath(this)">
                        <i class="fas fa-copy"></i> 复制
                    </button>
                </div>
                <div class="set-hint">模型、MCP、技能与各智能体配置均保存在该文件中，可手动备份或编辑。</div>
            </div>
            <div class="set-section">
                <div class="set-section-title">外观</div>
                <div class="set-row">
                    <div>
                        <div class="set-row-name"><i class="fas fa-moon"></i> 夜间模式</div>
                        <div class="set-row-desc">深色外观，适合低光环境使用</div>
                    </div>
                    <label class="toggle-switch" title="${isDark ? '点击切换到日间模式' : '点击切换到夜间模式'}">
                        <input type="checkbox" ${isDark ? 'checked' : ''} onchange="QSettings.toggleDark(this.checked)">
                        <span class="toggle-slider"></span>
                    </label>
                </div>
            </div>`;
    }

    // ===== 事件处理命名空间（供生成的 DOM 的 inline handler 引用）=====
    window.QSettings = {
        // 复用顶栏主题切换逻辑（index.html 全局函数，同步本地存储与代码高亮主题）
        toggleDark() {
            if (typeof window.toggleTheme === 'function') {
                window.toggleTheme();
            }
        },

        copyPath(btn) {
            if (!configFile) return;
            const done = (ok) => {
                btn.classList.toggle('ok', ok);
                btn.innerHTML = ok ? '<i class="fas fa-check"></i> 已复制' : '<i class="fas fa-copy"></i> 复制';
                setTimeout(() => {
                    btn.classList.remove('ok');
                    btn.innerHTML = '<i class="fas fa-copy"></i> 复制';
                }, 1500);
            };
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(configFile).then(() => done(true)).catch(() => done(false));
            } else {
                done(false);
            }
        }
    };
})();
