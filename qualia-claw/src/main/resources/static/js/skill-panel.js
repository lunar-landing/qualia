/**
 * SkillPanel —— 技能管理视图（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 侧边栏底部「技能管理」菜单的承载模块：
 *   - 点击菜单将聊天区域整体替换为技能网格视图（一行多个卡片），再点还原对话
 *   - 技能数据来自 GET /api/config/skills；启用/禁用状态来自 GET /api/config 的 disabledSkills
 *   - 开关即时生效：PUT /api/config 仅携带 disabledSkills（后端按键合并，且自动重载所有智能体配置）
 *   - 卸载：DELETE /api/config/skills/{name}（物理删除技能目录）
 *
 * 对外 API：
 *   window.SkillPanel.toggle()   切换技能视图/对话视图
 *   window.SkillPanel.hide()     回到对话视图（会话切换/智能体切换时由 index.html 调用）
 *   window.SkillPanel.isOpen()   当前是否处于技能视图
 */
(function () {
    'use strict';

    let skills = [];
    let disabledSkills = [];
    let open = false;

    // ===== 样式注入 =====
    const CSS = `
        /* ===== 技能视图：替换整个聊天区 ===== */
        .chat-area.skills-mode > *:not(#skillsView) { display: none !important; }
        /* 技能视图下隐藏右侧预览面板（与对话无关） */
        .chat-area.skills-mode ~ .steps-panel, .chat-area.skills-mode .steps-panel { display: none !important; }

        .skills-view {
            flex: 1;
            display: none; /* 默认隐藏，skills-mode 激活时才显示，避免 hide 后残留 */
            flex-direction: column;
            overflow-y: auto;
            padding: 26px 30px 34px;
        }
        .chat-area.skills-mode #skillsView { display: flex; }
        .skills-view::-webkit-scrollbar { width: 5px; }
        .skills-view::-webkit-scrollbar-thumb {
            background: var(--scrollbar-thumb);
            border-radius: 8px;
        }

        .sv-inner { max-width: 1040px; width: 100%; margin: 0 auto; }

        .sv-head { display: flex; align-items: center; gap: 11px; margin-bottom: 5px; }
        .sv-icon {
            width: 38px; height: 38px;
            border-radius: 11px;
            background: var(--accent-gradient);
            color: var(--white, #fff);
            font-size: 15px;
            display: flex; align-items: center; justify-content: center;
            box-shadow: var(--shadow-input);
        }
        .sv-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
        .sv-stat {
            font-size: 11px; font-weight: 600;
            color: var(--text-secondary);
            background: var(--bg-hover);
            border-radius: 100px; padding: 3px 10px;
            margin-left: auto;
        }
        .sv-desc {
            font-size: 11.5px; color: var(--text-muted);
            margin: 2px 0 20px 49px;
            line-height: 1.7;
        }
        .sv-desc code {
            font-family: 'JetBrains Mono', Consolas, monospace;
            color: var(--text-secondary);
            background: var(--bg-hover);
            border-radius: 5px; padding: 1px 6px;
            font-size: 10.5px;
        }

        /* 技能网格：一行多个，自适应列数 */
        .sk-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 12px;
        }

        /* 技能卡片（竖向布局） */
        .sk-card {
            display: flex;
            flex-direction: column;
            border: 1px solid var(--border-color);
            background: var(--bg-input);
            border-radius: var(--radius-sm);
            padding: 16px;
            transition: all 0.18s;
        }
        .sk-card:hover { border-color: var(--border-active); transform: translateY(-1px); }
        .sk-card.disabled { opacity: 0.55; }
        .sk-card.disabled:hover { opacity: 0.75; }

        .sk-top { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
        .sk-icon {
            width: 34px; height: 34px; flex-shrink: 0;
            border-radius: 10px;
            background: var(--accent-gradient);
            color: var(--white, #fff);
            font-size: 14px;
            display: flex; align-items: center; justify-content: center;
        }
        .sk-card.disabled .sk-icon { background: var(--bg-hover); color: var(--text-muted); }
        .sk-name {
            font-size: 13px; font-weight: 650;
            color: var(--text-primary);
            flex: 1; min-width: 0;
            overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        }
        .sk-src {
            font-size: 10px; font-weight: 650;
            color: var(--accent-light);
            background: var(--bg-active);
            border-radius: 5px; padding: 2px 7px;
            flex-shrink: 0;
        }

        .sk-desc {
            flex: 1;
            font-size: 11.5px; color: var(--text-secondary); line-height: 1.7;
            display: -webkit-box;
            -webkit-line-clamp: 3;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        .sk-meta { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 5px; }
        .sk-meta span {
            font-size: 10px; color: var(--text-muted);
            font-family: 'JetBrains Mono', Consolas, monospace;
            background: var(--bg-hover);
            border-radius: 6px; padding: 2px 8px;
            max-width: 100%;
            overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        }

        .sk-foot {
            margin-top: 12px; padding-top: 10px;
            border-top: 1px solid var(--border-color);
            display: flex; align-items: center;
        }
        .sk-del {
            display: inline-flex; align-items: center; gap: 6px;
            background: none; border: none;
            color: var(--text-muted); font-size: 11.5px;
            padding: 4px 8px; border-radius: 6px;
            transition: all 0.15s;
            opacity: 0;
            cursor: pointer;
        }
        .sk-card:hover .sk-del { opacity: 1; }
        .sk-del:hover { color: var(--error); background: rgba(248, 113, 113, 0.09); }

        /* 启用开关：与设置弹窗同款；作用域限定在 #skillsView，避免依赖 settings.js 的样式注入 */
        #skillsView .sk-foot .toggle-switch {
            margin-left: auto;
            position: relative;
            display: inline-block;
            width: 40px;
            height: 22px;
            flex-shrink: 0;
        }
        #skillsView .toggle-switch input { opacity: 0; width: 0; height: 0; }
        #skillsView .toggle-slider {
            position: absolute;
            cursor: pointer;
            inset: 0;
            background-color: var(--text-muted);
            transition: background-color 0.2s;
            border-radius: 4px;
        }
        #skillsView .toggle-slider:before {
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
        #skillsView .toggle-switch input:checked + .toggle-slider { background-color: var(--accent); }
        #skillsView .toggle-switch input:checked + .toggle-slider:before { transform: translateX(18px); }

        /* 空态 */
        .sv-empty {
            padding: 60px 20px; text-align: center;
            color: var(--text-muted); font-size: 12px; line-height: 2.1;
        }
        .sv-empty i { font-size: 30px; display: block; margin-bottom: 12px; opacity: 0.5; }
    `;

    function injectStyle() {
        if (document.getElementById('skill-panel-style')) return;
        const style = document.createElement('style');
        style.id = 'skill-panel-style';
        style.textContent = CSS;
        document.head.appendChild(style);
    }

    function esc(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    function escAttr(s) {
        return esc(s).replace(/'/g, '&#39;').replace(/"/g, '&quot;');
    }

    // ===== DOM =====

    function ensureView() {
        let view = document.getElementById('skillsView');
        if (view) return view;
        view = document.createElement('div');
        view.className = 'skills-view';
        view.id = 'skillsView';
        document.querySelector('.chat-area').appendChild(view);
        return view;
    }

    // ===== 数据 =====

    async function load() {
        try {
            const sres = await fetch('/api/config/skills');
            skills = sres.ok ? (await sres.json()) : [];
        } catch (e) {
            skills = [];
        }
        try {
            const cres = await fetch('/api/config');
            const config = cres.ok ? (await cres.json()) : {};
            disabledSkills = Array.isArray(config.disabledSkills) ? config.disabledSkills : [];
        } catch (e) {
            disabledSkills = [];
        }
    }

    function isEnabled(skill) {
        return skill.enabled !== false && !disabledSkills.includes(skill.name);
    }

    // ===== 渲染 =====

    function render() {
        const view = ensureView();
        const enabledCount = skills.filter(isEnabled).length;
        const cards = skills.map(s => {
            const enabled = isEnabled(s);
            const metas = []
                .concat(s.scripts || [])
                .concat(s.references || [])
                .filter(Boolean)
                .map(x => `<span>${esc(x)}</span>`)
                .join('');
            return `
            <div class="sk-card ${enabled ? '' : 'disabled'}">
                <div class="sk-top">
                    <span class="sk-icon"><i class="fas fa-shapes"></i></span>
                    <span class="sk-name" title="${esc(s.name)}">${esc(s.name)}</span>
                    <span class="sk-src">全局</span>
                </div>
                <div class="sk-desc">${esc(s.description) || '（无描述）'}</div>
                ${metas ? `<div class="sk-meta">${metas}</div>` : ''}
                <div class="sk-foot">
                    <button class="sk-del" title="卸载技能" onclick="SkillPanel.uninstall('${escAttr(s.name)}')">
                        <i class="far fa-trash-alt"></i> 卸载
                    </button>
                    <label class="toggle-switch" title="${enabled ? '点击禁用' : '点击启用'}">
                        <input type="checkbox" ${enabled ? 'checked' : ''} onchange="SkillPanel.toggleSkill('${escAttr(s.name)}', this.checked)">
                        <span class="toggle-slider"></span>
                    </label>
                </div>
            </div>`;
        }).join('');

        view.innerHTML = `
            <div class="sv-inner">
                <div class="sv-head">
                    <span class="sv-icon"><i class="fas fa-shapes"></i></span>
                    <span class="sv-title">技能管理</span>
                    <span class="sv-stat">${enabledCount} 已启用 / 共 ${skills.length}</span>
                </div>
                <div class="sv-desc">技能全局生效，所有智能体共享。技能目录 <code>~/.qualia/claw/skills</code></div>
                ${skills.length
                    ? `<div class="sk-grid">${cards}</div>`
                    : '<div class="sv-empty"><i class="fas fa-shapes"></i>暂无技能<br/>在技能目录下创建技能文件夹后刷新页面</div>'}
            </div>
        `;
    }

    // ===== 视图切换 =====

    function setMenuActive(active) {
        const btn = document.getElementById('skillsMenuBtn');
        if (btn) btn.classList.toggle('active', active);
    }

    function show() {
        injectStyle();
        if (window.McpPanel) window.McpPanel.hide(); // 与 MCP 视图互斥
        open = true;
        ensureView();
        document.querySelector('.chat-area').classList.add('skills-mode');
        setMenuActive(true);
        load().then(render);
    }

    function hide() {
        if (!open) return;
        open = false;
        document.querySelector('.chat-area').classList.remove('skills-mode');
        setMenuActive(false);
    }

    function toggle() {
        if (open) hide();
        else show();
    }

    // ===== 操作 =====

    /** 启用/禁用：仅提交 disabledSkills，后端按键合并不影响模型密钥 */
    async function toggleSkill(name, enabled) {
        const next = enabled
            ? disabledSkills.filter(n => n !== name)
            : (disabledSkills.includes(name) ? disabledSkills : [...disabledSkills, name]);
        try {
            const res = await fetch('/api/config', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ disabledSkills: next })
            });
            const result = await res.json().catch(() => ({}));
            if (res.ok && result.success !== false) {
                disabledSkills = next;
                render();
            } else {
                alert(result.error || result.message || '操作失败');
                load().then(render);
            }
        } catch (e) {
            alert('请求失败: ' + e.message);
            load().then(render);
        }
    }

    async function uninstall(name) {
        if (!confirm(`确定要卸载技能 "${name}" 吗？\n\n此操作将删除技能目录，不可恢复。`)) return;
        try {
            const res = await fetch(`/api/config/skills/${encodeURIComponent(name)}`, { method: 'DELETE' });
            const result = await res.json().catch(() => ({}));
            if (!res.ok || result.success === false) {
                alert(result.error || result.message || '卸载失败');
            }
            await load();
            render();
        } catch (e) {
            alert('请求失败: ' + e.message);
        }
    }

    window.SkillPanel = {
        toggle,
        show,
        hide,
        isOpen: () => open,
        toggleSkill,
        uninstall
    };
})();
