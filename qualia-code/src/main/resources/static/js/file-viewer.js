/**
 * FileViewer —— 工作区「文件」面板组件（自包含：样式自注入，依赖页面 CSS 变量与 index.css 的 .tree 系列类）
 *
 * 布局：左侧文件树 + 可拖拽分隔条 + 右侧文件预览（左树右预览）
 *
 * 职责：
 *   1. 文件树：加载 / 渲染 / 目录懒加载展开。沿用 .tree / .tree-item / .node 结构与
 *      data-path、data-is-dir 属性，保持 index.html 的活动徽标（factivity）逻辑与 index.css 样式兼容
 *   2. 文件预览：点击文件节点请求 /api/config/file，右侧按类型渲染：
 *      - text    行号列 + hljs 语法高亮（按扩展名推断语言），超长内容显示截断提示
 *      - image   经 /api/config/file/raw 以 <img> 展示
 *      - binary  提示不支持预览
 *   3. 分隔条拖拽调整树列宽度（126px ~ 面板 60%）
 *
 * 边界：徽标（.factivity）点击不在此处理，仍由 index.html 跳转审查面板
 *
 * 对外 API：
 *   FileViewer.init(containerEl, opts)  挂载渲染并加载文件树；opts.onOpen(path) 每次打开预览时回调（用于加宽面板）
 *   FileViewer.loadTree()               重新加载文件树（刷新按钮）
 *   FileViewer.open(path)               打开指定文件预览
 *   FileViewer.close()                  关闭预览回到空态
 */
