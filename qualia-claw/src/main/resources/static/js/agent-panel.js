/**
 * AgentPanel —— 智能体管理（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * Qualia Claw 的多智能体入口（对应 qualia-code 的 project-panel.js 工作区切换器）：
 *   - 侧边栏上半部分平铺智能体列表（#agentList），点击切换当前智能体
 *   - 每行悬停露出编辑按钮，打开管理弹窗编辑（名称/表情/角色/模型）
 *   - 工作区由后端托管（~/.qualia/claw/workspaces/{名称}），表单只读展示
 *   - 流式对话期间切换拦截（列表置灰 + 点击守卫，后端另有 BUSY 兜底）
 *   - 强制模式（启动时没有任何智能体）：弹窗不可关闭，创建成功后整页进入工作状态
 *
 * 对外 API：
 *   window.QAgent.init()          拉取智能体列表并渲染侧边栏（DOMContentLoaded 时调用）
 *   window.QAgent.current()       当前智能体对象（无则 null）
 *   window.QAgent.currentId()     当前智能体 id（无则空串）
 *   window.openAgentManager(id)   打开管理弹窗（传 true 为新建，传智能体 id 为编辑）
 *   window.closeAgentManager()    关闭管理弹窗（强制模式下无效）
 *   切换成功后回调 window.onAgentSwitched()（由 index.html 实现）
 */
