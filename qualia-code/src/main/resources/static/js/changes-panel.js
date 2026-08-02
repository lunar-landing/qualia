/**
 * ChangesPanel —— 工作区「审查」面板组件（自包含：样式自注入，依赖页面 CSS 变量与 .empty-steps 空态类；内部 API 仍用 Changes 命名）
 *
 * 职责：
 *   1. 以「对话轮」为组聚合变更：每轮提问一个组，组头显示提问摘要，组内按文件聚合该轮的 edit / write / delete 步骤
 *   2. 文件行：状态徽标（修改/写入/删除）+ 文件名 + 变更次数，点击展开/收起
 *   3. diff 展开层：按时间顺序渲染该轮内历次变更，edit 为红（旧文本）/ 绿（新文本）对照，
 *      write 全绿并标注写入模式（覆盖/追加/插入），delete 为红色删除标记
 *   4. 组按时间倒序排列（最新一轮在最上），无变更的轮次不产生组
 *
 * 边界：bash 等终端命令产生的文件改动无内容上下文，不纳入本面板
 *
 * 对外 API：
 *   ChangesPanel.init(containerEl)      绑定渲染容器（列表挂载点）
 *   ChangesPanel.beginGroup(label)      开启新对话轮（label 为提问文本），首个变更到达时才真正建组
 *   ChangesPanel.record(step)           记录一个步骤到当前组并增量渲染，返回是否产生新变更
 *   ChangesPanel.rebuild(groups)        按轮次整体重建，groups 为 [{label, steps}]（传空数组即清空）
 *   ChangesPanel.openFile(path)         从最新组起查找该文件，展开并滚动定位，返回是否命中
 */