window.FileViewer = (function () {
    'use strict';

    // 扩展名 → hljs 语言（未命中或 hljs 未注册时按纯文本渲染）
    const LANG_MAP = {
        js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'javascript',
        ts: 'typescript', tsx: 'typescript',
        java: 'java', py: 'python', go: 'go', rs: 'rust', rb: 'ruby', php: 'php',
        c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', hpp: 'cpp', cs: 'csharp', kt: 'kotlin', swift: 'swift',
        html: 'xml', htm: 'xml', xml: 'xml', vue: 'xml', svg: 'xml',
        css: 'css', scss: 'scss', less: 'less',
        json: 'json', md: 'markdown', yml: 'yaml', yaml: 'yaml',
        sh: 'bash', bash: 'bash', bat: 'dos', cmd: 'dos', ps1: 'powershell',
        sql: 'sql', gradle: 'groovy', properties: 'properties', ini: 'ini', toml: 'ini'
    };

    const FILE_ICONS = {
        js: 'fa-file-code', ts: 'fa-file-code', jsx: 'fa-file-code', tsx: 'fa-file-code',
        html: 'fa-file-code', css: 'fa-file-code', scss: 'fa-file-code',
        json: 'fa-file-code', xml: 'fa-file-code', yml: 'fa-file-code', yaml: 'fa-file-code',
        md: 'fa-file-alt', txt: 'fa-file-alt',
        java: 'fa-file-code', py: 'fa-file-code', go: 'fa-file-code', rs: 'fa-file-code',
        gradle: 'fa-file-code', properties: 'fa-cog',
        png: 'fa-file-image', jpg: 'fa-file-image', jpeg: 'fa-file-image', gif: 'fa-file-image', svg: 'fa-file-image'
    };

    let root = null;
    let treeEl = null;
    let leftEl = null;
    let viewEl = null;
    let emptyEl = null;
    let headNameEl = null;
    let headMetaEl = null;
    let headIconEl = null;
    let bodyEl = null;
    let opts = {};
    let currentPath = null;

    // ===== 内部工具函数 =====
    function esc(s) {
        const div = document.createElement('div');
        div.textContent = String(s == null ? '' : s);
        return div.innerHTML;
    }

    function extOf(name) {
        const dot = String(name || '').lastIndexOf('.');
        return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
    }

    function fileIcon(name) {
        return FILE_ICONS[extOf(name)] || 'fa-file-alt';
    }

    function fmtSize(n) {
        if (n == null) return '';
        if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
        if (n >= 1024) return (n / 1024).toFixed(1) + ' KB';
        return n + ' B';
    }

    // ===== 文件树 =====
    function nodeHtml(file, depth) {
        const isDir = !!file.isDirectory;
        const icon = isDir ? 'fa-folder' : fileIcon(file.name);
        return `
            <div class="tree-item" data-path="${esc(file.path)}" data-is-dir="${isDir}" data-depth="${depth}">
                <div class="node" style="padding-left:${depth * 18 + 7}px">
                    <i class="fas ${icon}"></i>
                    <span title="${esc(file.name)}">${esc(file.name)}</span>
                </div>
                ${isDir ? '<div class="tree-children"></div>' : ''}
            </div>
        `;
    }

    function listHtml(files, depth) {
        if (!Array.isArray(files)) return '';
        if (files.length === 1 && files[0].error) {
            return `<div class="fv-tree-err">${esc(files[0].error)}</div>`;
        }
        return files.map(f => nodeHtml(f, depth)).join('');
    }

    async function fetchList(path) {
        const res = await fetch(`/api/config/files?path=${encodeURIComponent(path || '')}`);
        return res.json();
    }

    async function loadTree() {
        if (!treeEl) return;
        try {
            treeEl.innerHTML = listHtml(await fetchList(''), 0);
            markActive(currentPath);
        } catch (e) {
            treeEl.innerHTML = '<div class="fv-tree-err">加载文件树失败</div>';
        }
    }

    async function toggleDir(item, node) {
        const children = item.querySelector(':scope > .tree-children');
        if (!children) return;
        const icon = node.querySelector('i');
        if (children.classList.contains('open')) {
            children.classList.remove('open');
            if (icon) icon.className = 'fas fa-folder';
            return;
        }
        children.classList.add('open');
        if (icon) icon.className = 'fas fa-folder-open';
        // 懒加载子目录
        if (children.children.length === 0) {
            const depth = (parseInt(item.dataset.depth, 10) || 0) + 1;
            try {
                children.innerHTML = listHtml(await fetchList(item.dataset.path), depth);
                markActive(currentPath);
            } catch (e) {
                children.innerHTML = '<div class="fv-tree-err">加载失败</div>';
            }
        }
    }

    function onTreeClick(e) {
        // 徽标点击由 index.html 处理（跳转审查面板），此处放行
        if (e.target.closest('.factivity')) return;
        const node = e.target.closest('.node');
        if (!node || !treeEl.contains(node)) return;
        const item = node.closest('.tree-item');
        if (!item) return;
        if (item.dataset.isDir === 'true') {
            toggleDir(item, node);
        } else {
            open(item.dataset.path);
        }
    }

    // 选中态高亮：清除旧的，标记当前预览文件
    function markActive(path) {
        if (!treeEl) return;
        treeEl.querySelectorAll('.node.fv-active').forEach(n => n.classList.remove('fv-active'));
        if (!path) return;
        const item = treeEl.querySelector(`.tree-item[data-is-dir="false"][data-path="${CSS.escape(path)}"]`);
        if (item) item.querySelector('.node').classList.add('fv-active');
    }

    // ===== 文件预览 =====
    function showView(show) {
        viewEl.classList.toggle('show', show);
        emptyEl.style.display = show ? 'none' : '';
    }

    function noticeHtml(icon, text) {
        return `<div class="fv-notice"><i class="fas ${icon}"></i><span>${esc(text)}</span></div>`;
    }

    function renderText(info) {
        const content = String(info.content || '').replace(/\r\n?/g, '\n');
        const lang = LANG_MAP[extOf(info.name)];
        let codeHtml;
        if (lang && window.hljs && hljs.getLanguage(lang)) {
            try {
                codeHtml = hljs.highlight(content, { language: lang }).value;
            } catch (e) {
                codeHtml = esc(content);
            }
        } else {
            codeHtml = esc(content);
        }
        const lineCount = content.split('\n').length;
        let nums = '';
        for (let i = 1; i <= lineCount; i++) nums += i + '\n';
        bodyEl.innerHTML = `
            <div class="fv-code-wrap">
                <div class="fv-gutter">${nums}</div>
                <pre class="fv-code"><code class="hljs">${codeHtml}</code></pre>
            </div>
            ${info.truncated ? '<div class="fv-truncated"><i class="fas fa-scissors"></i> 文件过大，仅显示前 512KB</div>' : ''}
        `;
    }

    function renderInfo(info) {
        headIconEl.className = 'fas ' + fileIcon(info.name);
        headNameEl.textContent = info.name || '';
        headNameEl.title = info.path || '';
        headMetaEl.textContent = fmtSize(info.size);
        if (info.type === 'image') {
            bodyEl.innerHTML = `<div class="fv-imgwrap"><img src="/api/config/file/raw?path=${encodeURIComponent(info.path)}" alt="${esc(info.name)}"></div>`;
        } else if (info.type === 'binary') {
            bodyEl.innerHTML = noticeHtml('fa-file-circle-question', '二进制文件，暂不支持预览');
        } else {
            renderText(info);
        }
    }

    async function open(path) {
        if (!root || !path) return;
        currentPath = path;
        markActive(path);
        showView(true);
        headIconEl.className = 'fas ' + fileIcon(path);
        headNameEl.textContent = path.split('/').pop();
        headNameEl.title = path;
        headMetaEl.textContent = '';
        bodyEl.innerHTML = noticeHtml('fa-circle-notch fa-spin', '加载中…');
        if (typeof opts.onOpen === 'function') opts.onOpen(path);
        try {
            const res = await fetch(`/api/config/file?path=${encodeURIComponent(path)}`);
            const info = await res.json();
            if (currentPath !== path) return; // 过期响应丢弃
            if (info.error) {
                bodyEl.innerHTML = noticeHtml('fa-triangle-exclamation', info.error);
            } else {
                renderInfo(info);
            }
        } catch (e) {
            if (currentPath === path) bodyEl.innerHTML = noticeHtml('fa-triangle-exclamation', '加载失败');
        }
    }

    function close() {
        currentPath = null;
        markActive(null);
        showView(false);
        bodyEl.innerHTML = '';
    }

    // ===== 分隔条拖拽：调整树列宽度 =====
    function initDivider(divider) {
        divider.addEventListener('mousedown', (e) => {
            e.preventDefault();
            const startX = e.clientX;
            const startW = leftEl.getBoundingClientRect().width;
            divider.classList.add('dragging');
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
            const onMove = (ev) => {
                const max = Math.max(126, root.getBoundingClientRect().width * 0.6);
                const w = Math.min(max, Math.max(126, startW + ev.clientX - startX));
                leftEl.style.width = w + 'px';
            };
            const onUp = () => {
                document.removeEventListener('mousemove', onMove);
                document.removeEventListener('mouseup', onUp);
                divider.classList.remove('dragging');
                document.body.style.cursor = '';
                document.body.style.userSelect = '';
            };
            document.addEventListener('mousemove', onMove);
            document.addEventListener('mouseup', onUp);
        });
    }

    // ===== 挂载 =====
    function init(el, options) {
        if (!el) return;
        opts = options || {};
        injectStyle();
        root = el;
        root.classList.add('fv-root');
        root.innerHTML = `
            <div class="fv-left">
                <div class="tree" id="fileTree"></div>
            </div>
            <div class="fv-divider"></div>
            <div class="fv-right">
                <div class="fv-empty">
                    <i class="far fa-file-code"></i>
                    <span>点击左侧文件预览内容</span>
                </div>
                <div class="fv-view">
                    <div class="fv-head">
                        <i class="fas fa-file-alt"></i>
                        <span class="fv-name"></span>
                        <span class="fv-meta"></span>
                        <button class="fv-close" title="关闭预览"><i class="fas fa-times"></i></button>
                    </div>
                    <div class="fv-body"></div>
                </div>
            </div>
        `;
        leftEl = root.querySelector('.fv-left');
        treeEl = root.querySelector('#fileTree');
        viewEl = root.querySelector('.fv-view');
        emptyEl = root.querySelector('.fv-empty');
        headIconEl = root.querySelector('.fv-head > i');
        headNameEl = root.querySelector('.fv-name');
        headMetaEl = root.querySelector('.fv-meta');
        bodyEl = root.querySelector('.fv-body');

        treeEl.addEventListener('click', onTreeClick);
        root.querySelector('.fv-close').addEventListener('click', close);
        initDivider(root.querySelector('.fv-divider'));
        loadTree();
    }

    // ===== 样式（自注入，仅依赖页面级 CSS 变量）=====
    function injectStyle() {
        if (document.getElementById('fv-style')) return;
        const style = document.createElement('style');
        style.id = 'fv-style';
        style.textContent = `
        .fv-root { flex: 1; min-height: 0; min-width: 0; display: flex; }
        .fv-left { width: 198px; min-width: 126px; flex-shrink: 0; display: flex; flex-direction: column; min-height: 0; }
        .fv-divider { width: 5px; flex-shrink: 0; cursor: col-resize; border-left: 1px solid var(--border-color); transition: background 0.15s; }
        .fv-divider:hover, .fv-divider.dragging { background: var(--accent-light); }
        .fv-right { flex: 1; min-width: 0; display: flex; flex-direction: column; min-height: 0; }
        .fv-tree-err { padding: 7px; color: var(--text-muted); font-size: 11px; }
        .tree-item .node.fv-active { background: var(--bg-hover); color: var(--text-primary); }

        .fv-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: var(--text-muted); font-size: 11.5px; }
        .fv-empty i { font-size: 23px; opacity: 0.5; }

        .fv-view { flex: 1; min-height: 0; display: none; flex-direction: column; }
        .fv-view.show { display: flex; }
        .fv-head { display: flex; align-items: center; gap: 7px; padding: 7px 11px; border-bottom: 1px solid var(--border-color); flex-shrink: 0; min-width: 0; }
        .fv-head > i { color: var(--text-muted); font-size: 11.5px; flex-shrink: 0; }
        .fv-name { font-size: 11.5px; font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .fv-meta { font-size: 10.5px; color: var(--text-muted); flex-shrink: 0; }
        .fv-close { margin-left: auto; flex-shrink: 0; background: transparent; border: none; color: var(--text-muted); font-size: 11.5px; padding: 4px 6px; border-radius: 6px; cursor: pointer; transition: all 0.2s; }
        .fv-close:hover { background: var(--bg-hover); color: var(--text-primary); }

        .fv-body { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
        .fv-code-wrap { flex: 1; overflow: auto; display: flex; align-items: flex-start; font-family: 'JetBrains Mono', monospace; font-size: 11px; line-height: 1.6; }
        .fv-gutter { position: sticky; left: 0; flex-shrink: 0; padding: 9px 9px 9px 13px; text-align: right; color: var(--text-muted); opacity: 0.65; white-space: pre; user-select: none; background: var(--bg-sidebar); border-right: 1px solid var(--border-color); }
        .fv-code { margin: 0; padding: 9px 14px; }
        .fv-code code { display: block; font-family: inherit; font-size: inherit; line-height: inherit; white-space: pre; background: transparent !important; padding: 0; }
        .fv-truncated { flex-shrink: 0; padding: 5px 11px; border-top: 1px solid var(--border-color); color: var(--text-muted); font-size: 10.5px; }
        .fv-truncated i { margin-right: 4px; }

        .fv-notice { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: var(--text-muted); font-size: 11.5px; }
        .fv-notice i { font-size: 20px; opacity: 0.6; }

        .fv-imgwrap { flex: 1; overflow: auto; display: flex; align-items: center; justify-content: center; padding: 14px; }
        .fv-imgwrap img { max-width: 100%; max-height: 100%; border-radius: 7px; }
        `;
        document.head.appendChild(style);
    }

    return { init, loadTree, open, close };
})();