(function () {
    'use strict';

    /** 备选表情（创建表单快捷选择） */
    const EMOJIS = ['🦞', '🧑‍💼', '📊', '📣', '📝', '💼', '🎨', '🔍', '🛠️', '📚', '💰', '🤝'];
    /** 当前智能体选择持久化键 */
    const CURRENT_KEY = 'claw-current-agent';

    let agents = [];
    let currentAgent = null;
    /** 强制模式：无智能体时弹窗不可关闭 */
    let forced = false;
    /** 弹窗内正在编辑的智能体 id（null 表示新建） */
    let editingId = null;

    // ===== 样式注入 =====
    const CSS = `
        /* ===== 智能体管理弹窗 ===== */
        .ag-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.55);
            backdrop-filter: blur(3px);
            -webkit-backdrop-filter: blur(3px);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
        }
        .ag-overlay.open { display: flex; animation: agFade 0.18s ease; }
        @keyframes agFade { from { opacity: 0; } to { opacity: 1; } }
        .ag-dialog {
            display: flex;
            width: min(1080px, calc(100vw - 48px));
            height: min(680px, calc(100vh - 64px));
            min-height: 480px;
            background: var(--bg-surface);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            box-shadow: var(--shadow);
            overflow: hidden;
        }
        .ag-overlay.open .ag-dialog { animation: agPop 0.26s cubic-bezier(0.16, 1, 0.3, 1); }
        @keyframes agPop {
            from { opacity: 0; transform: translateY(12px) scale(0.985); }
            to { opacity: 1; transform: none; }
        }

        /* ----- 左栏：智能体列表 ----- */
        .ag-side {
            width: 250px;
            flex-shrink: 0;
            display: flex;
            flex-direction: column;
            border-right: 1px solid var(--border-color);
        }
        .ag-side-head {
            padding: 16px 16px 10px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .ag-side-head h4 {
            font-size: 13px;
            font-weight: 650;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 7px;
            margin-right: auto;
        }
        .ag-side-head h4 i { color: var(--accent-light); font-size: 12px; }
        .ag-close {
            width: 27px; height: 27px;
            border: none; border-radius: 7px;
            background: transparent;
            color: var(--text-muted);
            font-size: 12px; cursor: pointer;
            transition: all 0.15s;
        }
        .ag-close:hover { color: var(--text-primary); background: var(--bg-hover); }
        .ag-list {
            flex: 1;
            overflow-y: auto;
            padding: 4px 10px;
        }
        .ag-item {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 9px 10px;
            border-radius: 9px;
            cursor: pointer;
            border: 1px solid transparent;
            transition: all 0.13s;
        }
        .ag-item:hover { background: var(--bg-hover); }
        .ag-item.active {
            background: var(--bg-active);
            border-color: var(--border-active);
        }
        .ag-item-emoji {
            width: 32px; height: 32px;
            flex-shrink: 0;
            display: flex; align-items: center; justify-content: center;
            font-size: 17px;
            border-radius: 9px;
            background: var(--bg-hover);
        }
        .ag-item-info { min-width: 0; flex: 1; }
        .ag-item-name {
            font-size: 12px; font-weight: 600;
            color: var(--text-primary);
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
        }
        .ag-item-path {
            font-size: 10px; color: var(--text-muted);
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
            margin-top: 2px;
        }
        /* 删除按钮：悬停智能体行时露出 */
        .ag-item-del {
            display: none;
            align-items: center; justify-content: center;
            width: 24px; height: 24px; flex-shrink: 0;
            border: none; border-radius: 6px;
            background: transparent;
            color: var(--text-muted);
            font-size: 11px; cursor: pointer;
            transition: all 0.13s;
        }
        .ag-item:hover .ag-item-del { display: inline-flex; }
        .ag-item-del:hover { color: var(--danger, #f87171); background: rgba(248, 113, 113, 0.1); }
        .ag-item .busy-dot {
            width: 7px; height: 7px; border-radius: 50%;
            background: var(--success, #3fb950);
            flex-shrink: 0;
            animation: agPulse 1.2s ease-in-out infinite;
        }
        @keyframes agPulse { 50% { opacity: 0.35; } }
        .ag-empty {
            padding: 26px 14px;
            text-align: center;
            color: var(--text-muted);
            font-size: 11.5px;
            line-height: 1.7;
        }
        .ag-side-foot { padding: 10px 12px 14px; border-top: 1px solid var(--border-color); }
        .ag-add-btn {
            width: 100%;
            display: flex; align-items: center; justify-content: center; gap: 7px;
            padding: 9px 0;
            border: 1px dashed var(--border-active);
            border-radius: 9px;
            background: transparent;
            color: var(--accent-light);
            font-size: 12px; font-weight: 600;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ag-add-btn:hover { background: var(--bg-active); }

        /* ----- 右栏：编辑表单 / 目录浏览 ----- */
        .ag-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
        .ag-main-head {
            padding: 16px 20px 12px;
            border-bottom: 1px solid var(--border-color);
            font-size: 13px; font-weight: 650;
            color: var(--text-primary);
            display: flex; align-items: center; gap: 8px;
        }
        .ag-main-head i { color: var(--accent-light); font-size: 12px; }
        .ag-form { flex: 1; overflow-y: auto; padding: 16px 20px; }
        .ag-field { margin-bottom: 14px; }
        .ag-field label {
            display: block;
            font-size: 11px; font-weight: 600;
            color: var(--text-muted);
            margin-bottom: 6px;
        }
        .ag-field input[type="text"], .ag-field textarea, .ag-field select {
            width: 100%;
            box-sizing: border-box;
            padding: 8px 11px;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background: var(--bg-app);
            color: var(--text-primary);
            font-size: 12.5px;
            font-family: inherit;
            outline: none;
            transition: border-color 0.15s;
        }
        .ag-field input:focus, .ag-field textarea:focus, .ag-field select:focus {
            border-color: var(--border-active);
        }
        .ag-field textarea { resize: vertical; min-height: 64px; line-height: 1.6; }
        .ag-field .hint { font-size: 10.5px; color: var(--text-muted); margin-top: 5px; }
        /* 基础字段两列网格（工作区已降级为底栏提示，不再占表单控件） */
        .ag-grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
        .ag-emoji-row { display: flex; gap: 6px; flex-wrap: wrap; }
        .ag-emoji-opt {
            width: 32px; height: 32px;
            display: flex; align-items: center; justify-content: center;
            font-size: 16px;
            border-radius: 8px;
            border: 1px solid var(--border-color);
            background: var(--bg-app);
            cursor: pointer;
            transition: all 0.12s;
        }
        .ag-emoji-opt:hover { background: var(--bg-hover); }
        .ag-emoji-opt.active { border-color: var(--border-active); background: var(--bg-active); }
        .ag-foot {
            padding: 12px 20px 16px;
            border-top: 1px solid var(--border-color);
            display: flex; align-items: center; gap: 9px;
        }
        .ag-foot .spacer { flex: 1; }
        .ag-btn {
            padding: 8px 18px;
            border-radius: 8px;
            font-size: 12.5px; font-weight: 600;
            cursor: pointer;
            border: 1px solid var(--border-color);
            background: var(--bg-hover);
            color: var(--text-primary);
            transition: all 0.15s;
        }
        .ag-btn:hover { border-color: var(--border-active); }
        .ag-btn.primary {
            background: var(--accent-gradient, var(--accent));
            border: none;
            color: var(--white, #fff);
        }
        .ag-btn.primary:hover { filter: brightness(1.08); }
        .ag-btn.danger { color: var(--danger, #f85149); }
        .ag-btn.danger:hover { background: rgba(248, 81, 73, 0.1); border-color: var(--danger, #f85149); }
        /* 工作区提示：底栏弱展示，非控件 */
        .ag-ws-hint {
            flex: 1; min-width: 0;
            display: flex; align-items: center; gap: 7px;
            font-size: 10.5px; color: var(--text-muted);
            overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        }
        .ag-ws-hint i { font-size: 10px; color: var(--accent-light); opacity: 0.7; flex-shrink: 0; }
        .ag-btn:disabled { opacity: 0.5; cursor: not-allowed; }
        .ag-msg { font-size: 11.5px; margin-top: 8px; min-height: 15px; }
        .ag-msg.err { color: var(--danger, #f85149); }
        .ag-msg.ok { color: var(--success, #3fb950); }

        /* ===== 引用白名单（技能 / MCP）：Tab 切换单面板 + 全宽卡片网格 ===== */
        .ag-refs-panel {
            border: 1px solid var(--border-color);
            border-radius: 11px;
            background: var(--bg-input);
            overflow: hidden;
        }
        .ag-refs-head {
            display: flex; align-items: center; gap: 8px;
            padding: 10px 13px;
            border-bottom: 1px solid var(--border-color);
        }
        .seg-tabs {
            display: flex; gap: 4px;
            background: var(--bg-app);
            border-radius: 8px; padding: 3px;
        }
        .seg-tab {
            border: none; background: transparent;
            color: var(--text-muted); font-size: 11px; font-weight: 600;
            padding: 5px 14px; border-radius: 6px; cursor: pointer; font-family: inherit;
            display: flex; align-items: center; gap: 6px;
            transition: all 0.13s;
        }
        .seg-tab.active { background: var(--bg-active); color: var(--accent-light); }
        .seg-count {
            font-size: 9.5px; opacity: 0.85;
            background: rgba(255,255,255,0.06);
            border-radius: 100px; padding: 1px 6px;
        }
        .seg-count:empty { display: none; }
        .ref-quick { margin-left: auto; display: flex; gap: 2px; }
        .ref-quick button {
            border: none; background: transparent;
            color: var(--text-muted); font-size: 10.5px;
            padding: 3px 7px; border-radius: 5px; cursor: pointer; font-family: inherit;
        }
        .ref-quick button:hover { color: var(--accent-light); background: var(--bg-active); }

        .ref-tab-body { display: none; }
        .ref-tab-body.show { display: block; }
        .ref-items {
            padding: 10px;
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 8px;
            overflow-y: auto;
            max-height: 220px;
        }
        .ref-items::-webkit-scrollbar { width: 5px; }
        .ref-items::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.12); border-radius: 8px; }

        /* 可点选卡片：选中态主题色描边 + 右上角对勾角标 */
        .ref-card {
            position: relative;
            border: 1px solid var(--border-color);
            border-radius: 9px;
            background: var(--bg-app);
            padding: 10px 11px;
            cursor: pointer;
            transition: all 0.15s;
            display: flex; flex-direction: column; gap: 5px;
        }
        .ref-card:hover { border-color: rgba(255, 255, 255, 0.16); }
        .ref-card.on { border-color: var(--border-active); background: var(--bg-active); }
        .ref-card.on::after {
            content: "\\f00c";
            font-family: "Font Awesome 6 Free"; font-weight: 900;
            position: absolute; top: -1px; right: -1px;
            width: 18px; height: 18px;
            background: var(--accent); color: var(--white, #fff); font-size: 8.5px;
            display: flex; align-items: center; justify-content: center;
            border-radius: 0 8px 0 8px;
        }
        .ref-card.off-global { opacity: 0.45; cursor: not-allowed; }
        .ref-card.off-global::after { display: none; }
        .ref-card-name {
            display: flex; align-items: center; gap: 7px;
            font-size: 11.5px; font-weight: 650; color: var(--text-primary);
            min-width: 0;
        }
        .ref-card-name i { color: var(--accent-light); font-size: 10.5px; flex-shrink: 0; }
        .ref-card-name span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .ref-card-desc {
            font-size: 10px; color: var(--text-muted); line-height: 1.55;
            display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .ref-badge-off {
            font-size: 9px; font-weight: 650;
            color: var(--danger, #f87171);
            background: rgba(248, 113, 113, 0.12);
            border-radius: 4px; padding: 1px 6px;
            align-self: flex-start;
        }
        .ref-empty {
            grid-column: 1 / -1;
            padding: 24px 16px; text-align: center;
            font-size: 11px; color: var(--text-muted); line-height: 1.9;
        }
    `;

    function injectStyle() {
        if (document.getElementById('agent-panel-style')) return;
        const style = document.createElement('style');
        style.id = 'agent-panel-style';
        style.textContent = CSS;
        document.head.appendChild(style);
    }

    function esc(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    // ===== 数据 =====

    async function loadAgents() {
        try {
            const res = await fetch('/api/agents');
            agents = res.ok ? (await res.json()) : [];
        } catch (e) {
            agents = [];
        }
        return agents;
    }

    /** 渲染侧边栏上半部分的智能体平铺列表（点击切换，悬停露出编辑按钮） */
    function renderAgentList() {
        const list = document.getElementById('agentList');
        if (!list) return;
        if (agents.length === 0) {
            list.innerHTML = '<div class="agent-empty">还没有智能体<br/>点右上角 ＋ 创建第一位伙伴</div>';
            return;
        }
        list.innerHTML = agents.map(a => `
            <div class="agent-item ${currentAgent && a.id === currentAgent.id ? 'active' : ''}" data-id="${a.id}" title="${esc(a.workspacePath)}">
                <span class="agent-item-emoji">${esc(a.emoji || '🦞')}</span>
                <span class="agent-item-info">
                    <span class="agent-item-name">${esc(a.name)}</span>
                    ${a.role ? `<span class="agent-item-role">${esc(a.role)}</span>` : ''}
                </span>
                ${a.streaming ? '<span class="agent-busy-dot" title="对话进行中"></span>' : ''}
                <button class="agent-item-edit" title="编辑"><i class="fas fa-pen"></i></button>
            </div>
        `).join('');
        list.querySelectorAll('.agent-item').forEach(item => {
            item.addEventListener('click', () => switchTo(item.dataset.id));
            item.querySelector('.agent-item-edit').addEventListener('click', (e) => {
                e.stopPropagation();
                openManager(item.dataset.id);
            });
        });
    }

    /** 点击侧边栏智能体行切换（流式期间拦截；重复点击忽略） */
    function switchTo(agentId) {
        if (window.isChatStreaming && window.isChatStreaming()) return;
        const agent = agents.find(a => a.id === agentId);
        if (!agent || (currentAgent && currentAgent.id === agentId)) return;
        selectAgent(agent, true);
    }

    function selectAgent(agent, notify) {
        currentAgent = agent;
        localStorage.setItem(CURRENT_KEY, agent.id);
        renderAgentList();
        if (notify && window.onAgentSwitched) window.onAgentSwitched();
    }

    /**
     * 初始化：拉列表，恢复上次选择（不存在则取第一个）
     * 无任何智能体时进入强制创建模式
     */
    async function init() {
        injectStyle();
        await loadAgents();
        if (agents.length === 0) {
            forced = true;
            openManager(true);
            return;
        }
        const savedId = localStorage.getItem(CURRENT_KEY);
        currentAgent = agents.find(a => a.id === savedId) || agents[0];
        renderAgentList();
        if (window.onAgentSwitched) window.onAgentSwitched();
    }

    // ===== 弹窗骨架 =====

    function ensureOverlay() {
        let overlay = document.getElementById('agOverlay');
        if (overlay) return overlay;
        overlay = document.createElement('div');
        overlay.className = 'ag-overlay';
        overlay.id = 'agOverlay';
        overlay.innerHTML = `
            <div class="ag-dialog">
                <div class="ag-side">
                    <div class="ag-side-head">
                        <h4><i class="fas fa-users"></i> 智能体</h4>
                        <button class="ag-close" id="agCloseBtn" title="关闭"><i class="fas fa-times"></i></button>
                    </div>
                    <div class="ag-list" id="agList"></div>
                    <div class="ag-side-foot">
                        <button class="ag-add-btn" id="agAddBtn"><i class="fas fa-plus"></i> 新建智能体</button>
                    </div>
                </div>
                <div class="ag-main" id="agMain"></div>
            </div>
        `;
        document.body.appendChild(overlay);
        overlay.addEventListener('click', e => { if (e.target === overlay) closeManager(); });
        overlay.querySelector('#agCloseBtn').addEventListener('click', closeManager);
        overlay.querySelector('#agAddBtn').addEventListener('click', () => showForm(null));
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && overlay.classList.contains('open')) closeManager();
        });
        return overlay;
    }

    /**
     * 打开管理弹窗
     * @param {boolean|string} [target] true = 新建表单；智能体 id = 编辑该智能体；缺省 = 编辑当前智能体
     */
    function openManager(target) {
        const overlay = ensureOverlay();
        overlay.classList.add('open');
        document.getElementById('agCloseBtn').style.display = forced ? 'none' : '';
        renderList();
        if (target === true || agents.length === 0) {
            showForm(null);
        } else if (typeof target === 'string') {
            showForm(target);
        } else {
            showForm(currentAgent ? currentAgent.id : null);
        }
    }
    window.openAgentManager = openManager;

    function closeManager() {
        if (forced && agents.length === 0) return; // 强制模式：必须先创建
        const overlay = document.getElementById('agOverlay');
        if (overlay) overlay.classList.remove('open');
    }
    window.closeAgentManager = closeManager;

    function renderList() {
        const list = document.getElementById('agList');
        if (!list) return;
        if (agents.length === 0) {
            list.innerHTML = '<div class="ag-empty">还没有智能体<br/>创建一个职能智能体开始办公吧</div>';
            return;
        }
        list.innerHTML = agents.map(a => `
            <div class="ag-item ${a.id === (editingId || (currentAgent && currentAgent.id)) ? 'active' : ''}" data-id="${a.id}">
                <div class="ag-item-emoji">${esc(a.emoji || '🦞')}</div>
                <div class="ag-item-info">
                    <div class="ag-item-name">${esc(a.name)}</div>
                    <div class="ag-item-path">${esc(a.workspacePath)}</div>
                </div>
                ${a.streaming ? '<span class="busy-dot" title="对话进行中"></span>' : ''}
                <button class="ag-item-del" title="删除智能体"><i class="fas fa-trash"></i></button>
            </div>
        `).join('');
        list.querySelectorAll('.ag-item').forEach(item => {
            item.addEventListener('click', () => showForm(item.dataset.id));
        });
        // 行内删除：悬停露出，阻止冒泡避免误触选中
        list.querySelectorAll('.ag-item-del').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                deleteAgent(btn.closest('.ag-item').dataset.id);
            });
        });
    }

    // ===== 编辑表单 =====

    function showForm(agentId) {
        editingId = agentId;
        renderList();
        const agent = agentId ? agents.find(a => a.id === agentId) : null;
        const main = document.getElementById('agMain');
        main.innerHTML = `
            <div class="ag-main-head">
                <i class="fas ${agent ? 'fa-pen' : 'fa-plus'}"></i> ${agent ? '编辑智能体' : '新建智能体'}
            </div>
            <div class="ag-form">
                <div class="ag-grid2">
                    <div class="ag-field">
                        <label>名称</label>
                        <input type="text" id="agName" maxlength="24" placeholder="如：市场专员" value="${agent ? esc(agent.name) : ''}" />
                    </div>
                    <div class="ag-field">
                        <label>模型</label>
                        <select id="agModel"></select>
                    </div>
                </div>
                <div class="ag-field">
                    <label>头像表情</label>
                    <div class="ag-emoji-row" id="agEmojiRow">
                        ${EMOJIS.map(e => `<button type="button" class="ag-emoji-opt ${agent && agent.emoji === e ? 'active' : (!agent && e === '🦞' ? 'active' : '')}" data-emoji="${e}">${e}</button>`).join('')}
                    </div>
                </div>
                <div class="ag-field">
                    <label>职能角色（叠加到系统提示词）</label>
                    <textarea id="agRole" maxlength="500" placeholder="如：资深市场营销专员，擅长活动策划、文案撰写与竞品分析">${agent ? esc(agent.role || '') : ''}</textarea>
                </div>
                <div class="ag-field">
                    <label>引用配置（仅勾选的资源对该智能体可用）</label>
                    <div class="ag-refs-panel">
                        <div class="ag-refs-head">
                            <div class="seg-tabs">
                                <button type="button" class="seg-tab active" data-ref-tab="skills"><i class="fas fa-shapes"></i> 技能 <span class="seg-count" id="agSkillCount"></span></button>
                                <button type="button" class="seg-tab" data-ref-tab="mcp"><i class="fas fa-server"></i> MCP <span class="seg-count" id="agMcpCount"></span></button>
                            </div>
                            <div class="ref-quick">
                                <button type="button" id="agRefAllBtn">全选</button>
                                <button type="button" id="agRefNoneBtn">清空</button>
                            </div>
                        </div>
                        <div class="ref-tab-body show" id="agRefSkillsBody">
                            <div class="ref-items" id="agSkillRefs" data-count="agSkillCount"><div class="ref-empty">加载中</div></div>
                        </div>
                        <div class="ref-tab-body" id="agRefMcpBody">
                            <div class="ref-items" id="agMcpRefs" data-count="agMcpCount"><div class="ref-empty">加载中</div></div>
                        </div>
                    </div>
                </div>
                <div class="ag-msg" id="agMsg"></div>
            </div>
            <div class="ag-foot">
                <div class="ag-ws-hint" title="${agent ? esc(agent.workspacePath || '') : '会话记忆保存在工作区内的 .qualia/memory'}">
                    <i class="fas fa-folder"></i>
                    ${agent
                        ? `工作区：${esc(agent.workspacePath || '')} · 会话记忆保存在 .qualia/memory`
                        : '工作区：创建后自动生成在 ~/.qualia/claw/workspaces/{名称}'}
                </div>
                <div class="spacer"></div>
                <button class="ag-btn" id="agCancelBtn">取消</button>
                <button class="ag-btn primary" id="agSaveBtn">${agent ? '保存' : '创建'}</button>
            </div>
        `;

        // 表情选择
        main.querySelectorAll('.ag-emoji-opt').forEach(btn => {
            btn.addEventListener('click', () => {
                main.querySelectorAll('.ag-emoji-opt').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
            });
        });

        // 引用区 Tab 切换
        main.querySelectorAll('.seg-tab').forEach(tab => {
            tab.addEventListener('click', () => switchRefTab(tab.dataset.refTab));
        });
        // 全选 / 清空：仅作用于当前展示的资源面板
        const quickToggle = (on) => {
            const box = main.querySelector('.ref-tab-body.show .ref-items');
            if (!box) return;
            box.querySelectorAll('.ref-card:not(.off-global)').forEach(c => c.classList.toggle('on', on));
            refreshRefCount(box, box.dataset.count);
        };
        main.querySelector('#agRefAllBtn').addEventListener('click', () => quickToggle(true));
        main.querySelector('#agRefNoneBtn').addEventListener('click', () => quickToggle(false));

        loadModelOptions(agent ? agent.model : null);
        loadRefOptions(agent);

        main.querySelector('#agCancelBtn').addEventListener('click', () => {
            if (agents.length === 0) return; // 强制模式下无取消
            closeManager();
        });
        main.querySelector('#agSaveBtn').addEventListener('click', () => saveForm(agentId));
    }

    /** 引用区 Tab 切换：一次只看一类资源 */
    function switchRefTab(which) {
        document.querySelectorAll('.seg-tab').forEach(t =>
            t.classList.toggle('active', t.dataset.refTab === which));
        document.getElementById('agRefSkillsBody').classList.toggle('show', which === 'skills');
        document.getElementById('agRefMcpBody').classList.toggle('show', which === 'mcp');
    }

    async function loadModelOptions(selected) {
        const sel = document.getElementById('agModel');
        if (!sel) return;
        try {
            const res = await fetch('/api/config');
            const config = await res.json();
            (config.models || []).forEach(m => {
                const opt = document.createElement('option');
                opt.value = m.name;
                opt.textContent = m.name + (m.name === config.defaultModel ? '（默认）' : '');
                sel.appendChild(opt);
            });
            // 智能体未指定模型时预选全局默认项；否则保持第一项
            if (selected) sel.value = selected;
            else if (config.defaultModel) sel.value = config.defaultModel;
        } catch (e) { /* 模型列表拉取失败保留空下拉 */ }
    }

    // ===== 引用白名单（技能 / MCP 逐个勾选） =====

    /** 属性值转义：esc 不处理引号，data-name 需额外处理 */
    function attrEsc(s) {
        return esc(s).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    /**
     * 拉取全局技能池与 MCP 资源池，渲染引用勾选区
     * 勾选初值：白名单数组按名单回显；null 区分两种来源——
     *   存量智能体（无白名单字段）= 全部勾选；新建 = 全部不勾，逐个挑选
     */
    async function loadRefOptions(agent) {
        const skillBox = document.getElementById('agSkillRefs');
        const mcpBox = document.getElementById('agMcpRefs');
        if (!skillBox || !mcpBox) return;
        try {
            const [sres, cres] = await Promise.all([fetch('/api/config/skills'), fetch('/api/config')]);
            const skillPool = sres.ok ? (await sres.json()) : [];
            const config = cres.ok ? (await cres.json()) : {};
            const globalDisabled = Array.isArray(config.disabledSkills) ? config.disabledSkills : [];
            renderRefList(skillBox, 'agSkillCount', skillPool.map(s => ({
                name: s.name,
                sub: s.description || '（无描述）',
                icon: 'fa-shapes',
                globalOff: s.enabled === false || globalDisabled.includes(s.name)
            })), agent ? agent.skills : [], '技能');
            renderRefList(mcpBox, 'agMcpCount', (config.mcpServers || []).map(m => ({
                name: m.name,
                sub: `${m.transport || 'streamable-http'} · ${m.url || ''}`,
                icon: 'fa-server',
                globalOff: m.enabled === false
            })), agent ? agent.mcpServers : [], 'MCP 服务器');
        } catch (e) {
            // 拉取失败标记降级：保存时不提交引用字段，后端保留原值
            [skillBox, mcpBox].forEach(b => {
                b.dataset.loaded = '';
                b.innerHTML = '<div class="ref-empty">资源列表加载失败，保存时将保留现有引用</div>';
            });
        }
    }

    /** 渲染单个资源卡片网格（whitelist 为 null = 存量智能体默认全部勾选；空数组 = 全部不勾） */
    function renderRefList(box, countId, items, whitelist, label) {
        box.dataset.loaded = '1';
        if (!items.length) {
            box.innerHTML = `<div class="ref-empty">暂无可用${label}<br/>请先在对应管理面板中添加</div>`;
            updateRefCount(countId, 0, 0);
            return;
        }
        box.innerHTML = items.map(it => {
            const inList = whitelist === null ? true : whitelist.includes(it.name);
            const on = inList && !it.globalOff;
            return `
            <div class="ref-card ${it.globalOff ? 'off-global' : (on ? 'on' : '')}" data-name="${attrEsc(it.name)}" title="${esc(it.sub)}">
                <div class="ref-card-name"><i class="fas ${it.icon}"></i><span>${esc(it.name)}</span></div>
                <div class="ref-card-desc">${esc(it.sub)}</div>
                ${it.globalOff ? '<span class="ref-badge-off">全局已禁用</span>' : ''}
            </div>`;
        }).join('');
        // 点卡片切换选中（全局已禁用的锁定不可点）
        box.querySelectorAll('.ref-card:not(.off-global)').forEach(card => {
            card.addEventListener('click', () => {
                card.classList.toggle('on');
                refreshRefCount(box, countId);
            });
        });
        refreshRefCount(box, countId);
    }

    function refreshRefCount(box, countId) {
        const total = box.querySelectorAll('.ref-card').length;
        const on = box.querySelectorAll('.ref-card.on').length;
        updateRefCount(countId, on, total);
    }

    function updateRefCount(countId, on, total) {
        const el = document.getElementById(countId);
        if (el) el.textContent = total ? `${on}/${total}` : '';
    }

    /** 收集已选中的白名单名称；资源列表加载失败时返回 null（保存不提交该字段，后端保留原值） */
    function collectRefs(boxId) {
        const box = document.getElementById(boxId);
        if (!box || box.dataset.loaded !== '1') return null;
        return [...box.querySelectorAll('.ref-card.on')]
            .map(c => c.dataset.name);
    }

    function formMsg(text, type) {
        const el = document.getElementById('agMsg');
        if (!el) return;
        el.textContent = text || '';
        el.className = 'ag-msg ' + (type || '');
    }

    function readForm() {
        const emojiBtn = document.querySelector('.ag-emoji-opt.active');
        return {
            name: document.getElementById('agName').value.trim(),
            emoji: emojiBtn ? emojiBtn.dataset.emoji : '🦞',
            role: document.getElementById('agRole').value.trim(),
            model: document.getElementById('agModel').value,
            skills: collectRefs('agSkillRefs'),
            mcpServers: collectRefs('agMcpRefs')
        };
    }

    async function saveForm(agentId) {
        const data = readForm();
        if (!data.name) { formMsg('请填写名称', 'err'); return; }

        try {
            if (agentId) {
                const res = await fetch(`/api/agents/${agentId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data)
                });
                const out = await res.json();
                if (!res.ok || out.success === false) { formMsg(out.message || '保存失败', 'err'); return; }
                await loadAgents();
                const updated = agents.find(a => a.id === agentId);
                renderList();
                showForm(agentId);
                formMsg('已保存', 'ok');
                // 编辑的是当前智能体：刷新侧边栏列表
                if (currentAgent && currentAgent.id === agentId && updated) {
                    currentAgent = updated;
                    renderAgentList();
                }
            } else {
                await createAgent(data);
            }
        } catch (e) {
            formMsg('请求失败: ' + e.message, 'err');
        }
    }

    /**
     * 创建智能体（工作区由后端自动生成在 ~/.qualia/claw/workspaces/{名称}）
     */
    async function createAgent(data) {
        try {
            const res = await fetch('/api/agents', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            const out = await res.json();
            if (!res.ok || out.success === false) {
                formMsg(out.message || '创建失败', 'err');
                return;
            }
            await loadAgents();
            forced = false;
            selectAgent(out.agent, true);
            document.getElementById('agCloseBtn').style.display = '';
            renderList();
            showForm(out.agent.id);
            formMsg('已创建', 'ok');
        } catch (e) {
            formMsg('请求失败: ' + e.message, 'err');
        }
    }

    async function deleteAgent(agentId) {
        const agent = agents.find(a => a.id === agentId);
        if (!agent) return;
        if (agent.streaming) { formMsg('该智能体有对话正在进行，请等待完成', 'err'); return; }
        if (!confirm(`确定删除智能体「${agent.name}」吗？\n（只移除工位，不删除工作区文件与会话记录）`)) return;
        try {
            const res = await fetch(`/api/agents/${agentId}`, { method: 'DELETE' });
            const out = await res.json().catch(() => ({}));
            if (!res.ok || out.success === false) { formMsg(out.message || '删除失败', 'err'); return; }
            await loadAgents();
            if (currentAgent && currentAgent.id === agentId) {
                currentAgent = agents[0] || null;
                if (currentAgent) {
                    localStorage.setItem(CURRENT_KEY, currentAgent.id);
                    renderAgentList();
                    if (window.onAgentSwitched) window.onAgentSwitched();
                } else {
                    renderAgentList();
                }
            }
            editingId = currentAgent ? currentAgent.id : null;
            renderList();
            if (currentAgent) showForm(currentAgent.id);
        } catch (e) {
            formMsg('请求失败: ' + e.message, 'err');
        }
    }

    window.QAgent = {
        init,
        current: () => currentAgent,
        currentId: () => (currentAgent ? currentAgent.id : ''),
        reload: loadAgents
    };
})();
