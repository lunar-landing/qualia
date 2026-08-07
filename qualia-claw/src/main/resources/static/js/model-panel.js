/**
 * ModelPanel —— 模型配置视图（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 侧边栏底部「模型管理」菜单的承载模块（与 js/mcp-panel.js 同构）：
 *   - 点击菜单将聊天区域整体替换为模型卡片网格，再点还原对话
 *   - 数据来自 GET /api/config 的 models/defaultModel；字段修改（失焦/切换）即时保存：
 *     PUT /api/config 仅携带 models 与 defaultModel（后端按键合并，且自动重载所有智能体配置）
 *   - 预览/编辑双模式（单卡粒度）：卡片默认只读预览防误改，点卡片上的「编辑」仅该卡进入
 *     表单态；改动先写入草稿，「完成」统一保存，「取消」丢弃还原
 *   - apiKey 掩码下发，含 **** 保存时后端保留原值，不会覆盖真实密钥
 *   - 与技能/MCP 视图互斥：打开任一面板自动收起其他
 *
 * 对外 API：
 *   window.ModelPanel.toggle()   切换模型视图/对话视图
 *   window.ModelPanel.hide()     回到对话视图（会话切换/智能体切换时由 index.html 调用）
 *   window.ModelPanel.isOpen()   当前是否处于模型视图
 */
