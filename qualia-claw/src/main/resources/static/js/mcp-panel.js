/**
 * McpPanel —— MCP 管理视图（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 侧边栏底部「MCP 管理」菜单的承载模块（与 js/skill-panel.js 同构）：
 *   - 点击菜单将聊天区域整体替换为 MCP 服务器卡片网格，再点还原对话
 *   - 数据来自 GET /api/config 的 mcpServers；字段修改（失焦/切换）即时保存：
 *     PUT /api/config 仅携带 mcpServers（后端按键合并，且自动重载所有智能体配置）
 *   - 与技能视图互斥：打开任一面板自动收起另一个
 *
 * 对外 API：
 *   window.McpPanel.toggle()   切换 MCP 视图/对话视图
 *   window.McpPanel.hide()     回到对话视图（会话切换/智能体切换时由 index.html 调用）
 *   window.McpPanel.isOpen()   当前是否处于 MCP 视图
 */
(function () {
    'use strict';

    let servers = []; // { name, transport, url, enabled, headers: [{k, v}] }
    let open = false;
    const TRANSPORTS = ['streamable-http', 'http-sse', 'stdio'];

    // ===== 样式注入 =====
    const CSS = `
        /* ===== MCP 视图：替换整个聊天区 ===== */
        .chat-area.mcp-mode > *:not(#mcpView) { display: none !important; }
        /* MCP 视图下隐藏右侧预览面板（与对话无关） */
        .chat-area.mcp-mode ~ .steps-panel, .chat-area.mcp-mode .steps-panel { display: none !important; }

        .mcp-view {
            flex: 1;
            display: none; /* 默认隐藏，mcp-mode 激活时才显示，避免 hide 后残留 */
            flex-direction: column;
            overflow-y: auto;
            padding: 26px 30px 34px;
        }
        .chat-area.mcp-mode #mcpView { display: flex; }
        .mcp-view::-webkit-scrollbar { width: 5px; }
        .mcp-view::-webkit-scrollbar-thumb {
            background: var(--scrollbar-thumb);
            border-radius: 8px;
        }

        .mv-inner { max-width: 1040px; width: 100%; margin: 0 auto; }

        .mv-head { display: flex; align-items: center; gap: 11px; margin-bottom: 5px; }
        .mv-icon {
            width: 38px; height: 38px;
            border-radius: 11px;
            background: var(--accent-gradient);
            color: var(--white, #fff);
            font-size: 15px;
            display: flex; align-items: center; justify-content: center;
            box-shadow: var(--shadow-input);
        }
        .mv-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
        .mv-stat {
            font-size: 11px; font-weight: 600;
            color: var(--text-secondary);
            background: var(--bg-hover);
            border-radius: 100px; padding: 3px 10px;
        }
        .mv-add {
            margin-left: auto;
            display: inline-flex; align-items: center; gap: 7px;
            border: 1px solid rgba(124, 108, 240, 0.25);
            border-radius: 8px;
            background: rgba(124, 108, 240, 0.14);
            color: var(--accent-light);
            font-size: 11.5px; font-weight: 600;
            padding: 7px 14px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .mv-add:hover { background: rgba(124, 108, 240, 0.24); border-color: rgba(124, 108, 240, 0.4); }
        .mv-desc {
            font-size: 11.5px; color: var(--text-muted);
            margin: 2px 0 20px 49px;
            line-height: 1.7;
        }

        /* MCP 网格：一行多个，自适应列数 */
        .mc-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 12px;
        }

        /* MCP 卡片（竖向布局） */
        .mc-card {
            display: flex;
            flex-direction: column;
            border: 1px solid var(--border-color);
            background: var(--bg-input);
            border-radius: var(--radius-sm);
            padding: 16px;
            transition: all 0.18s;
        }
        .mc-card:hover { border-color: var(--border-active); transform: translateY(-1px); }
        .mc-card.disabled { opacity: 0.55; }
        .mc-card.disabled:hover { opacity: 0.75; }

        .mc-top { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
        .mc-icon {
            width: 34px; height: 34px; flex-shrink: 0;
            border-radius: 10px;
            background: var(--accent-gradient);
            color: var(--white, #fff);
            font-size: 13px;
            display: flex; align-items: center; justify-content: center;
        }
        .mc-card.disabled .mc-icon { background: var(--bg-hover); color: var(--text-muted); }
        .mc-name {
            flex: 1; min-width: 0;
            font-size: 13px; font-weight: 650;
            color: var(--text-primary);
        }
        .mc-src {
            font-size: 10px; font-weight: 650;
            color: var(--accent-light);
            background: var(--bg-active);
            border-radius: 5px; padding: 2px 7px;
            flex-shrink: 0;
        }

        .mc-row { margin-bottom: 9px; }
        .mc-row label {
            display: block;
            font-size: 10.5px; font-weight: 500;
            color: var(--text-muted);
            letter-spacing: 0.3px;
            margin-bottom: 4px;
        }
        .mc-input, .mc-select {
            width: 100%;
            box-sizing: border-box;
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            color: var(--text-primary);
            font-size: 11.5px;
            padding: 7px 10px;
            outline: none;
            transition: border-color 0.15s;
        }
        .mc-input:focus, .mc-select:focus { border-color: var(--accent); }
        .mc-select { cursor: pointer; }

        .mc-kv { display: flex; gap: 6px; margin-bottom: 6px; align-items: center; }
        .mc-kv .mc-input { flex: 1; min-width: 0; }
        .mc-kv-del {
            width: 24px; height: 24px; flex-shrink: 0;
            display: inline-flex; align-items: center; justify-content: center;
            background: none; border: none;
            color: var(--text-muted); font-size: 11px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .mc-kv-del:hover { color: var(--error); background: rgba(248, 113, 113, 0.09); }
        .mc-kv-add {
            display: inline-flex; align-items: center; gap: 5px;
            background: none; border: none;
            color: var(--accent-light); font-size: 10.5px;
            padding: 3px 0;
            cursor: pointer;
        }
        .mc-kv-add:hover { text-decoration: underline; }

        .mc-foot {
            margin-top: 12px; padding-top: 10px;
            border-top: 1px solid var(--border-color);
            display: flex; align-items: center;
        }
        .mc-del {
            display: inline-flex; align-items: center; gap: 6px;
            background: none; border: none;
            color: var(--text-muted); font-size: 11.5px;
            padding: 4px 8px; border-radius: 6px;
            transition: all 0.15s;
            opacity: 0;
            cursor: pointer;
        }
        .mc-card:hover .mc-del { opacity: 1; }
        .mc-del:hover { color: var(--error); background: rgba(248, 113, 113, 0.09); }

        /* 启用开关：与设置弹窗同款；作用域限定在 #mcpView，避免依赖 settings.js 的样式注入 */
        #mcpView .mc-foot .toggle-switch {
            margin-left: auto;
            position: relative;
            display: inline-block;
            width: 40px;
            height: 22px;
            flex-shrink: 0;
        }
        #mcpView .toggle-switch input { opacity: 0; width: 0; height: 0; }
        #mcpView .toggle-slider {
            position: absolute;
            cursor: pointer;
            inset: 0;
            background-color: var(--text-muted);
            transition: background-color 0.2s;
            border-radius: 4px;
        }
        #mcpView .toggle-slider:before {
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
        #mcpView .toggle-switch input:checked + .toggle-slider { background-color: var(--accent); }
        #mcpView .toggle-switch input:checked + .toggle-slider:before { transform: translateX(18px); }

        /* 空态 */
        .mv-empty {
            padding: 60px 20px; text-align: center;
            color: var(--text-muted); font-size: 12px; line-height: 2.1;
        }
        .mv-empty i { font-size: 30px; display: block; margin-bottom: 12px; opacity: 0.5; }
    `;

    function injectStyle() {
        if (document.getElementById('mcp-panel-style')) return;
        const style = document.createElement('style');
        style.id = 'mcp-panel-style';
        style.textContent = CSS;
        document.head.appendChild(style);
    }

    function esc(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    // ===== DOM =====

    function ensureView() {
        let view = document.getElementById('mcpView');
        if (view) return view;
        view = document.createElement('div');
        view.className = 'mcp-view';
        view.id = 'mcpView';
        document.querySelector('.chat-area').appendChild(view);
        bindEvents(view);
        return view;
    }

    // ===== 数据 =====

    async function load() {
        try {
            const res = await fetch('/api/config');
            const cfg = res.ok ? (await res.json()) : {};
            servers = (cfg.mcpServers || []).map(s => ({
                name: s.name || '',
                transport: s.transport || 'streamable-http',
                url: s.url || '',
                enabled: s.enabled !== false,
                headers: Object.entries(s.headers || {}).map(([k, v]) => ({ k, v: String(v) }))
            }));
        } catch (e) {
            servers = [];
        }
    }

    /** 整理为后端 payload 格式（headers 数组 → 对象，丢弃空键） */
    function payload() {
        return servers.map(s => ({
            name: String(s.name || '').trim(),
            transport: s.transport || 'streamable-http',
            url: String(s.url || '').trim(),
            enabled: s.enabled !== false,
            headers: (s.headers || []).reduce((o, h) => {
                const k = String(h.k || '').trim();
                if (k) o[k] = String(h.v || '');
                return o;
            }, {})
        }));
    }

    /** 即时保存：仅提交 mcpServers，后端按键合并不影响模型密钥 */
    async function save() {
        try {
            const res = await fetch('/api/config', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mcpServers: payload() })
            });
            const result = await res.json().catch(() => ({}));
            if (res.ok && result.success !== false) {
                if (window.loadMcpBadge) window.loadMcpBadge(); // 同步顶栏 MCP 数量
            } else {
                alert(result.error || result.message || '保存失败');
                await load();
                render();
            }
        } catch (e) {
            alert('请求失败: ' + e.message);
            await load();
            render();
        }
    }

    // ===== 渲染 =====

    function render() {
        const view = ensureView();
        const enabledCount = servers.filter(s => s.enabled !== false).length;
        const cards = servers.map((s, i) => {
            const enabled = s.enabled !== false;
            const kvRows = (s.headers || []).map((h, j) => `
                <div class="mc-kv">
                    <input class="mc-input" value="${esc(h.k)}" placeholder="Header 名" data-idx="${i}" data-hidx="${j}" data-hfield="k">
                    <input class="mc-input" value="${esc(h.v)}" placeholder="Header 值" data-idx="${i}" data-hidx="${j}" data-hfield="v">
                    <button class="mc-kv-del" title="删除" data-idx="${i}" data-hidx="${j}" data-act="delHeader"><i class="fas fa-times"></i></button>
                </div>`).join('');
            return `
            <div class="mc-card ${enabled ? '' : 'disabled'}">
                <div class="mc-top">
                    <span class="mc-icon"><i class="fas fa-server"></i></span>
                    <input class="mc-input mc-name" value="${esc(s.name)}" placeholder="服务唯一标识" data-idx="${i}" data-field="name">
                    <span class="mc-src">MCP</span>
                </div>
                <div class="mc-row">
                    <label>传输方式</label>
                    <select class="mc-select" data-idx="${i}" data-field="transport">
                        ${TRANSPORTS.map(t => `<option value="${t}" ${s.transport === t ? 'selected' : ''}>${t}</option>`).join('')}
                    </select>
                </div>
                <div class="mc-row">
                    <label>服务地址</label>
                    <input class="mc-input" value="${esc(s.url)}" placeholder="http(s)://..." data-idx="${i}" data-field="url">
                </div>
                <div class="mc-row">
                    <label>Headers</label>
                    ${kvRows}
                    <button class="mc-kv-add" data-idx="${i}" data-act="addHeader"><i class="fas fa-plus"></i> 添加 Header</button>
                </div>
                <div class="mc-foot">
                    <button class="mc-del" title="删除服务器" data-idx="${i}" data-act="delServer">
                        <i class="far fa-trash-alt"></i> 删除
                    </button>
                    <label class="toggle-switch" title="${enabled ? '点击禁用' : '点击启用'}">
                        <input type="checkbox" ${enabled ? 'checked' : ''} data-idx="${i}" data-field="enabled">
                        <span class="toggle-slider"></span>
                    </label>
                </div>
            </div>`;
        }).join('');

        view.innerHTML = `
            <div class="mv-inner">
                <div class="mv-head">
                    <span class="mv-icon"><i class="fas fa-server"></i></span>
                    <span class="mv-title">MCP 管理</span>
                    <span class="mv-stat">${enabledCount} 已启用 / 共 ${servers.length}</span>
                    <button class="mv-add" data-act="addServer"><i class="fas fa-plus"></i> 新增 MCP 服务器</button>
                </div>
                <div class="mv-desc">MCP 服务器全局生效，所有智能体共享。修改后自动保存并重载智能体配置。</div>
                ${servers.length
                    ? `<div class="mc-grid">${cards}</div>`
                    : '<div class="mv-empty"><i class="fas fa-server"></i>暂无 MCP 服务器<br/>点击右上角「新增 MCP 服务器」添加</div>'}
            </div>
        `;
    }

    // ===== 事件（委托绑定一次，重渲染后依然有效） =====

    function bindEvents(view) {
        // 字段修改（失焦/切换时）即时保存
        view.addEventListener('change', (e) => {
            const el = e.target;
            const idx = el.dataset.idx !== undefined ? Number(el.dataset.idx) : null;
            if (idx === null || !servers[idx]) return;
            if (el.dataset.field) {
                servers[idx][el.dataset.field] = el.dataset.field === 'enabled' ? el.checked : el.value;
                render();
                save();
            } else if (el.dataset.hfield) {
                const h = servers[idx].headers[Number(el.dataset.hidx)];
                if (h) {
                    h[el.dataset.hfield] = el.value;
                    save();
                }
            }
        });

        view.addEventListener('click', async (e) => {
            const btn = e.target.closest('[data-act]');
            if (!btn) return;
            const idx = btn.dataset.idx !== undefined ? Number(btn.dataset.idx) : null;
            switch (btn.dataset.act) {
                case 'addServer':
                    servers.push({ name: '', transport: 'streamable-http', url: '', enabled: true, headers: [] });
                    render();
                    // 聚焦新卡片的名称输入框
                    view.querySelectorAll('.mc-name')[servers.length - 1]?.focus();
                    break;
                case 'delServer': {
                    const name = servers[idx] && servers[idx].name ? `"${servers[idx].name}"` : '该服务器';
                    if (!confirm(`确定要删除 MCP 服务器 ${name} 吗？`)) return;
                    servers.splice(idx, 1);
                    render();
                    await save();
                    break;
                }
                case 'addHeader':
                    servers[idx].headers.push({ k: '', v: '' });
                    render();
                    break;
                case 'delHeader':
                    servers[idx].headers.splice(Number(btn.dataset.hidx), 1);
                    render();
                    save();
                    break;
            }
        });
    }

    // ===== 视图切换 =====

    function setMenuActive(active) {
        const btn = document.getElementById('mcpMenuBtn');
        if (btn) btn.classList.toggle('active', active);
    }

    function show() {
        injectStyle();
        if (window.SkillPanel) window.SkillPanel.hide(); // 与技能视图互斥
        if (window.ModelPanel) window.ModelPanel.hide(); // 与模型视图互斥
        open = true;
        ensureView();
        document.querySelector('.chat-area').classList.add('mcp-mode');
        setMenuActive(true);
        load().then(render);
    }

    function hide() {
        if (!open) return;
        open = false;
        document.querySelector('.chat-area').classList.remove('mcp-mode');
        setMenuActive(false);
    }

    function toggle() {
        if (open) hide();
        else show();
    }

    window.McpPanel = {
        toggle,
        show,
        hide,
        isOpen: () => open
    };
})();
