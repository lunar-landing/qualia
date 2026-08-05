/**
 * ProjectPanel —— 工作区切换面板（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 布局（IDE「打开项目」风格，左右双栏）：
 *   - 左栏：品牌块 + 「打开项目」标题引导 + 「打开文件夹」主按钮
 *   - 右侧双视图：
 *     · 最近打开视图：最近项目列表（~/.qualia/workspaces.json）+ 搜索过滤
 *     · 目录浏览视图：地址栏 + 磁盘快捷入口 + 子目录列表 + 创建并打开
 *
 * 职责：
 *   1. 侧边栏入口按钮回填当前工作区名称与路径（#wsSwitchName / #wsSwitchPath）
 *   2. 流式对话期间切换动作拦截；切换成功后回调 window.onWorkspaceSwitched()
 *   3. 强制模式（启动未绑定工作区）：弹窗不可关闭，选定后整页重载
 *
 * 对外 API：
 *   window.openWorkspaceSwitcher()   打开弹窗（每次打开都重新拉取最新数据，回到最近视图）
 *   window.closeWorkspaceSwitcher()  关闭弹窗
 *   （内部事件处理统一挂在 window.QWorkspace 命名空间下，供生成的 DOM 引用）
 */
(function () {
    'use strict';

    // ===== 样式注入 =====
    const CSS = `
        /* ===== 工作区切换弹窗：IDE「打开项目」风格双栏 ===== */
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
            display: flex;
            width: min(860px, calc(100vw - 48px));
            height: min(540px, calc(100vh - 64px));
            min-height: 380px;
            background: var(--bg-surface);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            box-shadow: var(--shadow);
            overflow: hidden;
        }
        .ws-sw-overlay.open .ws-sw-dialog {
            animation: wsSwPop 0.26s cubic-bezier(0.16, 1, 0.3, 1);
        }
        @keyframes wsSwPop {
            from { opacity: 0; transform: translateY(12px) scale(0.985); }
            to { opacity: 1; transform: none; }
        }

        /* ----- 左栏：品牌 + 操作入口 ----- */
        .ws-sw-side {
            width: 248px;
            flex-shrink: 0;
            display: flex;
            flex-direction: column;
            padding: 24px 22px 18px;
            border-right: 1px solid var(--border-color);
        }
        .ws-sw-side-top {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 8px;
        }
        .ws-sw-brand { display: flex; align-items: center; gap: 11px; }
        .ws-sw-logo {
            width: 40px;
            height: 40px;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 12px;
            background: var(--accent-gradient);
            color: var(--white);
            box-shadow: var(--shadow-input), inset 0 1px 0 rgba(255, 255, 255, 0.22);
        }
        .ws-sw-logo svg { width: 19px; height: 19px; }
        .ws-sw-brand-name {
            font-size: 14px;
            font-weight: 650;
            color: var(--text-primary);
            letter-spacing: 0.1px;
        }
        .ws-sw-brand-ver {
            margin-top: 2px;
            font-size: 10px;
            color: var(--text-muted);
        }
        .ws-sw-close {
            flex-shrink: 0;
            width: 27px;
            height: 27px;
            border: none;
            border-radius: 7px;
            background: transparent;
            color: var(--text-muted);
            font-size: 12px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-close:hover { color: var(--text-primary); background: var(--bg-hover); }
        .ws-sw-side-title {
            margin-top: 30px;
            font-size: 18px;
            font-weight: 650;
            letter-spacing: -0.01em;
            color: var(--text-primary);
        }
        .ws-sw-side-sub {
            margin-top: 8px;
            font-size: 11.5px;
            line-height: 1.65;
            color: var(--text-muted);
        }
        .ws-sw-open-btn {
            margin-top: 20px;
            height: 38px;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 9px;
            border: none;
            border-radius: 9px;
            background: var(--accent-gradient);
            color: var(--white);
            font-size: 12.5px;
            font-weight: 600;
            font-family: inherit;
            cursor: pointer;
            box-shadow: var(--shadow-input);
            transition: filter 0.15s;
        }
        .ws-sw-open-btn:hover { filter: brightness(1.12); }
        .ws-sw-open-btn i { font-size: 12.5px; }
        .ws-sw-side-foot {
            margin-top: auto;
            padding-top: 16px;
            font-size: 10px;
            line-height: 1.7;
            color: var(--text-muted);
        }

        /* ----- 右侧内容区 ----- */
        .ws-sw-pane {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
        }
        .ws-sw-view { display: none; flex-direction: column; flex: 1; min-height: 0; }
        .ws-sw-view.on { display: flex; animation: wsSwFade 0.2s ease; }

        /* --- 最近打开视图 --- */
        .ws-sw-rp-head {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 22px 24px 12px;
        }
        .ws-sw-rp-head h3 {
            font-size: 13px;
            font-weight: 620;
            letter-spacing: 0.2px;
            color: var(--text-secondary);
        }
        .ws-sw-rp-count { color: var(--text-muted); font-weight: 500; margin-left: 3px; }
        .ws-sw-search {
            margin-left: auto;
            display: flex;
            align-items: center;
            gap: 8px;
            width: 208px;
            height: 30px;
            padding: 0 11px;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background: var(--bg-input);
            color: var(--text-muted);
            font-size: 11px;
            transition: all 0.15s;
        }
        .ws-sw-search:focus-within {
            border-color: var(--border-active);
            box-shadow: var(--shadow-input);
        }
        .ws-sw-search input {
            flex: 1;
            min-width: 0;
            border: none;
            outline: none;
            background: transparent;
            color: var(--text-primary);
            font-size: 11.5px;
            font-family: inherit;
        }
        .ws-sw-search input::placeholder { color: var(--text-muted); }

        .ws-sw-rp-list {
            flex: 1;
            min-height: 0;
            overflow-y: auto;
            padding: 2px 18px 12px;
        }
        .ws-sw-rp-list::-webkit-scrollbar { width: 4px; }
        .ws-sw-rp-list::-webkit-scrollbar-track { background: transparent; }
        .ws-sw-rp-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }

        .ws-sw-rp {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 10px 12px;
            border-radius: 9px;
            border: 1px solid transparent;
            cursor: pointer;
            transition: background 0.13s, border-color 0.13s;
        }
        .ws-sw-rp + .ws-sw-rp { margin-top: 2px; }
        .ws-sw-rp:hover { background: var(--bg-hover); }
        .ws-sw-rp.current { background: var(--bg-active); border-color: var(--border-active); }
        .ws-sw-rp .ico {
            width: 34px;
            height: 34px;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 9px;
            border: 1px solid var(--border-color);
            background: var(--bg-hover);
            color: var(--text-muted);
            font-size: 13px;
            transition: all 0.13s;
        }
        .ws-sw-rp:hover .ico, .ws-sw-rp.current .ico {
            color: var(--accent-light);
            border-color: var(--border-active);
        }
        .ws-sw-rp .meta { flex: 1; min-width: 0; }
        .ws-sw-rp .nm {
            font-size: 12.5px;
            font-weight: 550;
            color: var(--text-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .ws-sw-rp .pt {
            margin-top: 3px;
            font-size: 10.5px;
            color: var(--text-muted);
            font-family: var(--font-mono, ui-monospace, Consolas, monospace);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .ws-sw-rp .cur-tag {
            flex-shrink: 0;
            font-size: 10px;
            font-weight: 600;
            color: var(--accent-light);
            border: 1px solid var(--border-active);
            border-radius: 999px;
            padding: 2px 8px;
        }
        .ws-sw-rp .tm { flex-shrink: 0; font-size: 10px; color: var(--text-muted); }
        .ws-sw-rp .go {
            flex-shrink: 0;
            color: var(--accent-light);
            font-size: 10.5px;
            opacity: 0;
            transform: translateX(-3px);
            transition: all 0.15s;
        }
        .ws-sw-rp:hover .go { opacity: 1; transform: none; }
        .ws-sw-rp.missing { opacity: 0.4; cursor: not-allowed; }
        .ws-sw-rp.missing:hover { background: transparent; }
        .ws-sw-rp.missing:hover .ico { color: var(--text-muted); border-color: var(--border-color); }
        .ws-sw-rp.missing:hover .go { opacity: 0; }
        .ws-sw-rp.missing .tm { color: var(--error); }
        .ws-sw-rp.hidden { display: none; }
        .ws-sw-empty {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 6px;
            padding: 34px 20px;
            font-size: 11.5px;
            color: var(--text-muted);
            text-align: center;
        }
        .ws-sw-empty i { font-size: 18px; opacity: 0.5; }
        .ws-sw-rp-foot {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            gap: 7px;
            padding: 11px 24px 14px;
            border-top: 1px solid var(--border-color);
            font-size: 10.5px;
            color: var(--text-muted);
        }
        .ws-sw-rp-foot i { font-size: 10.5px; }
        .ws-sw-rp-foot .foot-close {
            margin-left: auto;
            flex-shrink: 0;
            height: 26px;
            padding: 0 13px;
            border: 1px solid var(--border-color);
            border-radius: 7px;
            background: transparent;
            color: var(--text-secondary);
            font-size: 11px;
            font-weight: 550;
            font-family: inherit;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-rp-foot .foot-close:hover {
            color: var(--text-primary);
            border-color: rgba(255, 255, 255, 0.12);
            background: var(--bg-hover);
        }

        /* --- 目录浏览视图 --- */
        .ws-sw-br-head {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 20px 24px 0;
        }
        .ws-sw-br-head .back-btn {
            width: 30px;
            height: 30px;
            flex-shrink: 0;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background: transparent;
            color: var(--text-secondary);
            font-size: 11.5px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-br-head .back-btn:hover {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-active);
        }
        .ws-sw-br-head h3 {
            font-size: 14px;
            font-weight: 620;
            letter-spacing: -0.01em;
            color: var(--text-primary);
        }
        .ws-sw-br-head .sub { margin-top: 3px; font-size: 10.5px; color: var(--text-muted); }

        /* 地址栏：上级/主目录 + 可编辑路径输入（回车跳转） */
        .ws-sw-addr-row { display: flex; gap: 8px; margin: 16px 24px 0; }
        .ws-sw-addr-row .nav-btn {
            flex-shrink: 0;
            width: 32px;
            height: 32px;
            border: 1px solid var(--border-color);
            border-radius: 8px;
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
            border-radius: 8px;
            background: var(--bg-input);
            color: var(--text-primary);
            font-size: 11.5px;
            font-family: var(--font-mono, ui-monospace, Consolas, monospace);
            outline: none;
            transition: all 0.15s;
        }
        .ws-sw-addr-row input:focus {
            border-color: var(--border-active);
            box-shadow: var(--shadow-input);
        }
        .ws-sw-addr-row input::placeholder { color: var(--text-muted); }

        /* 磁盘快捷 chips */
        .ws-sw-drives { display: flex; flex-wrap: wrap; gap: 6px; margin: 10px 24px 0; }
        .ws-sw-drives:empty { display: none; }
        .ws-sw-drives .drive-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            height: 26px;
            padding: 0 11px;
            border: 1px solid var(--border-color);
            border-radius: 7px;
            background: transparent;
            color: var(--text-secondary);
            font-size: 10.5px;
            font-weight: 550;
            font-family: var(--font-mono, ui-monospace, Consolas, monospace);
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-drives .drive-chip:hover, .ws-sw-drives .drive-chip.on {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-active);
        }
        .ws-sw-drives .drive-chip i { font-size: 10px; }

        /* 子目录列表 */
        .ws-sw-br-list {
            flex: 1;
            min-height: 0;
            margin: 12px 24px 0;
            border: 1px solid var(--border-color);
            border-radius: 11px;
            padding: 5px;
            overflow-y: auto;
        }
        .ws-sw-br-list::-webkit-scrollbar { width: 4px; }
        .ws-sw-br-list::-webkit-scrollbar-track { background: transparent; }
        .ws-sw-br-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }
        .ws-sw-br-item {
            display: flex;
            align-items: center;
            gap: 11px;
            height: 38px;
            padding: 0 12px;
            border-radius: 8px;
            border: 1px solid transparent;
            font-size: 12px;
            font-weight: 500;
            color: var(--text-primary);
            cursor: pointer;
            transition: background 0.12s, border-color 0.12s;
        }
        .ws-sw-br-item i {
            font-size: 12.5px;
            color: var(--text-muted);
            width: 15px;
            text-align: center;
            transition: color 0.12s;
        }
        .ws-sw-br-item:hover { background: var(--bg-hover); }
        .ws-sw-br-item:hover i { color: var(--text-secondary); }
        .ws-sw-br-item .enter {
            margin-left: auto;
            color: var(--text-muted);
            font-size: 10px;
            opacity: 0;
            transition: opacity 0.12s;
        }
        .ws-sw-br-item:hover .enter { opacity: 1; }
        .ws-sw-br-empty {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 7px;
            min-height: 150px;
            height: 100%;
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

        /* 提示行：错误信息（两个视图各一条） */
        .ws-sw-hint {
            display: none;
            align-items: center;
            gap: 9px;
            margin: 10px 24px 0;
            padding: 8px 12px;
            border: 1px dashed var(--border-color);
            border-radius: 9px;
            font-size: 11px;
            color: var(--text-secondary);
            animation: wsSwFade 0.18s ease;
        }
        .ws-sw-hint.show { display: flex; }
        .ws-sw-hint.error { color: var(--error); border-color: var(--error); }

        /* 底部动作栏：选中路径 + 取消 + 打开 */
        .ws-sw-br-foot {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 14px 24px 18px;
        }
        .ws-sw-br-foot .sel {
            flex: 1;
            min-width: 0;
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 10.5px;
            color: var(--text-muted);
        }
        .ws-sw-br-foot .sel i { color: var(--accent-light); font-size: 11px; flex-shrink: 0; }
        .ws-sw-br-foot .sel .p {
            font-family: var(--font-mono, ui-monospace, Consolas, monospace);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            direction: rtl;
            text-align: left;
        }
        .ws-sw-br-foot .ghost-btn {
            flex-shrink: 0;
            height: 34px;
            padding: 0 17px;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            background: transparent;
            color: var(--text-secondary);
            font-size: 12px;
            font-weight: 550;
            font-family: inherit;
            cursor: pointer;
            transition: all 0.15s;
        }
        .ws-sw-br-foot .ghost-btn:hover {
            color: var(--text-primary);
            border-color: rgba(255, 255, 255, 0.12);
            background: var(--bg-hover);
        }
        .ws-sw-br-foot .pick-btn {
            flex-shrink: 0;
            height: 34px;
            padding: 0 20px;
            border: none;
            border-radius: 8px;
            background: var(--accent-gradient);
            color: var(--white);
            font-size: 12px;
            font-weight: 600;
            font-family: inherit;
            cursor: pointer;
            box-shadow: var(--shadow-input);
            transition: filter 0.15s;
        }
        .ws-sw-br-foot .pick-btn:hover { filter: brightness(1.1); }
        .ws-sw-br-foot .pick-btn:disabled { opacity: 0.45; cursor: not-allowed; filter: none; }

        /* ----- 强制模式（启动时未绑定工作区）：拦截一切关闭路径 ----- */
        .ws-sw-overlay.forced .ws-sw-close { display: none; }
        .ws-sw-overlay.forced .ws-sw-rp-foot .foot-close { display: none; }
        .ws-sw-overlay.forced .ws-sw-br-foot .ghost-btn { display: none; }

        /* ----- 窄屏：左栏收窄为顶部条 ----- */
        @media (max-width: 720px) {
            .ws-sw-dialog {
                flex-direction: column;
                width: min(520px, calc(100vw - 32px));
                height: auto;
                max-height: calc(100vh - 48px);
            }
            .ws-sw-side {
                width: auto;
                border-right: none;
                border-bottom: 1px solid var(--border-color);
                padding: 16px 18px;
            }
            .ws-sw-side-title, .ws-sw-side-sub, .ws-sw-side-foot { display: none; }
            .ws-sw-open-btn { margin-top: 12px; }
            .ws-sw-rp-list { min-height: 220px; }
            .ws-sw-br-list { min-height: 180px; }
        }
    `;

    let switching = false;
    let forced = false;         // 强制选择模式：启动时未绑定工作区，弹窗不可关闭

    // 品牌四角星（与聊天区 AI 头像同款）
    const STAR_SVG = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2c1 6 4 9 10 10-6 1-9 4-10 10-1-6-4-9-10-10 6-1 9-4 10-10z"/></svg>';
    let mainData = null;        // 最近视图数据缓存（返回时免重新请求）
    let browsePath = '';        // 浏览视图当前目录
    let browseParent = null;    // 当前目录上级（null=已在盘符列表层）
    let rootsCache = null;      // 盘符列表 + 主目录（进入浏览视图时拉取一次）

    function esc(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    // 相对时间：今天 HH:mm / 昨天 / N 天前 / yyyy-MM-dd
    function relTime(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        const pad = n => String(n).padStart(2, '0');
        const now = new Date();
        const dayStart = x => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
        const diffDays = Math.round((dayStart(now) - dayStart(d)) / 86400000);
        if (diffDays <= 0) return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
        if (diffDays === 1) return '昨天';
        if (diffDays < 7) return `${diffDays} 天前`;
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    }

    // Windows 绝对路径的父目录（仅用于确定浏览起点，失败时回退主目录）
    function parentOf(p) {
        const t = String(p || '').replace(/[\\/]+$/, '');
        const i = Math.max(t.lastIndexOf('\\'), t.lastIndexOf('/'));
        if (i <= 2) return t.slice(0, 1) + ':\\';
        return t.slice(0, i);
    }

    // ===== 视图切换 =====
    function setView(browsing) {
        const dialog = document.querySelector('#wsSwOverlay .ws-sw-dialog');
        if (dialog) dialog.classList.toggle('browsing', browsing);
        document.getElementById('wsSwViewRecent').classList.toggle('on', !browsing);
        document.getElementById('wsSwViewBrowse').classList.toggle('on', browsing);
        hideHint();
    }

    // ===== 最近打开视图 =====
    async function load() {
        const list = document.getElementById('wsSwRecentList');
        try {
            const res = await fetch('/api/workspace');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            mainData = data;
            renderRecent(data);
            applyCurrentToSidebar(data.current);
        } catch (e) {
            list.innerHTML = `<div class="ws-sw-empty"><i class="fas fa-circle-exclamation"></i><span>加载失败：${esc(e.message)}</span></div>`;
        }
    }

    function renderRecent(data) {
        setView(false);
        const cur = data.current || null;
        const curPath = cur ? String(cur.path || '').toLowerCase() : '';
        const recent = Array.isArray(data.recent) ? data.recent : [];

        const items = recent.map(item => {
            const missing = item.exists === false;
            const isCurrent = (item.path || '').toLowerCase() === curPath;
            const tm = missing ? '不存在' : relTime(item.lastOpened);
            const click = missing ? '' :
                ` onclick="QWorkspace.switchTo(this.dataset.path)" data-path="${esc(item.path)}"`;
            return `
                <div class="ws-sw-rp${missing ? ' missing' : ''}${isCurrent ? ' current' : ''}"${click}>
                    <span class="ico"><i class="fas fa-folder${isCurrent ? '-open' : ''}"></i></span>
                    <div class="meta">
                        <div class="nm">${esc(item.name)}</div>
                        <div class="pt">${esc(item.path)}</div>
                    </div>
                    ${isCurrent ? '<span class="cur-tag">使用中</span>' : ''}
                    <span class="tm">${esc(tm)}</span>
                    <i class="fas fa-arrow-right go"></i>
                </div>`;
        }).join('');

        const count = document.getElementById('wsSwCount');
        if (count) count.textContent = recent.length ? ' · ' + recent.length : '';

        document.getElementById('wsSwRecentList').innerHTML = items || `
            <div class="ws-sw-empty">
                <i class="far fa-folder-open"></i>
                <span>${forced ? '暂无打开记录，点击左侧「打开文件夹」从本机选择' : '暂无历史记录'}</span>
            </div>`;

        const search = document.getElementById('wsSwSearch');
        if (search) {
            search.value = '';
            search.style.display = recent.length ? '' : 'none';
        }
        const foot = document.getElementById('wsSwRpFoot');
        if (foot) foot.innerHTML = `
            <i class="fas fa-circle-info"></i>
            <span>${forced ? '首次启动，任选一个目录作为工作区即可开始' : '点击项目立即切换；已删除的目录会置灰显示'}</span>
            <button class="foot-close" onclick="closeWorkspaceSwitcher()">关闭</button>`;
    }

    // 最近列表搜索过滤（纯前端）
    function filterRecent(q) {
        const kw = String(q || '').trim().toLowerCase();
        document.querySelectorAll('#wsSwRecentList .ws-sw-rp').forEach(row => {
            row.classList.toggle('hidden', !!kw && !row.textContent.toLowerCase().includes(kw));
        });
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

    // ===== 目录浏览视图 =====
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
                   <span>可直接点击右下角「打开」</span>
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
        const pick = document.getElementById('wsSwBrPick');
        if (pick) pick.disabled = true;
        document.getElementById('wsSwBrSel').textContent = '';
    }

    // ===== 提示行 =====
    function hintEl() {
        const browsing = document.querySelector('#wsSwOverlay .ws-sw-dialog.browsing');
        return document.getElementById(browsing ? 'wsSwBrHint' : 'wsSwHint');
    }

    function hideHint() {
        ['wsSwHint', 'wsSwBrHint'].forEach(id => {
            const el = document.getElementById(id);
            if (el) { el.className = 'ws-sw-hint'; el.innerHTML = ''; }
        });
    }

    function showError(msg) {
        const hint = hintEl();
        if (!hint) return;
        hint.className = 'ws-sw-hint show error';
        hint.innerHTML = `<i class="fas fa-circle-exclamation"></i><span>${esc(msg)}</span>`;
    }

    // ===== 事件处理 =====
    window.QWorkspace = {
        // 进入浏览视图：起点为当前工作区的上级目录（项目通常是兄弟目录，一步可达）
        async showBrowse() {
            setView(true);
            await loadDrives();
            const curPath = mainData && mainData.current ? mainData.current.path : '';
            const start = curPath ? parentOf(curPath) : ((rootsCache && rootsCache.home) || '');
            if (start) browseTo(start);
        },

        backToMain() {
            if (mainData) renderRecent(mainData);
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
                    if (forced) {
                        // 首次选择：整页重载让全部模块按新工作区初始化，也顺带解除强制态
                        location.replace('/');
                        return;
                    }
                    applyCurrentToSidebar(data.workspace);
                    window.closeWorkspaceSwitcher();
                    // 路径未变化时无需刷新页面状态
                    if (data.changed && typeof window.onWorkspaceSwitched === 'function') {
                        await window.onWorkspaceSwitched();
                    }
                } else if (data.code === 'NOT_FOUND') {
                    // 目录不存在（最近列表的兜底场景）：进浏览视图给创建选项
                    setView(true);
                    loadDrives();
                    showBrowseError(path, '目录不存在');
                } else {
                    showError(data.message || ('切换失败（HTTP ' + res.status + '）'));
                }
            } catch (e) {
                showError('切换失败：' + e.message);
            } finally {
                switching = false;
                const btn = document.getElementById('wsSwBrPick');
                if (btn && browsePath) btn.disabled = false;
            }
        }
    };

    // 强制选择（启动时未绑定工作区）：同一弹窗，但拦截一切关闭路径
    function openForced() {
        forced = true;
        window.isWorkspacePickerForced = () => true;
        const overlay = document.getElementById('wsSwOverlay');
        overlay.classList.add('open', 'forced');
        // 左栏文案切到首次启动语境
        const sub = document.getElementById('wsSwSideSub');
        if (sub) sub.textContent = '首次启动，请选择一个工作区目录——会话、文件树与技能都会存放在这里';
        document.getElementById('wsSwRecentList').innerHTML =
            '<div class="ws-sw-empty"><i class="fas fa-spinner fa-spin"></i><span>加载中...</span></div>';
        load();
    }

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
                <aside class="ws-sw-side">
                    <div class="ws-sw-side-top">
                        <div class="ws-sw-brand">
                            <div class="ws-sw-logo">${STAR_SVG}</div>
                            <div>
                                <div class="ws-sw-brand-name">Qualia Code</div>
                                <div class="ws-sw-brand-ver">AI 编程搭档</div>
                            </div>
                        </div>
                        <button class="ws-sw-close" title="关闭" onclick="closeWorkspaceSwitcher()"><i class="fas fa-times"></i></button>
                    </div>
                    <h2 class="ws-sw-side-title">打开项目</h2>
                    <div class="ws-sw-side-sub" id="wsSwSideSub">工作区是 Qualia Code 的活动范围，会话、文件树与技能都存放在这里</div>
                    <button class="ws-sw-open-btn" onclick="QWorkspace.showBrowse()">
                        <i class="fas fa-folder"></i><span>打开文件夹</span>
                    </button>
                    <div class="ws-sw-side-foot">点击项目即可直接打开<br/>历史记录保存于 ~/.qualia</div>
                </aside>
                <main class="ws-sw-pane">
                    <section class="ws-sw-view on" id="wsSwViewRecent">
                        <div class="ws-sw-rp-head">
                            <h3>最近打开<span class="ws-sw-rp-count" id="wsSwCount"></span></h3>
                            <label class="ws-sw-search">
                                <i class="fas fa-magnifying-glass"></i>
                                <input type="text" id="wsSwSearch" placeholder="搜索项目">
                            </label>
                        </div>
                        <div class="ws-sw-rp-list" id="wsSwRecentList">
                            <div class="ws-sw-empty"><i class="fas fa-spinner fa-spin"></i><span>加载中...</span></div>
                        </div>
                        <div class="ws-sw-hint" id="wsSwHint"></div>
                        <div class="ws-sw-rp-foot" id="wsSwRpFoot"></div>
                    </section>
                    <section class="ws-sw-view" id="wsSwViewBrowse">
                        <div class="ws-sw-br-head">
                            <button class="back-btn" title="返回" onclick="QWorkspace.backToMain()"><i class="fas fa-arrow-left"></i></button>
                            <div>
                                <h3>选择文件夹</h3>
                                <div class="sub">进入目标目录后点击「打开」，也可粘贴路径跳转</div>
                            </div>
                        </div>
                        <div class="ws-sw-addr-row">
                            <button class="nav-btn" id="wsSwBrUp" title="上级目录" onclick="QWorkspace.browseUp()"><i class="fas fa-arrow-up"></i></button>
                            <button class="nav-btn" title="用户主目录" onclick="QWorkspace.browseHome()"><i class="fas fa-house"></i></button>
                            <input type="text" id="wsSwAddr" placeholder="输入或粘贴目录路径，回车跳转" spellcheck="false">
                        </div>
                        <div class="ws-sw-drives" id="wsSwDrives"></div>
                        <div class="ws-sw-br-list" id="wsSwBrList"></div>
                        <div class="ws-sw-hint" id="wsSwBrHint"></div>
                        <div class="ws-sw-br-foot">
                            <div class="sel"><i class="fas fa-folder-open"></i><span class="p" id="wsSwBrSel"></span></div>
                            <button class="ghost-btn" onclick="QWorkspace.backToMain()">取消</button>
                            <button class="pick-btn" id="wsSwBrPick" onclick="QWorkspace.pickBrowsed()" disabled>打开</button>
                        </div>
                    </section>
                </main>
            </div>`;
        document.body.appendChild(overlay);

        overlay.addEventListener('click', function (e) {
            if (e.target === this) closeWorkspaceSwitcher();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            const open = document.getElementById('wsSwOverlay').classList.contains('open');
            if (!open) return;
            // 浏览视图下 Esc 先返回最近视图，再次 Esc 才关闭
            const browsing = document.querySelector('#wsSwOverlay .ws-sw-dialog.browsing');
            if (browsing) QWorkspace.backToMain();
            else closeWorkspaceSwitcher();
        });
        document.getElementById('wsSwSearch').addEventListener('input', e => filterRecent(e.target.value));
        document.getElementById('wsSwAddr').addEventListener('keydown', e => {
            if (e.key === 'Enter') browseTo(e.target.value.trim());
        });

        // 启动即拉取当前工作区：有则回填侧边栏；未绑定则强制弹出选择弹窗
        fetch('/api/workspace')
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (!data) return;
                if (!data.current) {
                    openForced();
                    return;
                }
                applyCurrentToSidebar(data.current);
            })
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
        setView(false);
        document.getElementById('wsSwRecentList').innerHTML =
            '<div class="ws-sw-empty"><i class="fas fa-spinner fa-spin"></i><span>加载中...</span></div>';
        load();
    };

    window.closeWorkspaceSwitcher = function () {
        // 强制模式下不允许关闭：只有选择成功后的整页重载能解除
        if (forced) return;
        document.getElementById('wsSwOverlay').classList.remove('open');
    };
})();
