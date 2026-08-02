/**
 * ProjectPanel —— 工作区切换面板（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 职责：
 *   1. 侧边栏入口按钮回填当前工作区名称与路径（#wsSwitchName / #wsSwitchPath）
 *   2. 切换弹窗（双视图）：
 *      - 主视图：当前工作区 + 最近打开列表（~/.qualia/workspaces.json）+ 浏览入口
 *      - 浏览视图：全尺寸目录选择器（地址栏 + 磁盘快捷入口 + 子目录大列表）
 *   3. 地址栏输入不存在的目录时提供「创建并打开」；流式对话期间入口置灰、动作拦截
 *   4. 切换成功后回调 window.onWorkspaceSwitched() 完成整页状态刷新
 *
 * 对外 API：
 *   window.openWorkspaceSwitcher()   打开弹窗（每次打开都重新拉取最新数据，回到主视图）
 *   window.closeWorkspaceSwitcher()  关闭弹窗
 *   （内部事件处理统一挂在 window.QWorkspace 命名空间下，供生成的 DOM 引用）
 */
(function () {
    'use strict';

    // ===== 样式注入 =====
    const CSS = `
        /* ===== 工作区切换弹窗 ===== */
        .ws-sw-overlay {
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
        .ws-sw-overlay.open {
            display: flex;
            animation: wsSwFade 0.18s ease;
        }
        @keyframes wsSwFade {
            from { opacity: 0; }
            to { opacity: 1; }
        }
        .ws-sw-dialog {
            width: min(504px, calc(100vw - 48px));
            max-height: min(78vh, 648px);
            background: var(--bg-surface);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            box-shadow: var(--shadow);
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .ws-sw-overlay.open .ws-sw-dialog {
            animation: wsSwPop 0.22s cubic-bezier(0.16, 1, 0.3, 1);
        }
        @keyframes wsSwPop {
            from { opacity: 0; transform: translateY(10px) scale(0.97); }
            to { opacity: 1; transform: none; }
        }
        /* 头部：渐变图标块 + 标题（随视图切换），浏览视图下左侧出现返回按钮 */
        .ws-sw-head {
            display: flex;
            align-items: center;
            gap: 11px;
            padding: 16px 18px;
            border-bottom: 1px solid var(--border-color);
        }
        .ws-sw-head .back-btn {
            display: none;
            flex-shrink: 0;
            width: 31px;
            height: 31px;
            align-items: center;
            justify-content: center;
            border: 1px solid var(--border-color);
            border-radius: 9px;
            background: transparent;
            color: var(--text-secondary);
            font-size: 11.5px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-head .back-btn:hover {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-active);
        }
        .ws-sw-dialog.browsing .back-btn { display: flex; }
        .ws-sw-dialog.browsing .head-icon { display: none; }
        .ws-sw-head .head-icon {
            width: 31px;
            height: 31px;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 9px;
            background: var(--accent-gradient);
            color: var(--white);
            font-size: 12.5px;
            box-shadow: var(--shadow-input);
        }
        .ws-sw-head .head-text { flex: 1; min-width: 0; }
        .ws-sw-head h4 {
            font-size: 13.5px;
            font-weight: 600;
            color: var(--text-primary);
            line-height: 1.3;
        }
        .ws-sw-head .head-sub {
            font-size: 10.5px;
            color: var(--text-muted);
            margin-top: 1px;
        }
        .ws-sw-head .close-btn {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 12.5px;
            cursor: pointer;
            width: 27px;
            height: 27px;
            border-radius: 7px;
            transition: all 0.15s;
        }
        .ws-sw-head .close-btn:hover {
            color: var(--text-primary);
            background: var(--bg-hover);
        }
        .ws-sw-body {
            padding: 16px 18px 18px;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
            gap: 18px;
        }
        .ws-sw-body::-webkit-scrollbar { width: 4px; }
        .ws-sw-body::-webkit-scrollbar-track { background: transparent; }
        .ws-sw-body::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }

        .ws-sw-label {
            font-size: 10.5px;
            font-weight: 600;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 0.6px;
            margin-bottom: 7px;
        }
        .ws-sw-ico {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 8px;
        }
        /* 当前工作区卡片：品牌色高亮 */
        .ws-sw-current {
            display: flex;
            align-items: center;
            gap: 11px;
            padding: 11px 13px;
            border: 1px solid var(--border-active);
            border-radius: var(--radius-sm);
            background: var(--bg-active);
        }
        .ws-sw-current .ws-sw-ico {
            width: 31px;
            height: 31px;
            background: var(--accent-gradient);
            color: var(--white);
            font-size: 12.5px;
            box-shadow: var(--shadow-input);
        }
        .ws-sw-current .info { flex: 1; min-width: 0; }
        .ws-sw-current .name {
            font-size: 12px;
            font-weight: 600;
            color: var(--text-primary);
        }
        .ws-sw-current .path {
            font-size: 10.5px;
            color: var(--text-secondary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            margin-top: 2px;
        }
        .ws-sw-current .cur-tag {
            flex-shrink: 0;
            font-size: 10.5px;
            font-weight: 600;
            color: var(--accent-light);
            background: var(--bg-surface);
            border: 1px solid var(--border-active);
            border-radius: 999px;
            padding: 2px 9px;
        }
        /* 最近打开列表 */
        .ws-sw-recent { display: flex; flex-direction: column; gap: 3px; }
        .ws-sw-item {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 7px 9px;
            border-radius: 9px;
            border: 1px solid transparent;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-item:hover {
            background: var(--bg-hover);
            border-color: var(--border-color);
        }
        .ws-sw-item .ws-sw-ico {
            width: 25px;
            height: 25px;
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            color: var(--text-secondary);
            font-size: 11px;
            transition: all 0.15s;
        }
        .ws-sw-item:hover .ws-sw-ico {
            color: var(--accent-light);
            border-color: var(--border-active);
        }
        .ws-sw-item .info { flex: 1; min-width: 0; }
        .ws-sw-item .name {
            font-size: 11.5px;
            font-weight: 500;
            color: var(--text-primary);
            transition: color 0.15s;
        }
        .ws-sw-item:hover .name { color: var(--accent-light); }
        .ws-sw-item .path {
            font-size: 10.5px;
            color: var(--text-muted);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            margin-top: 1px;
        }
        .ws-sw-item .tag {
            flex-shrink: 0;
            font-size: 10.5px;
            color: var(--text-muted);
            padding: 1px 4px;
        }
        .ws-sw-item .go {
            flex-shrink: 0;
            display: none;
            color: var(--accent-light);
            font-size: 11px;
            padding: 1px 4px;
        }
        .ws-sw-item:hover .tag { display: none; }
        .ws-sw-item:hover .go { display: block; }
        .ws-sw-item.missing { opacity: 0.45; cursor: not-allowed; }
        .ws-sw-item.missing:hover { background: transparent; border-color: transparent; }
        .ws-sw-item.missing:hover .tag { display: block; }
        .ws-sw-item.missing:hover .go { display: none; }
        .ws-sw-item.missing:hover .ws-sw-ico { color: var(--text-secondary); border-color: var(--border-color); }
        .ws-sw-item.missing:hover .name { color: var(--text-primary); }
        .ws-sw-empty {
            font-size: 11px;
            color: var(--text-muted);
            padding: 6px 2px;
        }
        /* 浏览入口：整行虚线卡片，视觉上与列表项同族 */
        .ws-sw-browse-entry {
            display: flex;
            align-items: center;
            gap: 10px;
            width: 100%;
            padding: 9px 11px;
            border: 1px dashed var(--border-color);
            border-radius: 9px;
            background: transparent;
            cursor: pointer;
            text-align: left;
            transition: all 0.15s;
        }
        .ws-sw-browse-entry:hover {
            border-color: var(--border-active);
            background: var(--bg-active);
        }
        .ws-sw-browse-entry .ws-sw-ico {
            width: 25px;
            height: 25px;
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            color: var(--accent-light);
            font-size: 11px;
        }
        .ws-sw-browse-entry .info { flex: 1; min-width: 0; }
        .ws-sw-browse-entry .name {
            font-size: 11.5px;
            font-weight: 500;
            color: var(--text-primary);
        }
        .ws-sw-browse-entry .path {
            font-size: 10.5px;
            color: var(--text-muted);
            margin-top: 1px;
        }
        .ws-sw-browse-entry .go {
            color: var(--text-muted);
            font-size: 10.5px;
            transition: color 0.15s;
        }
        .ws-sw-browse-entry:hover .go { color: var(--accent-light); }
        /* ===== 浏览视图 ===== */
        .ws-sw-browse-view {
            display: flex;
            flex-direction: column;
            gap: 11px;
            min-height: 0;
        }
        /* 地址栏：上级/主目录 + 可编辑路径输入（回车跳转） */
        .ws-sw-addr-row { display: flex; gap: 7px; }
        .ws-sw-addr-row .nav-btn {
            flex-shrink: 0;
            width: 32px;
            height: 32px;
            border: 1px solid var(--border-color);
            border-radius: 9px;
            background: var(--bg-input);
            color: var(--text-secondary);
            font-size: 11px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-addr-row .nav-btn:hover {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-active);
        }
        .ws-sw-addr-row .nav-btn:disabled { opacity: 0.35; cursor: not-allowed; }
        .ws-sw-addr-row input {
            flex: 1;
            min-width: 0;
            height: 32px;
            padding: 0 12px;
            border: 1px solid var(--border-color);
            border-radius: 9px;
            background: var(--bg-input);
            color: var(--text-primary);
            font-size: 11.5px;
            font-family: inherit;
            outline: none;
            transition: all 0.15s;
        }
        .ws-sw-addr-row input:focus {
            border-color: var(--border-active);
            box-shadow: var(--shadow-input);
        }
        .ws-sw-addr-row input::placeholder { color: var(--text-muted); }
        /* 磁盘/主目录快捷 chips */
        .ws-sw-drives { display: flex; flex-wrap: wrap; gap: 5px; }
        .ws-sw-drives .drive-chip {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            padding: 5px 11px;
            border: 1px solid var(--border-color);
            border-radius: 7px;
            background: transparent;
            color: var(--text-secondary);
            font-size: 10.5px;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-drives .drive-chip:hover {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-active);
        }
        .ws-sw-drives .drive-chip i { font-size: 10.5px; }
        .ws-sw-drives .drive-chip.on {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-active);
        }
        /* 子目录大列表 */
        .ws-sw-br-list {
            flex: 1;
            min-height: 198px;
            max-height: 288px;
            overflow-y: auto;
            border: 1px solid var(--border-color);
            border-radius: 9px;
            padding: 5px;
        }
        .ws-sw-br-list::-webkit-scrollbar { width: 4px; }
        .ws-sw-br-list::-webkit-scrollbar-track { background: transparent; }
        .ws-sw-br-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }
        .ws-sw-br-item {
            display: flex;
            align-items: center;
            gap: 9px;
            padding: 7px 10px;
            border-radius: 7px;
            font-size: 11.5px;
            color: var(--text-primary);
            cursor: pointer;
            transition: background 0.12s;
        }
        .ws-sw-br-item:hover { background: var(--bg-hover); }
        .ws-sw-br-item i {
            font-size: 11px;
            color: var(--text-secondary);
            width: 14px;
            text-align: center;
            transition: color 0.12s;
        }
        .ws-sw-br-item:hover i { color: var(--accent-light); }
        .ws-sw-br-item .enter {
            margin-left: auto;
            display: none;
            color: var(--text-muted);
            font-size: 10px;
        }
        .ws-sw-br-item:hover .enter { display: block; }
        .ws-sw-br-empty {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 7px;
            min-height: 162px;
            font-size: 11px;
            color: var(--text-muted);
            text-align: center;
            padding: 18px;
        }
        .ws-sw-br-empty i { font-size: 20px; opacity: 0.5; }
        .ws-sw-br-empty .create-btn {
            padding: 5px 13px;
            border: 1px solid var(--accent-light);
            border-radius: 7px;
            background: transparent;
            color: var(--accent-light);
            font-size: 11px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-br-empty .create-btn:hover { background: var(--accent-light); color: var(--white); }
        .ws-sw-br-empty.error { color: var(--error); }
        /* 底部动作栏：选中路径 + 打开按钮 */
        .ws-sw-br-foot {
            display: flex;
            align-items: center;
            gap: 9px;
        }
        .ws-sw-br-foot .sel {
            flex: 1;
            min-width: 0;
            display: flex;
            align-items: center;
            gap: 7px;
            font-size: 10.5px;
            color: var(--text-secondary);
        }
        .ws-sw-br-foot .sel i { color: var(--accent-light); font-size: 11px; flex-shrink: 0; }
        .ws-sw-br-foot .sel .p {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            direction: rtl;
            text-align: left;
        }
        .ws-sw-br-foot .pick-btn {
            flex-shrink: 0;
            height: 31px;
            padding: 0 16px;
            border: none;
            border-radius: 9px;
            background: var(--accent-gradient);
            color: var(--white);
            font-size: 11.5px;
            font-weight: 600;
            cursor: pointer;
            box-shadow: var(--shadow-input);
            transition: all 0.15s;
        }
        .ws-sw-br-foot .pick-btn:hover { filter: brightness(1.1); }
        .ws-sw-br-foot .pick-btn:disabled { opacity: 0.5; cursor: not-allowed; }
        /* 提示行：错误信息（两个视图共用 #wsSwHint） */
        .ws-sw-hint {
            display: none;
            align-items: center;
            gap: 9px;
            padding: 8px 12px;
            border: 1px dashed var(--border-color);
            border-radius: 9px;
            font-size: 11px;
            color: var(--text-secondary);
            animation: wsSwFade 0.18s ease;
        }
        .ws-sw-hint.show { display: flex; }
        .ws-sw-hint.error { color: var(--error); border-color: var(--error); }
    `;

    let switching = false;
    let mainData = null;        // 主视图数据缓存（返回时免重新请求）
    let browsePath = '';        // 浏览视图当前目录
    let browseParent = null;    // 当前目录上级（null=已在盘符列表层）
    let rootsCache = null;      // 盘符列表 + 主目录（进入浏览视图时拉取一次）

    function esc(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    function fmtTime(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        const pad = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }

    // Windows 绝对路径的父目录（仅用于确定浏览起点，失败时回退主目录）
    function parentOf(p) {
        const t = String(p || '').replace(/[\\/]+$/, '');
        const i = Math.max(t.lastIndexOf('\\'), t.lastIndexOf('/'));
        if (i <= 2) return t.slice(0, 1) + ':\\';
        return t.slice(0, i);
    }

    // ===== 头部视图态 =====
    function setHead(browsing) {
        const dialog = document.querySelector('#wsSwOverlay .ws-sw-dialog');
        if (dialog) dialog.classList.toggle('browsing', browsing);
        const title = document.getElementById('wsSwTitle');
        const sub = document.getElementById('wsSwSub');
        if (title) title.textContent = browsing ? '选择目录' : '切换工作区';
        if (sub) sub.textContent = browsing
            ? '进入目标目录后点击「打开此目录」'
            : '会话、文件树与技能将切换到新工作区';
    }

    // ===== 主视图 =====
    async function load() {
        const body = document.getElementById('wsSwBody');
        try {
            const res = await fetch('/api/workspace');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            mainData = data;
            renderMain(data);
            applyCurrentToSidebar(data.current);
        } catch (e) {
            body.innerHTML = `<div class="ws-sw-empty">加载失败：${esc(e.message)}</div>`;
        }
    }

    function renderMain(data) {
        setHead(false);
        const cur = data.current || {};
        const curPath = (cur.path || '').toLowerCase();
        // 当前工作区已用高亮卡片展示，最近列表中不再重复
        const recent = (Array.isArray(data.recent) ? data.recent : [])
            .filter(item => (item.path || '').toLowerCase() !== curPath);

        const items = recent.map(item => {
            const missing = item.exists === false;
            const tag = missing ? '<span class="tag">不存在</span>'
                      : (fmtTime(item.lastOpened) ? `<span class="tag">${esc(fmtTime(item.lastOpened))}</span>` : '');
            const click = missing ? '' :
                ` onclick="QWorkspace.switchTo(this.dataset.path)" data-path="${esc(item.path)}"`;
            return `
                <div class="ws-sw-item${missing ? ' missing' : ''}"${click}>
                    <span class="ws-sw-ico"><i class="fas fa-folder"></i></span>
                    <div class="info">
                        <div class="name">${esc(item.name)}</div>
                        <div class="path">${esc(item.path)}</div>
                    </div>
                    ${tag}
                    <span class="go"><i class="fas fa-arrow-right"></i></span>
                </div>`;
        }).join('');

        document.getElementById('wsSwBody').innerHTML = `
            <div>
                <div class="ws-sw-label">当前工作区</div>
                <div class="ws-sw-current">
                    <span class="ws-sw-ico"><i class="fas fa-folder-open"></i></span>
                    <div class="info">
                        <div class="name">${esc(cur.name)}</div>
                        <div class="path">${esc(cur.path)}</div>
                    </div>
                    <span class="cur-tag">使用中</span>
                </div>
            </div>
            <div>
                <div class="ws-sw-label">最近打开</div>
                <div class="ws-sw-recent">${items || '<div class="ws-sw-empty">暂无其他历史记录</div>'}</div>
            </div>
            <div>
                <div class="ws-sw-label">打开其他目录</div>
                <button class="ws-sw-browse-entry" onclick="QWorkspace.showBrowse()">
                    <span class="ws-sw-ico"><i class="fas fa-folder-plus"></i></span>
                    <div class="info">
                        <div class="name">浏览本机目录…</div>
                        <div class="path">从磁盘中选择一个目录作为工作区，也可粘贴路径跳转</div>
                    </div>
                    <span class="go"><i class="fas fa-chevron-right"></i></span>
                </button>
                <div class="ws-sw-hint" id="wsSwHint"></div>
            </div>`;
    }

    function applyCurrentToSidebar(cur) {
        if (!cur) return;
        const nameEl = document.getElementById('wsSwitchName');
        const pathEl = document.getElementById('wsSwitchPath');
        const btn = document.getElementById('wsSwitchBtn');
        if (nameEl) nameEl.textContent = cur.name || '工作区';
        if (pathEl) pathEl.textContent = cur.path || '';
        if (btn) btn.title = '切换工作区（当前：' + (cur.path || '') + '）';
    }

    // ===== 浏览视图 =====
    function renderBrowseSkeleton() {
        setHead(true);
        document.getElementById('wsSwBody').innerHTML = `
            <div class="ws-sw-browse-view">
                <div class="ws-sw-addr-row">
                    <button class="nav-btn" id="wsSwBrUp" title="上级目录" onclick="QWorkspace.browseUp()"><i class="fas fa-arrow-up"></i></button>
                    <button class="nav-btn" title="用户主目录" onclick="QWorkspace.browseHome()"><i class="fas fa-house"></i></button>
                    <input type="text" id="wsSwAddr" placeholder="输入或粘贴目录路径，回车跳转" spellcheck="false">
                </div>
                <div class="ws-sw-drives" id="wsSwDrives"></div>
                <div class="ws-sw-br-list" id="wsSwBrList"></div>
                <div class="ws-sw-hint" id="wsSwHint"></div>
                <div class="ws-sw-br-foot">
                    <div class="sel"><i class="fas fa-folder-open"></i><span class="p" id="wsSwBrSel"></span></div>
                    <button class="pick-btn" id="wsSwBrPick" onclick="QWorkspace.pickBrowsed()" disabled>打开此目录</button>
                </div>
            </div>`;

        const addr = document.getElementById('wsSwAddr');
        addr.addEventListener('keydown', e => {
            if (e.key === 'Enter') browseTo(addr.value.trim());
        });
    }

    // 磁盘快捷 chips（首次进入浏览视图拉取一次盘符列表）
    async function loadDrives() {
        if (!rootsCache) {
            try {
                const res = await fetch('/api/workspace/browse');
                if (res.ok) rootsCache = await res.json();
            } catch (e) { /* 拉不到盘符时 chips 区留空，不影响浏览 */ }
        }
        renderDrives();
    }

    function renderDrives() {
        const box = document.getElementById('wsSwDrives');
        if (!box || !rootsCache) return;
        const drives = Array.isArray(rootsCache.dirs) ? rootsCache.dirs : [];
        const cur = browsePath.toLowerCase();
        box.innerHTML = drives.map(d => {
            const on = cur.startsWith(String(d.path).toLowerCase());
            return `<button class="drive-chip${on ? ' on' : ''}" onclick="QWorkspace.browseTo(this.dataset.path)" data-path="${esc(d.path)}">
                        <i class="fas fa-hard-drive"></i>${esc(d.name)}
                    </button>`;
        }).join('');
    }

    async function browseTo(path) {
        if (!path) { QWorkspace.browseHome(); return; }
        const list = document.getElementById('wsSwBrList');
        if (!list) return;
        hideHint();
        list.innerHTML = '<div class="ws-sw-br-empty"><i class="fas fa-spinner fa-spin"></i>加载中...</div>';
        try {
            const res = await fetch('/api/workspace/browse?path=' + encodeURIComponent(path));
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                showBrowseError(path, data.message || '读取失败');
                return;
            }
            browsePath = data.path || '';
            browseParent = data.parent;
            renderBrowseList(data);
        } catch (e) {
            showBrowseError(path, '读取失败：' + e.message);
        }
    }

    function renderBrowseList(data) {
        const dirs = Array.isArray(data.dirs) ? data.dirs : [];
        const addr = document.getElementById('wsSwAddr');
        if (addr) addr.value = browsePath;
        const up = document.getElementById('wsSwBrUp');
        // parent 为空串表示已在盘符根，置灰上级按钮（换盘用下方 chips）
        if (up) up.disabled = !browseParent;
        renderDrives();

        document.getElementById('wsSwBrSel').textContent = browsePath;
        document.getElementById('wsSwBrPick').disabled = !browsePath;

        const list = document.getElementById('wsSwBrList');
        list.innerHTML = dirs.length
            ? dirs.map(d => `
                <div class="ws-sw-br-item" onclick="QWorkspace.browseTo(this.dataset.path)" data-path="${esc(d.path)}">
                    <i class="fas fa-folder"></i>
                    <span>${esc(d.name)}</span>
                    <span class="enter"><i class="fas fa-chevron-right"></i></span>
                </div>`).join('')
            : `<div class="ws-sw-br-empty">
                   <i class="far fa-folder-open"></i>
                   <span>该目录下没有子目录</span>
                   <span>可直接点击右下角「打开此目录」</span>
               </div>`;
    }

    // 浏览失败：路径像合法绝对路径时给「创建并打开」
    function showBrowseError(path, msg) {
        const list = document.getElementById('wsSwBrList');
        if (!list) return;
        const canCreate = /^[a-zA-Z]:[\\/]/.test(path);
        list.innerHTML = `
            <div class="ws-sw-br-empty error">
                <i class="fas fa-circle-exclamation"></i>
                <span>${esc(msg)}</span>
                ${canCreate ? `<button class="create-btn" onclick="QWorkspace.switchTo(this.dataset.path, true)" data-path="${esc(path)}">
                    <i class="fas fa-folder-plus"></i> 创建并打开该目录
                </button>` : ''}
            </div>`;
        document.getElementById('wsSwBrPick').disabled = true;
        document.getElementById('wsSwBrSel').textContent = '';
    }

    // ===== 提示行 =====
    function hideHint() {
        const hint = document.getElementById('wsSwHint');
        if (hint) { hint.className = 'ws-sw-hint'; hint.innerHTML = ''; }
    }

    function showError(msg) {
        const hint = document.getElementById('wsSwHint');
        if (!hint) return;
        hint.className = 'ws-sw-hint show error';
        hint.innerHTML = `<i class="fas fa-circle-exclamation"></i><span>${esc(msg)}</span>`;
    }

    // ===== 事件处理 =====
    window.QWorkspace = {
        // 进入浏览视图：起点为当前工作区的上级目录（项目通常是兄弟目录，一步可达）
        async showBrowse() {
            renderBrowseSkeleton();
            await loadDrives();
            const curPath = mainData && mainData.current ? mainData.current.path : '';
            const start = curPath ? parentOf(curPath) : ((rootsCache && rootsCache.home) || '');
            if (start) browseTo(start);
        },

        backToMain() {
            if (mainData) renderMain(mainData);
            else load();
        },

        browseTo(path) {
            browseTo(path);
        },

        browseUp() {
            if (browseParent) browseTo(browseParent);
        },

        browseHome() {
            const home = rootsCache && rootsCache.home;
            if (home) browseTo(home);
        },

        // 打开当前浏览目录
        pickBrowsed() {
            if (browsePath) QWorkspace.switchTo(browsePath);
        },

        // 切换到指定目录（create=true 时目录不存在则先创建）
        async switchTo(path, create) {
            if (switching) return;
            if (window.isChatStreaming && window.isChatStreaming()) {
                showError('有对话正在进行，请等待完成后再切换');
                return;
            }
            switching = true;
            hideHint();
            const pickBtn = document.getElementById('wsSwBrPick');
            if (pickBtn) pickBtn.disabled = true;
            try {
                const res = await fetch('/api/workspace/switch', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path, create: !!create })
                });
                const data = await res.json().catch(() => ({}));

                if (res.ok && data.success) {
                    applyCurrentToSidebar(data.workspace);
                    window.closeWorkspaceSwitcher();
                    // 路径未变化时无需刷新页面状态
                    if (data.changed && typeof window.onWorkspaceSwitched === 'function') {
                        await window.onWorkspaceSwitched();
                    }
                } else if (data.code === 'NOT_FOUND') {
                    // 目录不存在（主视图最近列表的兜底场景）：进浏览视图给创建选项
                    renderBrowseSkeleton();
                    loadDrives();
                    showBrowseError(path, '目录不存在');
                } else {
                    showError(data.message || ('切换失败（HTTP ' + res.status + '）'));
                }
            } catch (e) {
                showError('切换失败：' + e.message);
            } finally {
                switching = false;
                if (pickBtn && browsePath) pickBtn.disabled = false;
            }
        }
    };

    // ===== 挂载 =====
    function mount() {
        const style = document.createElement('style');
        style.id = 'project-panel-style';
        style.textContent = CSS;
        document.head.appendChild(style);

        const overlay = document.createElement('div');
        overlay.className = 'ws-sw-overlay';
        overlay.id = 'wsSwOverlay';
        overlay.innerHTML = `
            <div class="ws-sw-dialog">
                <div class="ws-sw-head">
                    <button class="back-btn" title="返回" onclick="QWorkspace.backToMain()"><i class="fas fa-arrow-left"></i></button>
                    <span class="head-icon"><i class="fas fa-folder-tree"></i></span>
                    <div class="head-text">
                        <h4 id="wsSwTitle">切换工作区</h4>
                        <div class="head-sub" id="wsSwSub">会话、文件树与技能将切换到新工作区</div>
                    </div>
                    <button class="close-btn" title="关闭" onclick="closeWorkspaceSwitcher()"><i class="fas fa-times"></i></button>
                </div>
                <div class="ws-sw-body" id="wsSwBody">
                    <div class="ws-sw-empty">加载中...</div>
                </div>
            </div>`;
        document.body.appendChild(overlay);

        overlay.addEventListener('click', function (e) {
            if (e.target === this) closeWorkspaceSwitcher();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            const open = document.getElementById('wsSwOverlay').classList.contains('open');
            if (!open) return;
            // 浏览视图下 Esc 先返回主视图，再次 Esc 才关闭
            const browsing = document.querySelector('#wsSwOverlay .ws-sw-dialog.browsing');
            if (browsing) QWorkspace.backToMain();
            else closeWorkspaceSwitcher();
        });

        // 启动即回填侧边栏当前工作区名称
        fetch('/api/workspace')
            .then(res => res.ok ? res.json() : null)
            .then(data => { if (data) applyCurrentToSidebar(data.current); })
            .catch(() => {});
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', mount);
    } else {
        mount();
    }

    // ===== 打开 / 关闭 =====
    window.openWorkspaceSwitcher = function () {
        // 流式期间入口已置灰，此处为直接调用的兜底拦截
        if (window.isChatStreaming && window.isChatStreaming()) return;
        document.getElementById('wsSwOverlay').classList.add('open');
        setHead(false);
        document.getElementById('wsSwBody').innerHTML = '<div class="ws-sw-empty">加载中...</div>';
        load();
    };

    window.closeWorkspaceSwitcher = function () {
        document.getElementById('wsSwOverlay').classList.remove('open');
    };
})();
