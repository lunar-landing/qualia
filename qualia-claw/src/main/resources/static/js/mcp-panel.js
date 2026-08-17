/**
 * McpPanel —— MCP 管理视图 v2（只读卡片 + 弹窗编辑 + 验证连接）
 *
 * 侧边栏底部「MCP 管理」菜单的承载模块：
 *   - 卡片只读展示（名称、传输方式、地址、工具数、状态徽章）
 *   - 编辑/新建通过模态弹窗完成，弹窗内含表单编辑 + 验证连接
 *   - 验证结果展现在弹窗内，保存后卡片刷新
 *   - 与技能/模型面板互斥
 *
 * 对外 API：
 *   window.McpPanel.toggle()   切换 MCP 视图/对话视图
 *   window.McpPanel.hide()     回到对话视图
 *   window.McpPanel.isOpen()   当前是否处于 MCP 视图
 */
(function () {
    'use strict';

    let servers = [];  // { name, transport, url, enabled, headers: [{k,v}] }
    let open = false;
    const TRANSPORTS = ['streamable-http', 'http-sse', 'stdio'];

    // 每台服务器的验证结果缓存（卡片上显示徽章），key = name
    let verifyResults = {};

    // ===== 样式注入 =====
    const CSS = `
        /* 视图替换 */
        .chat-area.mcp-mode > *:not(#mcpView) { display: none !important; }
        .chat-area.mcp-mode ~ .steps-panel, .chat-area.mcp-mode .steps-panel { display: none !important; }
        .mcp-view {
            flex: 1; display: none; flex-direction: column;
            overflow-y: auto; padding: 26px 30px 34px;
        }
        .chat-area.mcp-mode #mcpView { display: flex; }
        .mcp-view::-webkit-scrollbar { width: 5px; }
        .mcp-view::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }

        .mv-inner { max-width: 1040px; width: 100%; margin: 0 auto; }
        .mv-head { display: flex; align-items: center; gap: 11px; margin-bottom: 5px; }
        .mv-icon {
            width: 38px; height: 38px; border-radius: 11px;
            background: var(--accent-gradient); color: var(--white, #fff);
            font-size: 15px; display: flex; align-items: center; justify-content: center;
            box-shadow: var(--shadow-input);
        }
        .mv-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
        .mv-stat {
            font-size: 11px; font-weight: 600; color: var(--text-secondary);
            background: var(--bg-hover); border-radius: 100px; padding: 3px 10px;
        }
        .mv-add {
            margin-left: auto; display: inline-flex; align-items: center; gap: 7px;
            border: 1px solid rgba(124,108,240,0.25); border-radius: 8px;
            background: rgba(124,108,240,0.14); color: var(--accent-light);
            font-size: 11.5px; font-weight: 600; padding: 7px 14px;
            cursor: pointer; transition: all 0.15s;
        }
        .mv-add:hover { background: rgba(124,108,240,0.24); border-color: rgba(124,108,240,0.4); }
        .mv-desc {
            font-size: 11.5px; color: var(--text-muted);
            margin: 2px 0 20px 49px; line-height: 1.7;
        }

        /* 卡片网格 */
        .mc-grid {
            display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
            gap: 12px;
        }

        /* 只读卡片 */
        .mc-card {
            display: flex; flex-direction: column;
            border: 1px solid var(--border-color); background: var(--bg-input);
            border-radius: var(--radius-sm); padding: 14px 16px;
            transition: all 0.18s; cursor: default;
        }
        .mc-card:hover { border-color: var(--border-active); transform: translateY(-1px); }
        .mc-card.disabled { opacity: 0.55; }
        .mc-card.disabled:hover { opacity: 0.75; }

        .mc-top { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
        .mc-icon {
            width: 34px; height: 34px; flex-shrink: 0; border-radius: 10px;
            background: var(--accent-gradient); color: var(--white, #fff);
            font-size: 13px; display: flex; align-items: center; justify-content: center;
        }
        .mc-card.disabled .mc-icon { background: var(--bg-hover); color: var(--text-muted); }
        .mc-name-val {
            flex: 1; min-width: 0; font-size: 13px; font-weight: 650;
            color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        }
        .mc-src {
            font-size: 10px; font-weight: 650; color: var(--accent-light);
            background: var(--bg-active); border-radius: 5px; padding: 2px 7px; flex-shrink: 0;
        }

        /* 状态徽章 */
        .mc-status {
            display: inline-flex; align-items: center; gap: 4px;
            font-size: 10px; font-weight: 600; border-radius: 99px;
            padding: 2px 8px; flex-shrink: 0;
        }
        .mc-status.ok { color: var(--success, #22c55e); background: var(--success-bg, rgba(34,197,94,0.09)); }
        .mc-status.fail { color: var(--error, #ef4444); background: var(--error-bg, rgba(239,68,68,0.09)); }
        .mc-status .dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; }
        .mc-status.ok .dot { background: var(--success, #22c55e); }
        .mc-status.fail .dot { background: var(--error, #ef4444); }

        /* 信息行 */
        .mc-info {
            display: flex; flex-wrap: wrap; gap: 6px 16px;
            font-size: 11px; color: var(--text-secondary); padding: 6px 0 8px;
        }
        .mc-info-item { display: flex; align-items: center; gap: 5px; }
        .mc-info-item i { font-size: 10px; color: var(--text-muted); width: 12px; text-align: center; }
        .mc-info-item .mono { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 10.5px; }

        /* 操作栏 */
        .mc-actions {
            display: flex; align-items: center; gap: 4px;
            padding-top: 8px; border-top: 1px solid var(--border-color);
        }
        .mc-act {
            display: inline-flex; align-items: center; gap: 5px;
            background: none; border: none; color: var(--text-muted);
            font-size: 11px; padding: 5px 10px; border-radius: 6px;
            cursor: pointer; transition: all 0.15s;
        }
        .mc-act:hover { background: var(--bg-hover); color: var(--text-secondary); }
        .mc-act.primary { color: var(--accent-light); }
        .mc-act.primary:hover { background: var(--bg-active); }
        .mc-act.danger:hover { color: var(--error, #ef4444); background: rgba(248,113,113,0.09); }

        /* 开关 */
        .mc-actions .toggle-switch {
            margin-left: auto; position: relative; display: inline-block;
            width: 40px; height: 22px; flex-shrink: 0;
        }
        .mc-actions .toggle-switch input { opacity: 0; width: 0; height: 0; }
        .mc-actions .toggle-slider {
            position: absolute; cursor: pointer; inset: 0;
            background-color: var(--text-muted); transition: background-color 0.2s; border-radius: 4px;
        }
        .mc-actions .toggle-slider:before {
            position: absolute; content: ""; height: 18px; width: 18px;
            left: 2px; bottom: 2px; background-color: var(--white);
            transition: transform 0.2s; border-radius: 3px;
        }
        .mc-actions .toggle-switch input:checked + .toggle-slider { background-color: var(--accent); }
        .mc-actions .toggle-switch input:checked + .toggle-slider:before { transform: translateX(18px); }

        /* 空态 */
        .mv-empty {
            padding: 60px 20px; text-align: center;
            color: var(--text-muted); font-size: 12px; line-height: 2.1;
        }
        .mv-empty i { font-size: 30px; display: block; margin-bottom: 12px; opacity: 0.5; }

        /* ============================================================
           编辑弹窗
           ============================================================ */
        .mcp-modal-overlay {
            position: fixed; inset: 0; z-index: 1000;
            background: rgba(0,0,0,0.45);
            display: flex; align-items: center; justify-content: center;
            animation: mcpFadeIn 0.15s ease;
        }
        @keyframes mcpFadeIn { from { opacity: 0; } to { opacity: 1; } }

        .mcp-modal {
            background: var(--bg-surface); border: 1px solid var(--border-color);
            border-radius: 14px; box-shadow: 0 16px 48px rgba(0,0,0,0.18);
            width: 480px; max-height: 82vh;
            display: flex; flex-direction: column;
            animation: mcpSlideUp 0.2s ease;
        }
        @keyframes mcpSlideUp {
            from { opacity: 0; transform: translateY(12px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .mcp-modal-head {
            display: flex; align-items: center; justify-content: space-between;
            padding: 16px 20px; border-bottom: 1px solid var(--border-color);
        }
        .mcp-modal-title {
            font-size: 14px; font-weight: 700; color: var(--text-primary);
            display: flex; align-items: center; gap: 8px;
        }
        .mcp-modal-title i { color: var(--accent); font-size: 13px; }
        .mcp-modal-close {
            width: 28px; height: 28px; display: flex; align-items: center; justify-content: center;
            background: none; border: none; color: var(--text-muted);
            border-radius: 7px; cursor: pointer; font-size: 12px; transition: all 0.15s;
        }
        .mcp-modal-close:hover { background: var(--bg-hover); color: var(--text-primary); }

        .mcp-modal-body { flex: 1; overflow-y: auto; padding: 18px 20px; }

        .mcp-fg { margin-bottom: 16px; }
        .mcp-fg:last-child { margin-bottom: 0; }
        .mcp-fl {
            display: block; font-size: 11.5px; font-weight: 600;
            color: var(--text-secondary); margin-bottom: 6px;
        }
        .mcp-fi, .mcp-fs {
            width: 100%; background: var(--bg-input); border: 1px solid var(--border-color);
            border-radius: 8px; color: var(--text-primary); font-size: 12px;
            padding: 9px 12px; outline: none; transition: border-color 0.15s;
        }
        .mcp-fi:focus, .mcp-fs:focus { border-color: var(--accent); }
        .mcp-fs { cursor: pointer; }
        .mcp-fi.mono { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 11.5px; }

        /* KV 编辑 */
        .mcp-kv-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
        .mcp-kv-row .mcp-fi { flex: 1; min-width: 0; }
        .mcp-kv-del {
            width: 28px; height: 28px; flex-shrink: 0;
            display: inline-flex; align-items: center; justify-content: center;
            background: none; border: none; color: var(--text-muted);
            border-radius: 7px; cursor: pointer; font-size: 11px; transition: all 0.15s;
        }
        .mcp-kv-del:hover { color: var(--error, #ef4444); background: rgba(248,113,113,0.09); }
        .mcp-kv-add {
            display: inline-flex; align-items: center; gap: 5px;
            background: none; border: none; color: var(--accent-light);
            font-size: 11px; padding: 4px 0; cursor: pointer;
        }
        .mcp-kv-add:hover { text-decoration: underline; }

        /* 验证区域 */
        .mcp-verify-bar {
            margin-top: 16px; padding-top: 14px;
            border-top: 1px solid var(--border-color);
        }
        .mcp-verify-btn {
            display: inline-flex; align-items: center; gap: 6px;
            border: 1px solid var(--border-color); border-radius: 8px;
            background: var(--bg-input); color: var(--text-secondary);
            font-size: 11.5px; font-weight: 600; padding: 8px 16px;
            cursor: pointer; transition: all 0.15s;
        }
        .mcp-verify-btn:hover { border-color: var(--accent); color: var(--accent); background: var(--bg-active); }
        .mcp-verify-btn.testing { pointer-events: none; color: var(--accent); border-color: var(--accent); background: var(--bg-active); }
        .mcp-verify-btn i.fa-spinner { animation: mcpSpin 0.8s linear infinite; }
        @keyframes mcpSpin { to { transform: rotate(360deg); } }

        .mcp-verify-result {
            margin-top: 10px; border-radius: 8px; padding: 10px 12px;
            font-size: 11.5px; line-height: 1.7; animation: mcpFadeSlide 0.2s ease;
        }
        @keyframes mcpFadeSlide {
            from { opacity: 0; transform: translateY(-4px); }
            to { opacity: 1; transform: translateY(0); }
        }
        .mcp-verify-result.success { background: var(--success-bg, rgba(34,197,94,0.09)); border: 1px solid var(--success-border, rgba(34,197,94,0.3)); }
        .mcp-verify-result.error { background: var(--error-bg, rgba(239,68,68,0.09)); border: 1px solid var(--error-border, rgba(239,68,68,0.3)); }
        .mcp-vr-hdr { display: flex; align-items: center; gap: 7px; margin-bottom: 4px; font-weight: 600; }
        .mcp-vr-hdr i.success { color: var(--success, #22c55e); }
        .mcp-vr-hdr i.error { color: var(--error, #ef4444); }
        .mcp-vr-body {
            display: grid; grid-template-columns: auto 1fr; gap: 1px 10px;
            font-size: 10.5px; color: var(--text-secondary); padding-left: 20px;
        }
        .mcp-vr-lbl { color: var(--text-muted); white-space: nowrap; }
        .mcp-vr-val { word-break: break-all; }
        .mcp-vr-val.mono { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 10px; }

        /* 弹窗底部 */
        .mcp-modal-foot {
            padding: 12px 20px; border-top: 1px solid var(--border-color);
            display: flex; justify-content: flex-end; gap: 8px;
        }
        .mcp-btn-cancel {
            background: var(--bg-input); border: 1px solid var(--border-color);
            border-radius: 8px; color: var(--text-secondary);
            font-size: 11.5px; font-weight: 600; padding: 8px 18px;
            cursor: pointer; transition: all 0.15s;
        }
        .mcp-btn-cancel:hover { background: var(--bg-hover); border-color: var(--border-active); }
        .mcp-btn-save {
            background: var(--accent); color: var(--white, #fff);
            border: none; border-radius: 8px;
            font-size: 11.5px; font-weight: 600; padding: 8px 22px;
            cursor: pointer; transition: opacity 0.15s;
        }
        .mcp-btn-save:hover { opacity: 0.85; }
    `;

    function injectStyle() {
        if (document.getElementById('mcp-panel-style')) return;
        const s = document.createElement('style');
        s.id = 'mcp-panel-style';
        s.textContent = CSS;
        document.head.appendChild(s);
    }

    function esc(s) {
        const d = document.createElement('div');
        d.textContent = s == null ? '' : String(s);
        return d.innerHTML;
    }

    // ===== DOM =====
    function ensureView() {
        let v = document.getElementById('mcpView');
        if (v) return v;
        v = document.createElement('div');
        v.className = 'mcp-view';
        v.id = 'mcpView';
        document.querySelector('.chat-area').appendChild(v);
        bindListEvents(v);
        return v;
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
        } catch (_) { servers = []; }
    }

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

    async function save() {
        try {
            const res = await fetch('/api/config', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mcpServers: payload() })
            });
            const r = await res.json().catch(() => ({}));
            if (res.ok && r.success !== false) {
                if (window.loadMcpBadge) window.loadMcpBadge();
            } else {
                alert(r.error || r.message || '保存失败');
                await load(); render();
            }
        } catch (e) {
            alert('请求失败: ' + e.message);
            await load(); render();
        }
    }

    // ===== 列表渲染 =====
    function render() {
        const view = ensureView();
        const enabledCount = servers.filter(s => s.enabled !== false).length;
        const cards = servers.map((s, i) => {
            const enabled = s.enabled !== false;
            const vr = verifyResults[s.name];
            const badgeHtml = vr
                ? (vr.success
                    ? `<span class="mc-status ok"><span class="dot"></span> 已连接</span>`
                    : `<span class="mc-status fail"><span class="dot"></span> 连接失败</span>`)
                : '';
            const toolInfo = vr && vr.success ? `${vr.toolCount} 个工具` : '—';
            return `
            <div class="mc-card ${enabled ? '' : 'disabled'}">
                <div class="mc-top">
                    <span class="mc-icon"><i class="fas fa-server"></i></span>
                    <span class="mc-name-val">${esc(s.name) || '(未命名)'}</span>
                    ${badgeHtml}
                    <span class="mc-src">MCP</span>
                </div>
                <div class="mc-info">
                    <span class="mc-info-item"><i class="fas fa-exchange-alt"></i> ${esc(s.transport)}</span>
                    <span class="mc-info-item"><i class="fas fa-link"></i> <span class="mono">${esc(s.url) || '—'}</span></span>
                    <span class="mc-info-item"><i class="fas fa-wrench"></i> ${toolInfo}</span>
                </div>
                <div class="mc-actions">
                    <button class="mc-act primary" data-idx="${i}" data-act="edit"><i class="fas fa-pen"></i> 编辑</button>
                    <button class="mc-act danger" data-idx="${i}" data-act="del"><i class="far fa-trash-alt"></i> 删除</button>
                    <label class="toggle-switch" title="${enabled ? '点击禁用' : '点击启用'}">
                        <input type="checkbox" ${enabled ? 'checked' : ''} data-idx="${i}" data-act="toggle">
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
                    <button class="mv-add" data-act="add"><i class="fas fa-plus"></i> 新增 MCP 服务器</button>
                </div>
                <div class="mv-desc">MCP 服务器全局生效，所有智能体共享。修改后自动保存并重载智能体配置。</div>
                ${servers.length
                    ? `<div class="mc-grid">${cards}</div>`
                    : '<div class="mv-empty"><i class="fas fa-server"></i>暂无 MCP 服务器<br/>点击右上角「新增 MCP 服务器」添加</div>'}
            </div>`;
    }

    // ===== 列表事件 =====
    function bindListEvents(view) {
        view.addEventListener('click', async (e) => {
            const btn = e.target.closest('[data-act]');
            if (!btn) return;
            const idx = btn.dataset.idx !== undefined ? Number(btn.dataset.idx) : null;
            switch (btn.dataset.act) {
                case 'add':
                    openModal(null);
                    break;
                case 'edit':
                    openModal(idx);
                    break;
                case 'del': {
                    const name = servers[idx]?.name ? `"${servers[idx].name}"` : '该服务器';
                    if (!confirm(`确定要删除 MCP 服务器 ${name} 吗？`)) return;
                    delete verifyResults[servers[idx].name];
                    servers.splice(idx, 1);
                    render();
                    await save();
                    break;
                }
            }
        });
        view.addEventListener('change', async (e) => {
            const el = e.target;
            if (el.dataset.act === 'toggle' && el.dataset.idx !== undefined) {
                const idx = Number(el.dataset.idx);
                if (servers[idx]) {
                    servers[idx].enabled = el.checked;
                    render();
                    await save();
                }
            }
        });
    }

    // ===== 弹窗 =====

    let modalDraft = null;  // 当前编辑草稿
    let modalVerify = null; // 弹窗内验证结果
    let modalTesting = false;

    function openModal(idx) {
        if (idx !== null && servers[idx]) {
            modalDraft = {
                name: servers[idx].name,
                transport: servers[idx].transport,
                url: servers[idx].url,
                headers: (servers[idx].headers || []).map(h => ({ ...h })),
                enabled: servers[idx].enabled !== false
            };
            // 复用缓存的验证结果
            modalVerify = verifyResults[servers[idx].name] || null;
        } else {
            modalDraft = { name: '', transport: 'streamable-http', url: '', headers: [], enabled: true };
            modalVerify = null;
        }
        modalTesting = false;
        renderModal(idx);
    }

    function renderModal(editIdx) {
        let overlay = document.getElementById('mcpModalOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'mcpModalOverlay';
            overlay.className = 'mcp-modal-overlay';
            document.body.appendChild(overlay);
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) closeModal(editIdx);
            });
        }
        const d = modalDraft;
        const isStdio = d.transport === 'stdio';

        const kvHtml = (d.headers || []).map((h, j) => `
            <div class="mcp-kv-row">
                <input class="mcp-fi" value="${esc(h.k)}" placeholder="Header 名" data-hidx="${j}" data-hf="k">
                <input class="mcp-fi" value="${esc(h.v)}" placeholder="Header 值" data-hidx="${j}" data-hf="v">
                <button class="mcp-kv-del" data-act="kvDel" data-hidx="${j}"><i class="fas fa-times"></i></button>
            </div>`).join('');

        let vrHtml = '';
        if (modalVerify) {
            if (modalVerify.success) {
                const names = (modalVerify.toolNames || []).slice(0, 6).join(', ');
                const more = (modalVerify.toolNames || []).length > 6 ? ' ...' : '';
                vrHtml = `
                <div class="mcp-verify-result success">
                    <div class="mcp-vr-hdr"><i class="fas fa-check-circle success"></i> 连接成功</div>
                    <div class="mcp-vr-body">
                        <span class="mcp-vr-lbl">服务器</span><span class="mcp-vr-val">${esc(modalVerify.serverName || d.name)}</span>
                        <span class="mcp-vr-lbl">响应耗时</span><span class="mcp-vr-val mono">${modalVerify.latencyMs}ms</span>
                        <span class="mcp-vr-lbl">可用工具</span><span class="mcp-vr-val">${modalVerify.toolCount} 个（${esc(names)}${more}）</span>
                    </div>
                </div>`;
            } else {
                vrHtml = `
                <div class="mcp-verify-result error">
                    <div class="mcp-vr-hdr"><i class="fas fa-times-circle error"></i> 连接失败</div>
                    <div class="mcp-vr-body">
                        <span class="mcp-vr-lbl">错误类型</span><span class="mcp-vr-val">${esc(modalVerify.errorType)}</span>
                        <span class="mcp-vr-lbl">错误信息</span><span class="mcp-vr-val">${esc(modalVerify.errorMessage)}</span>
                    </div>
                </div>`;
            }
        }

        const verifyBtnHtml = modalTesting
            ? `<button class="mcp-verify-btn testing"><i class="fas fa-spinner"></i> 验证中...</button>`
            : modalVerify
                ? `<button class="mcp-verify-btn" data-act="verify"><i class="fas fa-redo"></i> 重新验证</button>`
                : `<button class="mcp-verify-btn" data-act="verify"><i class="fas fa-plug"></i> 验证连接</button>`;

        overlay.innerHTML = `
            <div class="mcp-modal">
                <div class="mcp-modal-head">
                    <div class="mcp-modal-title"><i class="fas fa-server"></i> ${editIdx !== null ? '编辑 MCP 服务器' : '新建 MCP 服务器'}</div>
                    <button class="mcp-modal-close" data-act="modalClose"><i class="fas fa-times"></i></button>
                </div>
                <div class="mcp-modal-body">
                    <div class="mcp-fg">
                        <label class="mcp-fl">名称</label>
                        <input class="mcp-fi" value="${esc(d.name)}" placeholder="服务唯一标识" data-field="name">
                    </div>
                    <div class="mcp-fg">
                        <label class="mcp-fl">传输方式</label>
                        <select class="mcp-fs" data-field="transport">
                            ${TRANSPORTS.map(t => `<option value="${t}" ${d.transport === t ? 'selected' : ''}>${t}</option>`).join('')}
                        </select>
                    </div>
                    <div class="mcp-fg">
                        <label class="mcp-fl">${isStdio ? '命令' : '服务地址'}</label>
                        <input class="mcp-fi mono" value="${esc(d.url)}" placeholder="${isStdio ? 'npx -y @...' : 'http(s)://...'}" data-field="url">
                    </div>
                    <div class="mcp-fg">
                        <label class="mcp-fl">Headers</label>
                        ${kvHtml}
                        <button class="mcp-kv-add" data-act="kvAdd"><i class="fas fa-plus"></i> 添加 Header</button>
                    </div>
                    <div class="mcp-verify-bar">
                        ${verifyBtnHtml}
                        ${vrHtml}
                    </div>
                </div>
                <div class="mcp-modal-foot">
                    <button class="mcp-btn-cancel" data-act="modalCancel">取消</button>
                    <button class="mcp-btn-save" data-act="modalSave">保存</button>
                </div>
            </div>`;

        bindModalEvents(overlay, editIdx);
    }

    function bindModalEvents(overlay, editIdx) {
        // 表单字段同步到 draft
        overlay.querySelectorAll('[data-field]').forEach(el => {
            const handler = () => {
                if (el.dataset.field === 'transport') {
                    modalDraft.transport = el.value;
                    // 切换 transport 时清除验证结果
                    modalVerify = null;
                    renderModal(editIdx);
                } else {
                    modalDraft[el.dataset.field] = el.value;
                }
            };
            el.addEventListener('input', handler);
            el.addEventListener('change', handler);
        });

        // Header KV 字段同步
        overlay.querySelectorAll('[data-hf]').forEach(el => {
            el.addEventListener('input', () => {
                const j = Number(el.dataset.hidx);
                if (modalDraft.headers[j]) {
                    modalDraft.headers[j][el.dataset.hf] = el.value;
                }
            });
        });

        // 按钮
        overlay.addEventListener('click', async (e) => {
            const btn = e.target.closest('[data-act]');
            if (!btn) return;
            switch (btn.dataset.act) {
                case 'modalClose':
                case 'modalCancel':
                    closeModal(editIdx);
                    break;
                case 'kvAdd':
                    modalDraft.headers.push({ k: '', v: '' });
                    renderModal(editIdx);
                    break;
                case 'kvDel': {
                    const hidx = Number(btn.dataset.hidx);
                    modalDraft.headers.splice(hidx, 1);
                    renderModal(editIdx);
                    break;
                }
                case 'verify':
                    await doVerify(editIdx);
                    break;
                case 'modalSave':
                    doSave(editIdx);
                    break;
            }
        });
    }

    async function doVerify(editIdx) {
        const name = modalDraft.name.trim();
        const url = modalDraft.url.trim();
        if (!name) { alert('请填写名称'); return; }
        if (!url) { alert('请填写' + (modalDraft.transport === 'stdio' ? '命令' : '服务地址')); return; }

        modalTesting = true;
        modalVerify = null;
        renderModal(editIdx);

        const headers = (modalDraft.headers || []).reduce((o, h) => {
            const k = String(h.k || '').trim();
            if (k) o[k] = String(h.v || '');
            return o;
        }, {});

        try {
            const res = await fetch('/api/config/mcp/verify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name,
                    transport: modalDraft.transport,
                    url,
                    headers
                })
            });
            modalVerify = res.ok ? await res.json() : { success: false, errorType: '请求失败', errorMessage: `HTTP ${res.status}` };
            // 同步到卡片缓存
            verifyResults[name] = modalVerify;
        } catch (err) {
            modalVerify = { success: false, errorType: '网络错误', errorMessage: err.message };
        }
        modalTesting = false;
        renderModal(editIdx);
    }

    function doSave(editIdx) {
        const name = modalDraft.name.trim();
        if (!name) { alert('请填写名称'); return; }
        const url = modalDraft.url.trim();
        if (!url) { alert('请填写' + (modalDraft.transport === 'stdio' ? '命令' : '服务地址')); return; }

        const entry = {
            name,
            transport: modalDraft.transport,
            url,
            enabled: modalDraft.enabled !== false,
            headers: (modalDraft.headers || []).filter(h => String(h.k || '').trim())
        };

        if (editIdx !== null && servers[editIdx]) {
            // 编辑：如果改名，清除旧名缓存
            const oldName = servers[editIdx].name;
            if (oldName !== name) delete verifyResults[oldName];
            servers[editIdx] = entry;
        } else {
            servers.push(entry);
        }

        closeModal(null);
        render();
        save();
    }

    function closeModal(editIdx) {
        const overlay = document.getElementById('mcpModalOverlay');
        if (overlay) { overlay.remove(); }
        modalDraft = null;
        modalVerify = null;
        modalTesting = false;
    }

    // ===== 视图切换 =====
    function setMenuActive(active) {
        const btn = document.getElementById('mcpMenuBtn');
        if (btn) btn.classList.toggle('active', active);
    }

    function show() {
        injectStyle();
        if (window.SkillPanel) window.SkillPanel.hide();
        if (window.ModelPanel) window.ModelPanel.hide();
        if (window.ExtensionPanel) window.ExtensionPanel.hide();
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
        // 关闭可能残留的弹窗
        const overlay = document.getElementById('mcpModalOverlay');
        if (overlay) overlay.remove();
    }

    function toggle() {
        if (open) hide(); else show();
    }

    window.McpPanel = { toggle, show, hide, isOpen: () => open };
})();
