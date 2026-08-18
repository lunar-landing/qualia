/**
 * workspace-files.js —— 右侧面板「工作区」页签：当前智能体工作区文件树 + 文件内容预览
 *
 * 结构依赖 index.html 中的 #stepsPanel（页签 #wsFiles）：
 *   - #wsPath        工作区路径展示
 *   - #wsTree        文件树容器（懒加载目录）
 *   - #wsFileView    文件内容预览态（返回键 + 文件名 + 元信息 + 内容）
 *
 * 对外 API：
 *   window.WorkspaceFiles.reload()  重载文件树（智能体切换 / 手动刷新）
 *   window.WorkspaceFiles.back()    从文件预览返回文件树
 *
 * 联动：agent-panel.js 切换智能体成功后由 index.html 的 onAgentSwitched 调用 reload()
 */
(function () {
    'use strict';

    // ===== 工具 =====

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function fmtSize(n) {
        if (n == null || n < 0) return '';
        if (n < 1024) return n + ' B';
        if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
        return (n / 1024 / 1024).toFixed(1) + ' MB';
    }

    function fmtTime(ms) {
        if (!ms) return '';
        const d = new Date(ms);
        const p = (x) => String(x).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    }

    // ===== 图标映射（与 index.css 着色规则一一对应） =====

    const DOC_EXTS = new Set(['md', 'txt', 'log', 'rst']);
    const CODE_EXTS = new Set(['js', 'ts', 'jsx', 'tsx', 'py', 'java', 'json', 'xml',
        'yml', 'yaml', 'css', 'html', 'htm', 'sh', 'bat', 'sql', 'properties']);
    const IMG_EXTS = new Set(['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp', 'ico']);

    function iconCls(name) {
        const dot = name.lastIndexOf('.');
        const ext = dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
        if (DOC_EXTS.has(ext)) return 'fa-file-alt';
        if (CODE_EXTS.has(ext)) return 'fa-file-code';
        if (IMG_EXTS.has(ext)) return 'fa-image';
        return 'fa-file';
    }

    // ===== 状态 =====

    /** 文件树当前归属的 agentId，异步返回后校验防止切换智能体导致的串数据 */
    let currentAgentId = '';

    function api(path) {
        return `/api/agents/${currentAgentId}/workspace${path}`;
    }

    function treeEl() {
        return document.getElementById('wsTree');
    }

    function showTreeMsg(icon, text, spin) {
        const t = treeEl();
        if (t) {
            t.innerHTML = `<div class="ws-empty"><i class="fas ${icon}${spin ? ' fa-spin' : ''}"></i><div class="ws-empty-title">${esc(text)}</div></div>`;
        }
    }

    // ===== 文件树渲染 =====

    function itemHtml(e) {
        if (e.dir) {
            return `<div class="tree-item" data-path="${esc(e.path)}" data-dir="1">
                <div class="node"><i class="fas fa-folder"></i><span>${esc(e.name)}</span></div>
                <div class="tree-children"></div>
            </div>`;
        }
        const size = (typeof e.size === 'number' && e.size >= 0)
            ? `<span class="size">${fmtSize(e.size)}</span>` : '';
        return `<div class="tree-item" data-path="${esc(e.path)}">
            <div class="node"><i class="fas ${iconCls(e.name)}"></i><span>${esc(e.name)}</span>${size}</div>
        </div>`;
    }

    function renderEntries(container, entries) {
        if (!entries.length) {
            container.innerHTML = '<div class="ws-empty-inline">（空目录）</div>';
            return;
        }
        container.innerHTML = entries.map(itemHtml).join('');
        bindItems(container);
    }

    function bindItems(container) {
        container.querySelectorAll(':scope > .tree-item').forEach(item => {
            const node = item.querySelector(':scope > .node');
            node.addEventListener('click', () => {
                if (item.dataset.dir === '1') toggleDir(item);
                else openFile(item.dataset.path);
            });
        });
    }

    /** 目录点击：懒加载子项 + 展开/收起 */
    async function toggleDir(item) {
        const children = item.querySelector(':scope > .tree-children');
        const icon = item.querySelector(':scope > .node > i');
        const agentId = currentAgentId;

        if (item.dataset.open === '1') {
            item.dataset.open = '';
            children.classList.remove('open');
            icon.className = 'fas fa-folder';
            return;
        }
        if (item.dataset.loaded !== '1') {
            children.innerHTML = '<div class="ws-empty-inline">加载中…</div>';
            children.classList.add('open'); // 先展开让加载态可见
            try {
                const res = await fetch(api(`/files?path=${encodeURIComponent(item.dataset.path)}`));
                const data = await res.json();
                if (agentId !== currentAgentId) return; // 期间已切换智能体，丢弃结果
                if (!res.ok) {
                    children.innerHTML = `<div class="ws-empty-inline">${esc(data.message || '加载失败')}</div>`;
                    return;
                }
                renderEntries(children, data.entries);
                item.dataset.loaded = '1';
            } catch (e) {
                if (agentId !== currentAgentId) return;
                children.innerHTML = '<div class="ws-empty-inline">加载失败</div>';
                return;
            }
        }
        item.dataset.open = '1';
        children.classList.add('open');
        icon.className = 'fas fa-folder-open';
    }

    // ===== 文件内容预览 =====

    async function openFile(path) {
        const agentId = currentAgentId;

        // 高亮选中行
        const tree = treeEl();
        if (tree) {
            tree.querySelectorAll('.node.sel').forEach(n => n.classList.remove('sel'));
            tree.querySelectorAll('.tree-item').forEach(it => {
                if (it.dataset.path === path) {
                    it.querySelector(':scope > .node').classList.add('sel');
                }
            });
        }

        const nameEl = document.getElementById('wsFileName');
        const metaEl = document.getElementById('wsFileMeta');
        const contentEl = document.getElementById('wsFileContent');
        showFileView();
        nameEl.textContent = path;
        metaEl.textContent = '加载中…';
        contentEl.textContent = '';

        try {
            const res = await fetch(api(`/file?path=${encodeURIComponent(path)}`));
            const data = await res.json();
            if (agentId !== currentAgentId) return;
            if (!res.ok) {
                metaEl.textContent = '';
                contentEl.textContent = data.message || '读取失败';
                return;
            }
            nameEl.textContent = data.path;
            metaEl.textContent = [fmtSize(data.size), fmtTime(data.modified)].filter(Boolean).join(' · ');
            if (data.supported) {
                contentEl.textContent = data.content || '（空文件）';
            } else {
                contentEl.textContent = data.message || '该文件暂不支持在线预览';
            }
        } catch (e) {
            if (agentId !== currentAgentId) return;
            metaEl.textContent = '';
            contentEl.textContent = '读取文件失败';
        }
    }

    function showFileView() {
        const tv = document.getElementById('wsTreeView');
        const fv = document.getElementById('wsFileView');
        if (tv) tv.style.display = 'none';
        if (fv) fv.style.display = 'flex';
    }

    function back() {
        const tv = document.getElementById('wsTreeView');
        const fv = document.getElementById('wsFileView');
        if (tv) tv.style.display = '';
        if (fv) fv.style.display = 'none';
    }

    // ===== 根加载 =====

    async function reload() {
        const agentId = window.QAgent ? window.QAgent.currentId() : '';
        currentAgentId = agentId;
        back();

        const pathEl = document.getElementById('wsPath');
        if (!agentId) {
            if (pathEl) { pathEl.textContent = ''; pathEl.title = ''; }
            showTreeMsg('fa-user-slash', '请先创建或选择智能体');
            return;
        }
        showTreeMsg('fa-circle-notch', '加载工作区…', true);
        try {
            const res = await fetch(api('/files'));
            const data = await res.json();
            if (agentId !== currentAgentId) return;
            if (!res.ok) {
                if (pathEl) { pathEl.textContent = ''; pathEl.title = ''; }
                showTreeMsg('fa-triangle-exclamation', data.message || '工作区加载失败');
                return;
            }
            if (pathEl) {
                pathEl.textContent = data.workspacePath || '';
                pathEl.title = data.workspacePath || '';
            }
            const tree = treeEl();
            if (!tree) return;
            if (!data.entries.length) {
                tree.innerHTML = '<div class="ws-empty"><i class="fas fa-folder-open"></i><div class="ws-empty-title">工作区暂无文件</div><div class="ws-empty-desc">智能体产出的文件会出现在这里</div></div>';
            } else {
                renderEntries(tree, data.entries);
            }
        } catch (e) {
            if (agentId !== currentAgentId) return;
            showTreeMsg('fa-triangle-exclamation', '工作区加载失败');
        }
    }

    window.WorkspaceFiles = { reload, back };
})();