window.ChangesPanel = (function () {
    'use strict';

    const TEXT_LIMIT = 4000;
    const LABEL_LIMIT = 40;

    let container = null;
    let groups = [];            // { id, seq, label, files: Map(归一化路径 → 步骤列表) }，时间正序
    let groupSeq = 0;
    let currentGroup = null;    // 当前对话轮的组（懒建）
    let pendingLabel = null;    // beginGroup 暂存的提问文本
    const expanded = new Set(); // 展开状态，key 为 `${组id}|${路径}`

    // ===== 内部工具函数 =====
    function esc(s) {
        const div = document.createElement('div');
        div.textContent = String(s == null ? '' : s);
        return div.innerHTML;
    }

    function truncate(s, n) {
        s = String(s || '');
        return s.length > n ? s.slice(0, n) + '…' : s;
    }

    function normPath(p) {
        return String(p || '').replace(/\\/g, '/').replace(/^\.\//, '');
    }

    function baseName(p) {
        const parts = p.split('/').filter(Boolean);
        return parts[parts.length - 1] || p;
    }

    // 步骤是否为可记录的文件变更，是则返回归一化路径，否则返回 null
    const CHANGE_TOOLS = ['edit', 'write', 'delete'];
    function changeKeyOf(step) {
        if (!step || step.stepType !== 'ACTION') return null;
        if (CHANGE_TOOLS.indexOf(step.toolName) === -1) return null;
        const args = step.toolArgs || {};
        const key = normPath(args.path || args.file_path);
        return key || null;
    }

    // 当前轮首个变更到达时才建组，无变更的轮次不留空组
    function ensureGroup() {
        if (currentGroup) return currentGroup;
        groupSeq++;
        currentGroup = { id: 'g' + groupSeq, seq: groupSeq, label: pendingLabel, files: new Map() };
        groups.push(currentGroup);
        return currentGroup;
    }

    function add(step) {
        const key = changeKeyOf(step);
        if (!key) return false;
        const g = ensureGroup();
        if (!g.files.has(key)) g.files.set(key, []);
        g.files.get(key).push(step);
        return true;
    }

    // ===== 渲染 =====
    const MODE_LABEL = { overwrite: '覆盖', append: '追加', insert: '插入' };

    function stepHtml(step, seq) {
        const args = step.toolArgs || {};
        if (step.toolName === 'delete') {
            return `
                <div class="cp-step">
                    <div class="cp-step-head">#${seq} 删除</div>
                    <div class="cp-diff">
                        <div class="cp-del">文件已删除</div>
                    </div>
                </div>
            `;
        }
        if (step.toolName === 'edit') {
            return `
                <div class="cp-step">
                    <div class="cp-step-head">#${seq} 修改${args.replace_all ? ' · 全部替换' : ''}</div>
                    <div class="cp-diff">
                        <div class="cp-del">${esc(truncate(args.old_text || '', TEXT_LIMIT))}</div>
                        <div class="cp-ins">${esc(truncate(args.new_text || '', TEXT_LIMIT))}</div>
                    </div>
                </div>
            `;
        }
        return `
            <div class="cp-step">
                <div class="cp-step-head">#${seq} 写入 · ${MODE_LABEL[args.mode] || '覆盖'}</div>
                <div class="cp-diff">
                    <div class="cp-ins">${esc(truncate(args.content || '', TEXT_LIMIT))}</div>
                </div>
            </div>
        `;
    }

    function fileHtml(group, key, steps) {
        // 徽标取文件终态：末步为删除则标删除，否则按是否含 edit 标修改/写入
        const BADGE_LABEL = { edit: '修改', write: '写入', delete: '删除' };
        const state = steps[steps.length - 1].toolName === 'delete'
            ? 'delete'
            : (steps.some(s => s.toolName === 'edit') ? 'edit' : 'write');
        const expandKey = group.id + '|' + key;
        return `
            <div class="cp-file${expanded.has(expandKey) ? ' open' : ''}" data-key="${esc(expandKey)}">
                <div class="cp-head" title="${esc(key)}">
                    <i class="fas fa-chevron-right cp-caret"></i>
                    <span class="cp-badge ${state}">${BADGE_LABEL[state]}</span>
                    <span class="cp-name">${esc(baseName(key))}</span>
                    <span class="cp-count">${steps.length} 次</span>
                </div>
                <div class="cp-diffs">${steps.map((s, i) => stepHtml(s, i + 1)).join('')}</div>
            </div>
        `;
    }

    function render() {
        if (!container) return;
        if (groups.length === 0) {
            container.innerHTML = `
                <div class="empty-steps">
                    <i class="fas fa-code-commit"></i>
                    <span>本会话暂无待审查的变更</span>
                </div>
            `;
            return;
        }
        // 最新一轮在最上
        let html = '';
        for (let i = groups.length - 1; i >= 0; i--) {
            const g = groups[i];
            let filesHtml = '';
            g.files.forEach((steps, key) => { filesHtml += fileHtml(g, key, steps); });
            html += `
                <div class="cp-group">
                    <div class="cp-group-head" title="${esc(g.label || '')}">
                        <span class="cp-group-seq">#${g.seq}</span>
                        <span class="cp-group-label">${esc(g.label ? truncate(g.label, LABEL_LIMIT) : '对话 ' + g.seq)}</span>
                        <span class="cp-group-count">${g.files.size} 个文件</span>
                    </div>
                    ${filesHtml}
                </div>
            `;
        }
        container.innerHTML = html;
    }

    // 点击文件行切换展开（事件委托，重渲染不丢监听）
    function onContainerClick(e) {
        const head = e.target.closest('.cp-head');
        if (!head || !container.contains(head)) return;
        const fileEl = head.parentElement;
        fileEl.classList.toggle('open');
        if (fileEl.classList.contains('open')) expanded.add(fileEl.dataset.key);
        else expanded.delete(fileEl.dataset.key);
    }

    // ===== 对外 API =====
    function init(el) {
        if (!el) return;
        container = el;
        container.classList.add('cp-list');
        container.addEventListener('click', onContainerClick);
        render();
    }

    function beginGroup(label) {
        pendingLabel = String(label || '').trim() || null;
        currentGroup = null;
    }

    function record(step) {
        if (!add(step)) return false;
        render();
        return true;
    }

    function rebuild(groupDefs) {
        groups = [];
        groupSeq = 0;
        currentGroup = null;
        pendingLabel = null;
        expanded.clear();
        (groupDefs || []).forEach(def => {
            currentGroup = null;
            pendingLabel = def && def.label != null ? String(def.label) : null;
            ((def && def.steps) || []).forEach(add);
        });
        currentGroup = null;
        pendingLabel = null;
        render();
    }

    // 工具参数路径与文件树路径可能一端带工作区前缀，做宽松匹配；从最新组往前找
    function openFile(path) {
        const norm = normPath(path);
        if (!norm || !container) return false;
        let hit = null;
        for (let i = groups.length - 1; i >= 0 && !hit; i--) {
            for (const k of groups[i].files.keys()) {
                if (k === norm || k.endsWith('/' + norm) || norm.endsWith('/' + k)) {
                    hit = groups[i].id + '|' + k;
                    break;
                }
            }
        }
        if (!hit) return false;
        expanded.add(hit);
        render();
        const el = container.querySelector(`.cp-file[data-key="${CSS.escape(hit)}"]`);
        if (el) el.scrollIntoView({ block: 'start', behavior: 'smooth' });
        return true;
    }

    // ===== 样式自注入（依赖页面已有的 CSS 变量）=====
    const STYLE = `
        /* 变更列表容器 */
        .cp-list {
            flex: 1;
            overflow-y: auto;
            padding: 11px;
            display: flex;
            flex-direction: column;
            gap: 13px;
        }
        .cp-list::-webkit-scrollbar { width: 3px; }
        .cp-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }

        /* 对话轮分组 */
        .cp-group {
            flex-shrink: 0;
            display: flex;
            flex-direction: column;
            gap: 7px;
        }
        .cp-group-head {
            display: flex;
            align-items: center;
            gap: 5px;
            padding: 0 2px;
            font-size: 10.5px;
            color: var(--text-muted);
            min-width: 0;
        }
        .cp-group-seq {
            flex-shrink: 0;
            font-family: 'JetBrains Mono', monospace;
            font-weight: 700;
        }
        .cp-group-label {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .cp-group-count { margin-left: auto; flex-shrink: 0; }

        /* 文件行 */
        .cp-file {
            flex-shrink: 0;
            border: 1px solid var(--border-color);
            border-radius: 9px;
            overflow: hidden;
        }
        .cp-head {
            display: flex;
            align-items: center;
            gap: 7px;
            padding: 8px 11px;
            font-size: 11px;
            cursor: pointer;
            user-select: none;
            min-width: 0;
        }
        .cp-head:hover { background: var(--bg-hover); }
        .cp-caret {
            flex-shrink: 0;
            font-size: 10px;
            color: var(--text-muted);
            transition: transform 0.15s;
        }
        .cp-file.open .cp-caret { transform: rotate(90deg); }
        .cp-badge {
            flex-shrink: 0;
            font-size: 9.5px;
            font-weight: 700;
            padding: 1px 6px;
            border-radius: 100px;
        }
        .cp-badge.edit { color: #f59e0b; background: rgba(245, 158, 11, 0.13); }
        .cp-badge.write { color: #34d399; background: rgba(52, 211, 153, 0.13); }
        .cp-badge.delete { color: #f87171; background: rgba(248, 113, 113, 0.13); }
        body.light-theme .cp-badge.edit { color: #b45309; background: rgba(180, 83, 9, 0.10); }
        body.light-theme .cp-badge.write { color: #059669; background: rgba(5, 150, 105, 0.10); }
        body.light-theme .cp-badge.delete { color: #b91c1c; background: rgba(185, 28, 28, 0.10); }
        .cp-name {
            font-family: 'JetBrains Mono', monospace;
            color: var(--text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .cp-count {
            margin-left: auto;
            flex-shrink: 0;
            font-size: 10.5px;
            color: var(--text-muted);
        }

        /* diff 展开层 */
        .cp-diffs {
            display: none;
            flex-direction: column;
            gap: 9px;
            padding: 9px 11px;
            border-top: 1px solid var(--border-color);
        }
        .cp-file.open .cp-diffs { display: flex; }
        .cp-step { min-width: 0; }
        .cp-step-head {
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            font-weight: 600;
            color: var(--text-muted);
            margin-bottom: 5px;
        }
        .cp-diff {
            display: flex;
            flex-direction: column;
            border: 1px solid var(--border-color);
            border-radius: 7px;
            overflow: hidden;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            line-height: 1.65;
        }
        .cp-del, .cp-ins {
            position: relative;
            padding: 7px 11px 7px 23px;
            white-space: pre-wrap;
            word-break: break-word;
            max-height: 432px;
            overflow-y: auto;
        }
        .cp-del::before, .cp-ins::before {
            position: absolute;
            left: 10px;
            font-weight: 700;
        }
        .cp-del { background: rgba(248, 113, 113, 0.08); color: #e89b9b; }
        .cp-del::before { content: '-'; color: #f87171; }
        .cp-ins { background: rgba(52, 211, 153, 0.08); color: #8fd9bb; }
        .cp-ins::before { content: '+'; color: #34d399; }
        .cp-del + .cp-ins { border-top: 1px solid var(--border-color); }
        /* 浅色主题下提高 diff 文字对比度 */
        body.light-theme .cp-del { color: #b91c1c; }
        body.light-theme .cp-ins { color: #047857; }
    `;

    function injectStyle() {
        if (document.getElementById('changes-panel-style')) return;
        const style = document.createElement('style');
        style.id = 'changes-panel-style';
        style.textContent = STYLE;
        document.head.appendChild(style);
    }
    injectStyle();

    return { init, beginGroup, record, rebuild, openFile };
})();
