/**
 * ModelPanel —— 模型配置视图（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 侧边栏底部「模型管理」菜单的承载模块（与 js/mcp-panel.js 同构）：
 *   - 点击菜单将聊天区域整体替换为模型卡片网格，再点还原对话
 *   - 数据来自 GET /api/config 的 models/defaultModel；字段修改（失焦/切换）即时保存：
 *     PUT /api/config 仅携带 models 与 defaultModel（后端按键合并，且自动重载所有智能体配置）
 *   - 预览卡片网格 + 弹窗编辑：卡片只读预览防误改，点击卡片/「编辑」打开编辑弹窗；
 *     「保存」校验后写回并即时 PUT 保存，「取消」丢弃草稿；「设为默认」即时生效并保存
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
    let dialogIndex = null; // 编辑弹窗对应的模型下标，-1 表示新增，null 表示弹窗未打开
    let draft = null;       // 弹窗编辑中的临时副本，点「保存」才写回并即时保存
    let defaultSnapshot = -1; // 打开弹窗前的默认下标（「取消」时还原默认选择）
    let dlgKeyVisible = false; // 弹窗内 API Key 明文显示状态

    // 服务类型：决定对接协议（当前仅开放按量付费）
    const MODEL_TYPES = [
        { id: 'pay-as-you-go', label: '按量付费' },
        { id: 'token-plan', label: '令牌计划' }
    ];

    // 厂商预设（OpenAI 兼容协议，baseUrl 由后端自动管理）
    const MODEL_PRESETS = {
        dashscope: {
            label: '通义千问（阿里云百炼）',
            models: ['qwen3.7-plus', 'qwen-max-latest', 'qwen-plus-latest', 'qwen3-coder-plus'],
            types: ['pay-as-you-go']
        },
        deepseek: {
            label: 'DeepSeek',
            models: ['deepseek-chat', 'deepseek-reasoner'],
            types: ['pay-as-you-go']
        },
        xiaomi: {
            label: '小米MiMo',
            models: ['MiMo'],
            types: ['pay-as-you-go', 'token-plan']
        },
        openai: {
            label: 'OpenAI',
            models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo'],
            types: ['pay-as-you-go']
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
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 12px;
        }

        /* 模型预览卡片（mockup 风格：厂商标识 + 模型标识主体 + 元信息标签 + 悬停操作蒙层）*/
        .md-card {
            position: relative;
            display: flex;
            flex-direction: column;
            border: 1px solid var(--border-color);
            background: var(--bg-input);
            border-radius: var(--radius-sm);
            padding: 14px 14px 11px;
            cursor: pointer;
            overflow: hidden;
            transition: border-color 0.18s, transform 0.18s, box-shadow 0.18s;
        }
        .md-card:hover {
            border-color: var(--border-active);
            transform: translateY(-2px);
            box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
        }
        .md-card.is-default { border-color: var(--accent); }

        .md-card-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            margin-bottom: 10px;
        }
        .md-provider {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-size: 11px;
            font-weight: 500;
            color: var(--text-secondary);
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .md-provider .dot {
            width: 18px;
            height: 18px;
            border-radius: 5px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 9px;
            font-weight: 700;
            color: var(--white, #fff);
            flex-shrink: 0;
        }
        .md-dot-dashscope { background: linear-gradient(145deg, #615ced, #8a7cf0); }
        .md-dot-deepseek { background: linear-gradient(145deg, #2f6bff, #5c9bff); }
        .md-dot-xiaomi { background: linear-gradient(145deg, #ff8a00, #ffb340); }
        .md-dot-openai { background: linear-gradient(145deg, #30333e, #5a5f70); }
        .md-dot-custom { background: var(--text-muted); }
        .md-badge-default {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-size: 10px;
            font-weight: 650;
            color: var(--accent-light);
            background: var(--bg-active);
            border-radius: 100px;
            padding: 2px 7px;
            flex-shrink: 0;
        }
        .md-model-id {
            font-size: 13px;
            font-weight: 650;
            color: var(--text-primary);
            margin-bottom: 2px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .md-name-sub {
            font-size: 11px;
            color: var(--text-muted);
            margin-bottom: 10px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .md-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            padding-top: 9px;
            margin-top: auto;
            border-top: 1px dashed var(--border-color);
        }
        .md-tag {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-size: 10px;
            color: var(--text-secondary);
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 100px;
            padding: 2px 7px;
        }
        .md-tag.warn { color: var(--warning); }

        /* 悬停操作蒙层 */
        .md-overlay-actions {
            position: absolute;
            inset: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 7px;
            background: rgba(0, 0, 0, 0.45);
            /* backdrop-filter 元素不受父级 overflow+圆角裁剪，需自带圆角 */
            border-radius: inherit;
            backdrop-filter: blur(4px);
            -webkit-backdrop-filter: blur(4px);
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.18s ease;
        }
        body.light-theme .md-overlay-actions { background: rgba(238, 241, 245, 0.62); }
        .md-card:hover .md-overlay-actions { opacity: 1; pointer-events: auto; }

        /* 添加占位卡 */
        .md-card.add-card {
            align-items: center;
            justify-content: center;
            gap: 7px;
            min-height: 128px;
            border-style: dashed;
            background: transparent;
            color: var(--text-muted);
            font-family: inherit;
        }
        .md-card.add-card:hover { color: var(--accent-light); border-color: var(--accent); box-shadow: none; }
        .md-card.add-card .plus { font-size: 18px; line-height: 1; }
        .md-card.add-card p { font-size: 11.5px; }

        .md-row { margin-bottom: 9px; }
        .md-row label {
            display: block;
            font-size: 10.5px; font-weight: 500;
            color: var(--text-muted);
            letter-spacing: 0.3px;
            margin-bottom: 4px;
        }
        .md-input {
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
        .md-input:focus { border-color: var(--accent); }
        /* 自定义下拉选择器 */
        .md-cs {
            position: relative;
        }
        .md-cs-trigger {
            display: flex;
            align-items: center;
            justify-content: space-between;
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            padding: 7px 10px;
            font-size: 11.5px;
            color: var(--text-primary);
            cursor: pointer;
            transition: border-color 0.15s;
            gap: 6px;
        }
        .md-cs-trigger:hover { border-color: var(--accent); }
        .md-cs.open .md-cs-trigger { border-color: var(--accent); }
        .md-cs-trigger .trigger-text {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            min-width: 0;
            flex: 1;
        }
        .md-cs-trigger .trigger-arrow {
            font-size: 8px;
            color: var(--text-muted);
            transition: transform 0.2s ease;
            flex-shrink: 0;
        }
        .md-cs.open .trigger-arrow { transform: rotate(180deg); }
        .md-cs-dropdown {
            position: absolute;
            top: calc(100% + 4px);
            left: 0;
            right: 0;
            background: var(--bg-surface);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 4px;
            box-shadow: var(--shadow);
            opacity: 0;
            visibility: hidden;
            transform: translateY(-4px);
            transition: opacity 0.15s ease, transform 0.15s ease, visibility 0.15s ease;
            z-index: 1000;
            display: flex;
            flex-direction: column;
            gap: 2px;
            max-height: 200px;
            overflow-y: auto;
        }
        .md-cs.open .md-cs-dropdown {
            opacity: 1;
            visibility: visible;
            transform: translateY(0);
        }
        .md-cs-option {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 6px 10px;
            border-radius: 6px;
            font-size: 12px;
            color: var(--text-secondary);
            cursor: pointer;
            transition: background 0.15s ease, color 0.15s ease;
            gap: 8px;
        }
        .md-cs-option:hover {
            background: var(--bg-hover);
            color: var(--text-primary);
        }
        .md-cs-option.active {
            color: var(--text-primary);
            background: var(--bg-active);
        }
        .md-cs-option .option-text {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            min-width: 0;
            flex: 1;
        }
        .md-cs-option .check-icon {
            font-size: 10px;
            color: var(--success);
            opacity: 0;
            flex-shrink: 0;
        }
        .md-cs-option.active .check-icon { opacity: 1; }

        .md-foot {
            margin-top: 12px; padding-top: 10px;
            border-top: 1px solid var(--border-color);
            display: flex; align-items: center; justify-content: flex-end;
            gap: 8px;
        }

        /* 空态 */
        .md-empty {
            padding: 60px 20px; text-align: center;
            color: var(--text-muted); font-size: 12px; line-height: 2.1;
        }
        .md-empty i { font-size: 30px; display: block; margin-bottom: 12px; opacity: 0.5; }

        /* ===== 模型编辑弹窗 ===== */
        .med-overlay {
            position: fixed;
            inset: 0;
            z-index: 2000;
            display: none;
            align-items: center;
            justify-content: center;
            background: rgba(0, 0, 0, 0.35);
            backdrop-filter: blur(4px);
            -webkit-backdrop-filter: blur(4px);
        }
        .med-overlay.open { display: flex; }
        .med-dialog {
            width: min(480px, calc(100vw - 64px));
            max-height: calc(100vh - 80px);
            overflow-y: auto;
            background: var(--bg-surface);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            box-shadow: var(--shadow);
            animation: med-pop 0.18s ease;
        }
        @keyframes med-pop {
            from { transform: scale(0.96) translateY(6px); opacity: 0; }
        }
        .med-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 13px 16px 10px;
            border-bottom: 1px solid var(--border-color);
        }
        .med-head h4 {
            font-size: 12.5px;
            font-weight: 650;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 7px;
            margin: 0;
        }
        .med-head h4 i { color: var(--text-muted); font-size: 11.5px; }
        .med-close {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 12px;
            padding: 4px 7px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .med-close:hover { background: var(--bg-hover); color: var(--text-primary); }
        .med-body {
            display: flex;
            flex-direction: column;
            gap: 13px;
            padding: 14px 16px 4px;
        }
        .med-field {
            display: flex;
            flex-direction: column;
            gap: 5px;
        }
        .med-field > label {
            font-size: 11px;
            font-weight: 500;
            color: var(--text-muted);
            letter-spacing: 0.3px;
        }
        .med-field > label .hint {
            color: var(--text-muted);
            font-weight: 400;
            opacity: 0.75;
            margin-left: 5px;
        }
        .med-field input {
            width: 100%;
            box-sizing: border-box;
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            padding: 7px 9px;
            font-size: 12px;
            color: var(--text-primary);
            outline: none;
            font-family: inherit;
            transition: border-color 0.15s, box-shadow 0.15s;
        }
        .med-field input:focus {
            border-color: var(--accent);
            box-shadow: 0 0 0 3px rgba(124, 108, 240, 0.15);
        }
        .med-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
        }
        .med-seg {
            display: flex;
            padding: 3px;
            gap: 3px;
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 7px;
        }
        .med-seg button {
            flex: 1;
            border: none;
            cursor: pointer;
            background: transparent;
            color: var(--text-secondary);
            font-size: 11.5px;
            font-weight: 500;
            font-family: inherit;
            padding: 6px 8px;
            border-radius: 5px;
            transition: all 0.15s ease;
        }
        .med-seg button:disabled { opacity: 0.4; cursor: not-allowed; }
        .med-seg button.on {
            background: var(--accent);
            color: var(--white, #fff);
        }
        .med-key-wrap { position: relative; }
        .med-key-wrap input { padding-right: 52px; }
        .med-key-toggle {
            position: absolute;
            right: 8px;
            top: 50%;
            transform: translateY(-50%);
            border: none;
            background: transparent;
            cursor: pointer;
            font-size: 10.5px;
            color: var(--text-muted);
            font-family: inherit;
        }
        .med-key-toggle:hover { color: var(--accent); }
        .med-baseurl-note {
            display: flex;
            align-items: center;
            gap: 7px;
            font-size: 10.5px;
            color: var(--text-muted);
            background: var(--bg-hover);
            border: 1px dashed var(--border-color);
            border-radius: 7px;
            padding: 7px 9px;
            line-height: 1.5;
        }
        .med-baseurl-note .mono {
            font-size: 10px;
            color: var(--text-secondary);
            word-break: break-all;
        }
        .med-foot {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 8px;
            padding: 13px 16px 15px;
        }
        .med-foot .med-del { margin-right: auto; }
        .md-btn.err {
            background: var(--error);
            border-color: var(--error);
            color: var(--white, #fff);
        }
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

    // ===== 渲染 =====

    function render() {
        const view = ensureView();
        const defaultName = defaultIndex >= 0 && models[defaultIndex] ? models[defaultIndex].name : '';
        const cards = models.map((m, i) => renderModelCard(m, i)).join('');
        const addCard = `
            <div class="md-card add-card" data-act="addModel" title="添加模型">
                <span class="plus"><i class="fas fa-plus"></i></span>
                <p>添加模型</p>
            </div>`;
        view.innerHTML = `
            <div class="md-inner">
                <div class="md-head">
                    <span class="md-icon-lg"><i class="fas fa-robot"></i></span>
                    <span class="md-title">模型管理</span>
                    <span class="md-stat">${models.length} 个模型${defaultName ? ' · 默认：' + esc(defaultName) : ''}</span>
                </div>
                <div class="md-desc">模型配置全局生效，所有智能体共享。点击卡片打开编辑弹窗，「保存」后自动保存并重载智能体配置；API Key 掩码展示，不会覆盖原值。</div>
                <div class="md-grid">${cards}${addCard}</div>
            </div>
        `;
    }

    /** 预览卡片：厂商标识 + 模型标识主体 + 元信息标签，悬停浮现操作蒙层 */
    function renderModelCard(m, i) {
        const preset = MODEL_PRESETS[m.provider] || {};
        const providerLabel = preset.label || m.provider || '未知厂商';
        const dotClass = PROVIDER_DOT_CLASS[m.provider] || 'md-dot-custom';
        const dotLetter = esc((providerLabel.trim()[0] || '?').toUpperCase());
        const typeLabel = (MODEL_TYPES.find(t => t.id === m.type) || { label: m.type }).label;
        const keyTag = m.apiKey
            ? `<span class="md-tag">API Key ${esc(m.apiKey)}</span>`
            : '<span class="md-tag warn">API Key 未配置</span>';
        const defaultBadge = i === defaultIndex
            ? '<span class="md-badge-default"><i class="fas fa-star"></i> 默认</span>' : '';
        const setDefaultBtn = i === defaultIndex ? ''
            : `<button class="md-btn md-btn-ghost" data-idx="${i}" data-act="setDefault">设为默认</button>`;
        return `
            <div class="md-card ${i === defaultIndex ? 'is-default' : ''}" data-idx="${i}" data-act="editModel" title="点击编辑">
                <div class="md-card-head">
                    <span class="md-provider"><span class="dot ${dotClass}">${dotLetter}</span>${esc(providerLabel)}</span>
                    ${defaultBadge}
                </div>
                <div class="md-model-id">${esc(m.model) || '（未设置模型）'}</div>
                <div class="md-name-sub">${esc(m.name) || '（未命名）'}</div>
                <div class="md-meta">
                    <span class="md-tag">${esc(typeLabel)}</span>
                    ${keyTag}
                </div>
                <div class="md-overlay-actions">
                    <button class="md-btn md-btn-primary" data-idx="${i}" data-act="editModel">编辑</button>
                    ${setDefaultBtn}
                    <button class="md-btn md-btn-ghost" data-idx="${i}" data-act="delModel">删除</button>
                </div>
            </div>`;
    }

    // 模型预览卡片：厂商标识 → 彩色徽标与渐变色
    const PROVIDER_DOT_CLASS = {
        dashscope: 'md-dot-dashscope',
        deepseek: 'md-dot-deepseek',
        xiaomi: 'md-dot-xiaomi',
        openai: 'md-dot-openai'
    };

    /** 编辑弹窗：草稿承载改动，「取消」丢弃、「保存」写回并即时保存 */
    function renderEditDialog() {
        let overlay = document.getElementById('mdEditOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.className = 'med-overlay';
            overlay.id = 'mdEditOverlay';
            document.body.appendChild(overlay);
            bindDialogEvents(overlay);
        }
        if (dialogIndex === null || !draft) { overlay.classList.remove('open'); return; }
        const d = draft;
        const providerIds = Object.keys(MODEL_PRESETS);
        // 配置中出现预设外的厂商标识时附加为选项，避免回显丢失
        const pids = providerIds.includes(d.provider) ? providerIds : providerIds.concat(d.provider);
        const preset = MODEL_PRESETS[d.provider] || {};
        const allowedTypes = preset.types || null;
        const modelOpts = (preset.models || []).map(v => `<option value="${escAttr(v)}"></option>`).join('');
        overlay.innerHTML = `
            <div class="med-dialog">
                <div class="med-head">
                    <h4><i class="fas fa-${dialogIndex < 0 ? 'plus' : 'pen'}"></i> ${dialogIndex < 0 ? '添加模型' : '编辑模型'}</h4>
                    <button class="med-close" title="关闭" data-act="closeDialog"><i class="fas fa-times"></i></button>
                </div>
                <div class="med-body">
                    <div class="med-row">
                        <div class="med-field">
                            <label>名称</label>
                            <input value="${escAttr(d.name)}" placeholder="自定义名称" data-field="name">
                        </div>
                        <div class="med-field">
                            <label>服务类型</label>
                            <div class="med-seg">
                                ${MODEL_TYPES.map(t => {
                                    const disabled = allowedTypes && !allowedTypes.includes(t.id);
                                    return `<button type="button" class="${d.type === t.id ? 'on' : ''}" ${disabled ? 'disabled' : ''} data-type="${t.id}">${esc(t.label)}</button>`;
                                }).join('')}
                            </div>
                        </div>
                    </div>
                    <div class="med-row">
                        <div class="med-field">
                            <label>厂商</label>
                            <div class="md-cs" data-field="provider">
                                <div class="md-cs-trigger">
                                    <span class="trigger-text">${esc(preset.label || d.provider)}</span>
                                    <i class="fas fa-chevron-down trigger-arrow"></i>
                                </div>
                                <div class="md-cs-dropdown">
                                    ${pids.map(pid => `
                                        <div class="md-cs-option${d.provider === pid ? ' active' : ''}" data-value="${escAttr(pid)}">
                                            <span class="option-text">${esc((MODEL_PRESETS[pid] || {}).label || pid)}</span>
                                            <i class="fas fa-check check-icon"></i>
                                        </div>
                                    `).join('')}
                                </div>
                            </div>
                        </div>
                        <div class="med-field">
                            <label>模型标识</label>
                            <input list="mdDlgModelOpts" value="${escAttr(d.model)}" placeholder="选择或输入模型标识" data-field="model">
                            <datalist id="mdDlgModelOpts">${modelOpts}</datalist>
                        </div>
                    </div>
                    <div class="med-field">
                        <label>API Key<span class="hint">含 **** 时保持原值不变</span></label>
                        <div class="med-key-wrap">
                            <input id="mdDlgKeyInput" type="${dlgKeyVisible ? 'text' : 'password'}" value="${escAttr(d.apiKey)}" placeholder="输入 API Key" data-field="apiKey">
                            <button type="button" class="med-key-toggle" data-act="toggleKey">${dlgKeyVisible ? '隐藏' : '显示'}</button>
                        </div>
                    </div>
                    <div class="med-baseurl-note">
                        <i class="fas fa-cog"></i> baseUrl 由系统按厂商自动管理
                        ${d.baseUrl ? `<span class="mono">${esc(d.baseUrl)}</span>` : ''}
                    </div>
                </div>
                <div class="med-foot">
                    ${dialogIndex >= 0 ? '<button class="md-btn md-btn-ghost med-del" data-act="delInDialog">删除</button>' : ''}
                    <button class="md-btn md-btn-ghost" data-act="closeDialog">取消</button>
                    <button class="md-btn md-btn-primary" id="mdDlgSaveBtn" data-act="saveDialog">保存</button>
                </div>
            </div>`;
        overlay.classList.add('open');
    }

    function closeDialog() {
        dialogIndex = null;
        draft = null;
        dlgKeyVisible = false;
        renderEditDialog();
    }

    // ===== 事件（委托绑定一次，重渲染后依然有效） =====

    function bindEvents(view) {
        view.addEventListener('click', async (e) => {
            const el = e.target.closest('[data-act]');
            if (!el) return;
            const idx = el.dataset.idx !== undefined ? Number(el.dataset.idx) : null;
            switch (el.dataset.act) {
                case 'addModel': {
                    dialogIndex = -1;
                    draft = {
                        name: '', provider: 'dashscope', type: 'pay-as-you-go',
                        model: MODEL_PRESETS.dashscope.models[0],
                        baseUrl: '', apiKey: ''
                    };
                    defaultSnapshot = defaultIndex;
                    dlgKeyVisible = false;
                    renderEditDialog();
                    break;
                }
                case 'editModel': {
                    if (idx === null || !models[idx]) return;
                    dialogIndex = idx;
                    draft = { ...models[idx] };
                    defaultSnapshot = defaultIndex;
                    dlgKeyVisible = false;
                    renderEditDialog();
                    break;
                }
                case 'setDefault': {
                    // 设为默认即时生效并保存
                    if (idx === null || !models[idx]) return;
                    defaultIndex = idx;
                    render();
                    await save();
                    break;
                }
                case 'delModel': {
                    if (idx === null || !models[idx]) return;
                    const m = models[idx];
                    const name = m.name ? `"${m.name}"` : '该模型';
                    if (!confirm(`确定要删除模型 ${name} 吗？`)) return;
                    models.splice(idx, 1);
                    if (defaultIndex === idx) defaultIndex = models.length > 0 ? 0 : -1;
                    else if (defaultIndex > idx) defaultIndex--;
                    render();
                    await save();
                    break;
                }
            }
        });
    }

    /** 编辑弹窗事件（overlay 只创建一次，内容重渲染后委托仍有效） */
    function bindDialogEvents(overlay) {
        // 弹窗内文本输入只写 draft 不重渲染（避免失焦）
        overlay.addEventListener('input', (e) => {
            const el = e.target;
            if (!draft || !el.dataset.field) return;
            draft[el.dataset.field] = el.value;
        });

        overlay.addEventListener('click', async (e) => {
            // 点击遮罩空白处关闭（丢弃草稿）
            if (e.target === overlay) { closeDialog(); return; }
            // 自定义下拉：切换展开/收起
            const trigger = e.target.closest('.md-cs-trigger');
            if (trigger) {
                const cs = trigger.closest('.md-cs');
                overlay.querySelectorAll('.md-cs.open').forEach(el => {
                    if (el !== cs) el.classList.remove('open');
                });
                cs.classList.toggle('open');
                return;
            }
            // 自定义下拉：选择厂商（模型回显首个常用模型，类型越界时归一化）
            const option = e.target.closest('.md-cs-option');
            if (option) {
                if (!draft) return;
                const value = option.dataset.value;
                draft.provider = value;
                const p = MODEL_PRESETS[value];
                if (p && p.models.length) draft.model = p.models[0];
                if (p && p.types && !p.types.includes(draft.type)) draft.type = p.types[0];
                renderEditDialog();
                return;
            }
            // 服务类型分段选择
            const segBtn = e.target.closest('.med-seg button');
            if (segBtn) {
                if (!draft || segBtn.disabled) return;
                draft.type = segBtn.dataset.type;
                renderEditDialog();
                return;
            }
            const act = e.target.closest('[data-act]');
            if (!act) return;
            switch (act.dataset.act) {
                case 'closeDialog':
                    // 取消：丢弃草稿并还原编辑期间的默认选择变更
                    defaultIndex = defaultSnapshot;
                    closeDialog();
                    render();
                    break;
                case 'toggleKey': {
                    const input = document.getElementById('mdDlgKeyInput');
                    if (!input) return;
                    dlgKeyVisible = input.type === 'password';
                    input.type = dlgKeyVisible ? 'text' : 'password';
                    act.textContent = dlgKeyVisible ? '隐藏' : '显示';
                    break;
                }
                case 'saveDialog': {
                    if (dialogIndex === null || !draft) return;
                    const btn = document.getElementById('mdDlgSaveBtn');
                    if (!String(draft.name || '').trim()) {
                        btn.classList.add('err');
                        btn.textContent = '名称不能为空';
                        setTimeout(() => { btn.classList.remove('err'); btn.textContent = '保存'; }, 1600);
                        return;
                    }
                    const committed = { ...draft };
                    if (dialogIndex >= 0) models[dialogIndex] = committed;
                    else {
                        models.push(committed);
                        if (defaultIndex < 0) defaultIndex = models.length - 1;
                    }
                    closeDialog();
                    render();
                    await save();
                    break;
                }
                case 'delInDialog': {
                    if (dialogIndex === null || dialogIndex < 0) return;
                    const m = models[dialogIndex];
                    const name = m.name ? `"${m.name}"` : '该模型';
                    if (!confirm(`确定要删除模型 ${name} 吗？`)) return;
                    models.splice(dialogIndex, 1);
                    if (defaultIndex === dialogIndex) defaultIndex = models.length > 0 ? 0 : -1;
                    else if (defaultIndex > dialogIndex) defaultIndex--;
                    defaultSnapshot = defaultIndex;
                    closeDialog();
                    render();
                    await save();
                    break;
                }
            }
        });

        // Esc 关闭弹窗（丢弃草稿）
        overlay.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                defaultIndex = defaultSnapshot;
                closeDialog();
                render();
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
        // 每次打开关闭编辑弹窗，保持卡片只读预览
        closeDialog();
        open = true;
        ensureView();
        document.querySelector('.chat-area').classList.add('model-mode');
        setMenuActive(true);
        load().then(render);
    }

    function hide() {
        if (!open) return;
        open = false;
        closeDialog(); // 切回对话视图时同步关闭编辑弹窗
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