(function () {
    'use strict';

    let models = []; // { name, provider, type, model, baseUrl, apiKey }
    let defaultIndex = -1; // 指向默认模型，避免改名后丢失默认标记
    let open = false;
    let editingIndex = null; // 正在编辑的卡片下标，null 表示全部只读（防误改）
    let draft = null;        // 编辑中的临时副本，点「完成」才写回并保存
    let isNewDraft = false;  // 编辑对象是否为刚新增的空卡片（「取消」时整卡移除）
    let defaultSnapshot = -1; // 进入编辑前的默认下标（「取消」时还原默认选择）

    // 服务类型：决定对接协议（当前仅开放按量付费）
    const MODEL_TYPES = [
        { id: 'pay-as-you-go', label: '按量付费' },
        { id: 'token-plan', label: '令牌计划' }
    ];

    // 厂商预设（OpenAI 兼容协议，baseUrl 由后端自动管理）
    const MODEL_PRESETS = {
        dashscope: {
            label: '通义千问（阿里云百炼）',
            models: ['qwen3.7-plus', 'qwen-max-latest', 'qwen-plus-latest', 'qwen3-coder-plus']
        },
        xiaomi: {
            label: '小米MiMo',
            models: ['MiMo']
        },
        openai: {
            label: 'OpenAI',
            models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo']
        }
    };

    // ===== 样式注入 =====
    const CSS = `
        /* ===== 模型视图：替换整个聊天区 ===== */
        .chat-area.model-mode > *:not(#modelView) { display: none !important; }
        /* 模型视图下隐藏右侧预览面板（与对话无关） */
        .chat-area.model-mode ~ .steps-panel, .chat-area.model-mode .steps-panel { display: none !important; }

        .model-view {
            flex: 1;
            display: none; /* 默认隐藏，model-mode 激活时才显示，避免 hide 后残留 */
            flex-direction: column;
            overflow-y: auto;
            padding: 26px 30px 34px;
        }
        .chat-area.model-mode #modelView { display: flex; }
        .model-view::-webkit-scrollbar { width: 5px; }
        .model-view::-webkit-scrollbar-thumb {
            background: var(--scrollbar-thumb);
            border-radius: 8px;
        }

        .md-inner { max-width: 1040px; width: 100%; margin: 0 auto; }

        .md-head { display: flex; align-items: center; gap: 11px; margin-bottom: 5px; }
        .md-icon-lg {
            width: 38px; height: 38px;
            border-radius: 11px;
            background: var(--accent-gradient);
            color: var(--white, #fff);
            font-size: 15px;
            display: flex; align-items: center; justify-content: center;
            box-shadow: var(--shadow-input);
        }
        .md-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
        .md-stat {
            font-size: 11px; font-weight: 600;
            color: var(--text-secondary);
            background: var(--bg-hover);
            border-radius: 100px; padding: 3px 10px;
        }
        .md-add {
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
        .md-add:hover { background: rgba(124, 108, 240, 0.24); border-color: rgba(124, 108, 240, 0.4); }
        /* 编辑卡片底部操作小按钮：取消（ghost）/完成（primary） */
        .md-actions { display: inline-flex; align-items: center; gap: 6px; }
        .md-btn {
            font-size: 11px; font-weight: 600;
            padding: 5px 12px;
            border-radius: 6px;
            border: 1px solid;
            cursor: pointer;
            transition: all 0.15s;
            font-family: inherit;
        }
        .md-btn-ghost {
            background: transparent;
            border-color: var(--border-color);
            color: var(--text-secondary);
        }
        .md-btn-ghost:hover { background: var(--bg-hover); color: var(--text-primary); }
        .md-btn-primary {
            background: var(--accent);
            border-color: var(--accent);
            color: var(--white, #fff);
        }
        .md-btn-primary:hover { background: var(--accent-light); border-color: var(--accent-light); }
        .md-desc {
            font-size: 11.5px; color: var(--text-muted);
            margin: 2px 0 20px 49px;
            line-height: 1.7;
        }

        /* 模型网格：一行多个，自适应列数 */
        .md-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 12px;
        }

        /* 模型卡片（竖向布局） */
        .md-card {
            display: flex;
            flex-direction: column;
            border: 1px solid var(--border-color);
            background: var(--bg-input);
            border-radius: var(--radius-sm);
            padding: 16px;
            transition: all 0.18s;
        }
        .md-card:hover { border-color: var(--border-active); transform: translateY(-1px); }
        .md-card.is-default { border-color: var(--accent); }
        .md-card.is-editing { border-color: var(--accent); }

        .md-top { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
        .md-icon {
            width: 34px; height: 34px; flex-shrink: 0;
            border-radius: 10px;
            background: var(--accent-gradient);
            color: var(--white, #fff);
            font-size: 13px;
            display: flex; align-items: center; justify-content: center;
        }
        .md-card .md-top .md-input { flex: 1; min-width: 0; margin-bottom: 0; }
        /* 预览模式：名称只读文本与行值 */
        .md-name-text {
            flex: 1; min-width: 0;
            font-size: 13px; font-weight: 650;
            color: var(--text-primary);
            overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        }
        .md-value {
            font-size: 11.5px;
            color: var(--text-primary);
            padding: 6px 2px 2px;
            word-break: break-all;
            line-height: 1.5;
        }
        .md-badge {
            font-size: 10px; font-weight: 650;
            color: var(--accent-light);
            background: var(--bg-active);
            border-radius: 5px; padding: 2px 7px;
            flex-shrink: 0;
        }

        .md-row { margin-bottom: 9px; }
        .md-row label {
            display: block;
            font-size: 10.5px; font-weight: 500;
            color: var(--text-muted);
            letter-spacing: 0.3px;
            margin-bottom: 4px;
        }
        .md-input, .md-select {
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
            font-family: inherit;
        }
        .md-input:focus, .md-select:focus { border-color: var(--accent); }
        .md-select { cursor: pointer; }

        .md-foot {
            margin-top: 12px; padding-top: 10px;
            border-top: 1px solid var(--border-color);
            display: flex; align-items: center; justify-content: space-between;
        }
        .md-default-pick {
            display: inline-flex; align-items: center; gap: 5px;
            font-size: 11px; color: var(--text-secondary);
            cursor: pointer; user-select: none;
        }
        .md-default-pick input { accent-color: var(--accent); cursor: pointer; }
        .md-del {
            display: inline-flex; align-items: center; gap: 6px;
            background: none; border: none;
            color: var(--text-muted); font-size: 11.5px;
            padding: 4px 8px; border-radius: 6px;
            transition: all 0.15s;
            opacity: 0;
            cursor: pointer;
        }
        .md-card:hover .md-del { opacity: 1; }
        .md-del:hover { color: var(--error); background: rgba(248, 113, 113, 0.09); }
        /* 编辑卡片中删除按钮保持可见 */
        .md-card.is-editing .md-del { opacity: 0.7; }
        /* 预览卡片底部「编辑」入口：悬停浮现，与删除按钮同风格 */
        .md-edit {
            display: inline-flex; align-items: center; gap: 6px;
            margin-left: auto;
            background: none; border: none;
            color: var(--text-muted); font-size: 11.5px;
            padding: 4px 8px; border-radius: 6px;
            transition: all 0.15s;
            opacity: 0;
            cursor: pointer;
        }
        .md-card:hover .md-edit { opacity: 1; }
        .md-edit:hover { color: var(--accent-light); background: rgba(124, 108, 240, 0.09); }

        /* 空态 */
        .md-empty {
            padding: 60px 20px; text-align: center;
            color: var(--text-muted); font-size: 12px; line-height: 2.1;
        }
        .md-empty i { font-size: 30px; display: block; margin-bottom: 12px; opacity: 0.5; }
    `;

    function injectStyle() {
        if (document.getElementById('model-panel-style')) return;
        const style = document.createElement('style');
        style.id = 'model-panel-style';
        style.textContent = CSS;
        document.head.appendChild(style);
    }

    function esc(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    // 属性值转义（esc 基于 textContent，不处理双引号，不能直接用于 value=""）
    function escAttr(s) {
        return String(s || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    }

    // ===== DOM =====

    function ensureView() {
        let view = document.getElementById('modelView');
        if (view) return view;
        view = document.createElement('div');
        view.className = 'model-view';
        view.id = 'modelView';
        document.querySelector('.chat-area').appendChild(view);
        bindEvents(view);
        return view;
    }

    // ===== 数据 =====

    async function load() {
        try {
            const res = await fetch('/api/config');
            const cfg = res.ok ? (await res.json()) : {};
            models = (cfg.models || []).map(m => ({
                name: m.name || '',
                provider: m.provider || 'dashscope',
                // 仅开放按量付费，历史配置中的其他类型归一化
                type: MODEL_TYPES.some(t => t.id === m.type) ? m.type : 'pay-as-you-go',
                model: m.model || '',
                baseUrl: m.baseUrl || '',
                apiKey: m.apiKey || ''
            }));
            defaultIndex = models.findIndex(m => m.name === cfg.defaultModel);
            if (defaultIndex < 0) defaultIndex = models.length > 0 ? 0 : -1;
        } catch (e) {
            models = [];
            defaultIndex = -1;
        }
    }

    /** 即时保存：仅提交 models 与 defaultModel，后端按键合并不影响其他配置 */
    async function save() {
        try {
            const res = await fetch('/api/config', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    defaultModel: defaultIndex >= 0 ? String(models[defaultIndex].name || '').trim() : '',
                    // 各字段统一 String() 兜底，避免历史/新增对象缺字段时 trim 崩溃
                    models: models.map(m => ({
                        name: String(m.name || '').trim(),
                        provider: String(m.provider || '').trim(),
                        type: m.type,
                        model: String(m.model || '').trim(),
                        baseUrl: String(m.baseUrl || '').trim(),
                        apiKey: String(m.apiKey || '').trim()
                    }))
                })
            });
            const result = await res.json().catch(() => ({}));
            if (res.ok && result.success !== false) {
                if (window.refreshModelSelector) window.refreshModelSelector(); // 同步输入区模型下拉
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

    /** 进入单卡编辑：快照默认下标，draft 承载改动，「取消」时还原 */
    function startEdit(i, isNew) {
        editingIndex = i;
        draft = { ...models[i] };
        isNewDraft = !!isNew;
        defaultSnapshot = defaultIndex;
    }

    // ===== 渲染 =====

    function render() {
        const view = ensureView();
        const defaultName = defaultIndex >= 0 && models[defaultIndex] ? models[defaultIndex].name : '';
        // 单卡粒度：仅 editingIndex 指向的卡片为编辑态，其余保持只读预览
        const cards = models.map((m, i) => (i === editingIndex ? renderEditCard(m, i) : renderPreviewCard(m, i))).join('');
        view.innerHTML = `
            <div class="md-inner">
                <div class="md-head">
                    <span class="md-icon-lg"><i class="fas fa-robot"></i></span>
                    <span class="md-title">模型管理</span>
                    <span class="md-stat">${models.length} 个模型${defaultName ? ' · 默认：' + esc(defaultName) : ''}</span>
                    <button class="md-add" data-act="addModel"><i class="fas fa-plus"></i> 新增模型</button>
                </div>
                <div class="md-desc">模型配置全局生效，所有智能体共享。点卡片底部「编辑」修改单个模型，「完成」后自动保存并重载智能体配置；API Key 掩码展示，不会覆盖原值。</div>
                ${models.length
                    ? `<div class="md-grid">${cards}</div>`
                    : '<div class="md-empty"><i class="fas fa-robot"></i>尚未配置模型<br/>点击右上角「新增模型」添加</div>'}
            </div>
        `;
    }

    /** 编辑卡片：单卡表单态，输入先写入 draft，点「完成」统一保存 */
    function renderEditCard(m, i) {
        const d = draft || m;
        const providerIds = Object.keys(MODEL_PRESETS);
        // 配置中出现预设外的厂商标识时附加为选项，避免回显丢失
        const pids = providerIds.includes(d.provider) ? providerIds : providerIds.concat(d.provider);
        const modelOpts = ((MODEL_PRESETS[d.provider] || {}).models || []).map(v =>
            `<option value="${escAttr(v)}"></option>`
        ).join('');
        return `
            <div class="md-card is-editing">
                <div class="md-top">
                    <span class="md-icon"><i class="fas fa-robot"></i></span>
                    <input class="md-input" value="${escAttr(d.name)}" placeholder="如 qwen-max" data-idx="${i}" data-field="name">
                </div>
                <div class="md-row">
                    <label>厂商</label>
                    <select class="md-select" data-idx="${i}" data-field="provider">
                        ${pids.map(pid => `<option value="${escAttr(pid)}" ${d.provider === pid ? 'selected' : ''}>${esc((MODEL_PRESETS[pid] || {}).label || pid)}</option>`).join('')}
                    </select>
                </div>
                <div class="md-row">
                    <label>类型</label>
                    <select class="md-select" data-idx="${i}" data-field="type">
                        ${MODEL_TYPES.map(t => `<option value="${escAttr(t.id)}" ${d.type === t.id ? 'selected' : ''}>${esc(t.label)}</option>`).join('')}
                    </select>
                </div>
                <div class="md-row">
                    <label>模型</label>
                    <input class="md-input" list="mdModelOpts${i}" value="${escAttr(d.model)}" placeholder="选择或输入模型标识" data-idx="${i}" data-field="model">
                    <datalist id="mdModelOpts${i}">${modelOpts}</datalist>
                </div>
                <div class="md-row">
                    <label>API Key（含 **** 时保持原值不变）</label>
                    <input class="md-input" value="${escAttr(d.apiKey)}" placeholder="输入 API Key" data-idx="${i}" data-field="apiKey">
                </div>
                <div class="md-foot">
                    <label class="md-default-pick" title="设为默认模型">
                        <input type="radio" name="mdDefaultPick" ${i === defaultIndex ? 'checked' : ''} data-idx="${i}" data-act="setDefault">
                        默认模型
                    </label>
                    <span class="md-actions">
                        <button class="md-del" title="删除模型" data-idx="${i}" data-act="delModel"><i class="far fa-trash-alt"></i> 删除</button>
                        <button class="md-btn md-btn-ghost" data-act="cancelEdit">取消</button>
                        <button class="md-btn md-btn-primary" data-act="saveEdit">完成</button>
                    </span>
                </div>
            </div>`;
    }

    /** 预览卡片：只读展示，仅保留「编辑」入口，防止误改 */
    function renderPreviewCard(m, i) {
        const providerLabel = (MODEL_PRESETS[m.provider] || {}).label || m.provider;
        const typeLabel = (MODEL_TYPES.find(t => t.id === m.type) || {}).label || m.type;
        return `
            <div class="md-card ${i === defaultIndex ? 'is-default' : ''}">
                <div class="md-top">
                    <span class="md-icon"><i class="fas fa-robot"></i></span>
                    <span class="md-name-text">${esc(m.name) || '（未命名）'}</span>
                    ${i === defaultIndex ? '<span class="md-badge">默认</span>' : ''}
                </div>
                <div class="md-row"><label>厂商</label><div class="md-value">${esc(providerLabel)}</div></div>
                <div class="md-row"><label>类型</label><div class="md-value">${esc(typeLabel)}</div></div>
                <div class="md-row"><label>模型</label><div class="md-value">${esc(m.model) || '-'}</div></div>
                <div class="md-row"><label>API Key</label><div class="md-value">${esc(m.apiKey) || '-'}</div></div>
                <div class="md-foot">
                    <button class="md-edit" data-idx="${i}" data-act="editModel"><i class="fas fa-pen"></i> 编辑</button>
                </div>
            </div>`;
    }

    // ===== 事件（委托绑定一次，重渲染后依然有效） =====

    function bindEvents(view) {
        // 文本输入只写 draft 不重渲染（避免失焦）；仅编辑中的卡片可写
        view.addEventListener('input', (e) => {
            const el = e.target;
            const idx = el.dataset.idx !== undefined ? Number(el.dataset.idx) : null;
            if (editingIndex === null || idx !== editingIndex || !draft || !el.dataset.field) return;
            draft[el.dataset.field] = el.value;
        });

        view.addEventListener('change', (e) => {
            const el = e.target;
            const idx = el.dataset.idx !== undefined ? Number(el.dataset.idx) : null;
            if (editingIndex === null || idx !== editingIndex || !draft) return;
            // 切换默认模型：仅更新状态，点「完成」时随 draft 一并保存
            if (el.dataset.act === 'setDefault') {
                defaultIndex = idx;
                render();
                return;
            }
            if (!el.dataset.field) return;
            draft[el.dataset.field] = el.value;
            if (el.tagName === 'SELECT' && el.dataset.field === 'provider') {
                // 切换厂商：模型回显该厂商首个常用模型
                const p = MODEL_PRESETS[el.value];
                if (p && p.models.length) draft.model = p.models[0];
            }
            render();
        });

        view.addEventListener('click', async (e) => {
            const btn = e.target.closest('[data-act]');
            if (!btn) return;
            const idx = btn.dataset.idx !== undefined ? Number(btn.dataset.idx) : null;
            switch (btn.dataset.act) {
                case 'addModel': {
                    models.push({
                        name: '', provider: 'dashscope', type: 'pay-as-you-go',
                        model: MODEL_PRESETS.dashscope.models[0],
                        baseUrl: '', apiKey: ''
                    });
                    startEdit(models.length - 1, true); // 先快照新增前的默认下标
                    if (defaultIndex < 0) defaultIndex = models.length - 1;
                    render();
                    // 聚焦新卡片的名称输入框
                    view.querySelectorAll('.md-top .md-input')[models.length - 1]?.focus();
                    break;
                }
                case 'editModel':
                    if (idx === null || !models[idx]) return;
                    startEdit(idx, false);
                    render();
                    break;
                case 'saveEdit': {
                    if (editingIndex === null || !draft) return;
                    if (!String(draft.name || '').trim()) {
                        alert('模型名称不能为空');
                        return;
                    }
                    models[editingIndex] = { ...draft };
                    editingIndex = null; draft = null; isNewDraft = false;
                    render();
                    await save();
                    break;
                }
                case 'cancelEdit': {
                    if (editingIndex === null) return;
                    if (isNewDraft) models.splice(editingIndex, 1); // 取消新增：整卡移除
                    defaultIndex = defaultSnapshot; // 还原编辑期间的默认选择变更
                    editingIndex = null; draft = null; isNewDraft = false;
                    render();
                    break;
                }
                case 'delModel': {
                    if (idx === null || !models[idx]) return;
                    const src = (editingIndex === idx && draft) ? draft : models[idx];
                    const name = src.name ? `"${src.name}"` : '该模型';
                    if (!confirm(`确定要删除模型 ${name} 吗？`)) return;
                    models.splice(idx, 1);
                    if (defaultIndex === idx) defaultIndex = models.length > 0 ? 0 : -1;
                    else if (defaultIndex > idx) defaultIndex--;
                    editingIndex = null; draft = null; isNewDraft = false;
                    render();
                    await save();
                    break;
                }
            }
        });
    }

    // ===== 视图切换 =====

    function setMenuActive(active) {
        const btn = document.getElementById('modelMenuBtn');
        if (btn) btn.classList.toggle('active', active);
    }

    function show() {
        injectStyle();
        if (window.SkillPanel) window.SkillPanel.hide(); // 与技能视图互斥
        if (window.McpPanel) window.McpPanel.hide(); // 与 MCP 视图互斥
        // 每次打开默认全部只读，防止误改
        editingIndex = null; draft = null; isNewDraft = false;
        open = true;
        ensureView();
        document.querySelector('.chat-area').classList.add('model-mode');
        setMenuActive(true);
        load().then(render);
    }

    function hide() {
        if (!open) return;
        open = false;
        document.querySelector('.chat-area').classList.remove('model-mode');
        setMenuActive(false);
    }

    function toggle() {
        if (open) hide();
        else show();
    }

    window.ModelPanel = {
        toggle,
        show,
        hide,
        isOpen: () => open
    };
})();
