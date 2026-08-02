/**
 * TokenHeatmap —— 侧边栏「近 30 日 Token 用量」热力矩阵组件（自包含：骨架 + 取数 + 样式自注入，依赖页面 CSS 变量）
 *
 * 职责：
 *   1. 拉取 /api/chat/stats/tokens?days=30，按当期最大值动态分 4 档渲染 10×3 方格矩阵
 *   2. hover 气泡展示「X月X日 · Y.Yk tokens」（自动 k/M 格式化，首末两列气泡贴边防溢出）
 *   3. 接口失败或无数据时整块保持隐藏，不破坏侧边栏布局
 *   4. 点击矩阵弹出统计详情面板：总量/日均/峰值/活跃天数四张卡片 + SVG 折线趋势图，支持近 30/60/90 日切换
 *
 * 视觉约定：品牌紫 4 档透明度梯度（lv4 实色 + 辉光），深浅主题经 CSS 变量自动适配
 *
 * 对外 API：
 *   TokenHeatmap.init(containerEl)   绑定容器并注入骨架，随即拉数据渲染
 *   TokenHeatmap.refresh()           重新拉数据刷新（供发消息后更新用量等场景调用）
 *   TokenHeatmap.openDetail()        打开统计详情面板（点击矩阵时自动调用）
 */
window.TokenHeatmap = (function () {
    'use strict';

    let container = null;

    function formatTokens(n) {
        if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
        if (n >= 1e3) return (n / 1e3).toFixed(1) + 'k';
        return String(n);
    }

    async function refresh() {
        if (!container) return;
        try {
            const resp = await fetch('/api/chat/stats/tokens?days=30');
            if (!resp.ok) return;
            const stats = await resp.json();
            if (!Array.isArray(stats) || !stats.length) return;

            const max = Math.max(...stats.map(d => d.tokens));
            container.querySelector('.th-grid').innerHTML = stats.map(d => {
                // 0 为 lv0，其余按最大值比例分 4 档
                let lv = 0;
                if (d.tokens > 0 && max > 0) {
                    const r = d.tokens / max;
                    lv = r > 0.75 ? 4 : r > 0.5 ? 3 : r > 0.25 ? 2 : 1;
                }
                const dt = new Date(d.date);
                const tip = `${dt.getMonth() + 1}月${dt.getDate()}日 · ${formatTokens(d.tokens)} tokens`;
                return `<span class="th-cell lv${lv}" data-tip="${tip}"></span>`;
            }).join('');
            container.style.display = 'block';
        } catch (e) {
            // 静默失败，不影响主界面
        }
    }

    function init(containerEl) {
        container = containerEl;
        if (!container) return;
        container.classList.add('token-heatmap');
        container.style.display = 'none';
        container.innerHTML = `
            <div class="th-title"><i class="fas fa-fire"></i> 近 30 日 Token 用量<i class="fas fa-chart-line th-more"></i></div>
            <div class="th-grid"></div>
        `;
        // 点击整块矩阵打开统计详情面板
        container.addEventListener('click', openDetail);
        refresh();
    }

    // ===== 统计详情面板：汇总卡片 + SVG 折线趋势图，近 30/60/90 日切换 =====
    // 折线图 viewBox 逻辑尺寸（实际宽度自适应，等比缩放）
    const VB_W = 640, VB_H = 230;
    const PAD = { l: 48, r: 18, t: 16, b: 28 };
    let currentDays = 30;
    // 当前折线各点的 viewBox 坐标与原始数据，hover 寻点用
    let chartPts = [];

    // y 轴最大值取整到 1/2/5×10^n，免得刻度出现碎数
    function niceMax(v) {
        if (v <= 0) return 1;
        const pow = Math.pow(10, Math.floor(Math.log10(v)));
        const r = v / pow;
        return (r <= 1 ? 1 : r <= 2 ? 2 : r <= 5 ? 5 : 10) * pow;
    }

    function buildChart(stats) {
        const innerW = VB_W - PAD.l - PAD.r;
        const innerH = VB_H - PAD.t - PAD.b;
        const maxV = niceMax(Math.max(...stats.map(d => d.tokens)));
        const n = stats.length;
        chartPts = stats.map((d, i) => ({
            x: PAD.l + (n > 1 ? i * innerW / (n - 1) : innerW / 2),
            y: PAD.t + (1 - d.tokens / maxV) * innerH,
            date: d.date, tokens: d.tokens
        }));
        const line = chartPts.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join('');
        const baseY = PAD.t + innerH;
        const area = `${line}L${chartPts[n - 1].x.toFixed(1)},${baseY}L${chartPts[0].x.toFixed(1)},${baseY}Z`;
        // 横向网格线 + y 轴刻度（0 到最大值均分 4 段）
        let grid = '';
        for (let i = 0; i <= 4; i++) {
            const y = PAD.t + innerH * i / 4;
            grid += `<line x1="${PAD.l}" y1="${y}" x2="${VB_W - PAD.r}" y2="${y}" class="thd-gridline"/>`
                + `<text x="${PAD.l - 8}" y="${y + 3.5}" class="thd-ytick">${formatTokens(maxV * (4 - i) / 4)}</text>`;
        }
        // x 轴日期刻度：首尾 + 中间均匀 3 个（去重防短周期重叠）
        let xticks = '';
        for (const i of [...new Set([0, (n - 1) >> 2, (n - 1) >> 1, Math.round((n - 1) * 3 / 4), n - 1])]) {
            const d = new Date(stats[i].date);
            xticks += `<text x="${chartPts[i].x}" y="${VB_H - 8}" class="thd-xtick">${d.getMonth() + 1}/${d.getDate()}</text>`;
        }
        return `
            <svg class="thd-svg" id="thdSvg" viewBox="0 0 ${VB_W} ${VB_H}">
                <defs>
                    <linearGradient id="thdFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stop-color="var(--accent, #7c6cf0)" stop-opacity="0.3"/>
                        <stop offset="100%" stop-color="var(--accent, #7c6cf0)" stop-opacity="0"/>
                    </linearGradient>
                </defs>
                ${grid}${xticks}
                <path d="${area}" fill="url(#thdFill)"/>
                <path d="${line}" fill="none" stroke="var(--accent, #7c6cf0)" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
                <line id="thdCursor" class="thd-cursor" y1="${PAD.t}" y2="${baseY}" visibility="hidden"/>
                <circle id="thdDot" class="thd-dot" r="3.5" visibility="hidden"/>
            </svg>
        `;
    }

    // hover 十字寻点：虺线游标 + 定位圆点 + 日期/用量气泡（气泡贴边限幅防溢出）
    function bindChartHover() {
        const svg = document.getElementById('thdSvg');
        const tip = document.getElementById('thdTip');
        if (!svg || !tip) return;
        const cursor = document.getElementById('thdCursor');
        const dot = document.getElementById('thdDot');
        svg.addEventListener('mousemove', e => {
            if (!chartPts.length) return;
            const rect = svg.getBoundingClientRect();
            const vx = (e.clientX - rect.left) / rect.width * VB_W;
            let best = 0, bd = Infinity;
            chartPts.forEach((p, i) => {
                const d = Math.abs(p.x - vx);
                if (d < bd) { bd = d; best = i; }
            });
            const p = chartPts[best];
            cursor.setAttribute('x1', p.x);
            cursor.setAttribute('x2', p.x);
            cursor.setAttribute('visibility', 'visible');
            dot.setAttribute('cx', p.x);
            dot.setAttribute('cy', p.y);
            dot.setAttribute('visibility', 'visible');
            const dt = new Date(p.date);
            tip.textContent = `${dt.getMonth() + 1}月${dt.getDate()}日 · ${formatTokens(p.tokens)} tokens`;
            const px = Math.max(60, Math.min(p.x / VB_W * rect.width, rect.width - 60));
            tip.style.left = px + 'px';
            tip.style.top = (p.y / VB_H * rect.height) + 'px';
            tip.style.display = 'block';
        });
        svg.addEventListener('mouseleave', () => {
            cursor.setAttribute('visibility', 'hidden');
            dot.setAttribute('visibility', 'hidden');
            tip.style.display = 'none';
        });
    }

    async function renderDetail() {
        const body = document.getElementById('thdBody');
        if (!body) return;
        let stats = [];
        try {
            const resp = await fetch(`/api/chat/stats/tokens?days=${currentDays}`);
            if (resp.ok) stats = await resp.json();
        } catch (e) { /* 静默失败，下方统一走空态 */ }
        if (!Array.isArray(stats) || !stats.length) {
            body.innerHTML = '<div class="thd-empty">暂无用量数据</div>';
            return;
        }
        const total = stats.reduce((s, d) => s + d.tokens, 0);
        const active = stats.filter(d => d.tokens > 0).length;
        const peak = stats.reduce((a, b) => (b.tokens > a.tokens ? b : a), stats[0]);
        const peakDt = new Date(peak.date);
        const cards = [
            { num: formatTokens(total), lbl: '总用量' },
            { num: formatTokens(Math.round(total / stats.length)), lbl: '日均用量' },
            { num: formatTokens(peak.tokens), lbl: `单日峰值 · ${peakDt.getMonth() + 1}/${peakDt.getDate()}` },
            { num: `${active}/${stats.length}`, lbl: '活跃天数' }
        ].map(c => `<div class="thd-card"><div class="num">${c.num}</div><div class="lbl">${c.lbl}</div></div>`).join('');
        body.innerHTML = `<div class="thd-cards">${cards}</div>`
            + `<div class="thd-chart">${buildChart(stats)}<div class="thd-tip" id="thdTip"></div></div>`;
        bindChartHover();
    }

    // 首次打开时创建弹窗并绑定交互（遮罩点击/Esc 关闭，范围切换重拉数据）
    function ensureOverlay() {
        if (document.getElementById('thdOverlay')) return;
        const overlay = document.createElement('div');
        overlay.className = 'thd-overlay';
        overlay.id = 'thdOverlay';
        overlay.innerHTML = `
            <div class="thd-dialog">
                <div class="thd-head">
                    <h4><i class="fas fa-chart-line"></i> Token 用量统计</h4>
                    <div class="thd-range" id="thdRange">
                        <button data-days="30" class="active">近 30 日</button>
                        <button data-days="60">近 60 日</button>
                        <button data-days="90">近 90 日</button>
                    </div>
                    <button class="thd-close" title="关闭"><i class="fas fa-times"></i></button>
                </div>
                <div id="thdBody"></div>
            </div>
        `;
        document.body.appendChild(overlay);
        overlay.addEventListener('click', e => { if (e.target === overlay) closeDetail(); });
        overlay.querySelector('.thd-close').addEventListener('click', closeDetail);
        document.getElementById('thdRange').addEventListener('click', e => {
            const btn = e.target.closest('button[data-days]');
            if (!btn) return;
            overlay.querySelectorAll('#thdRange button').forEach(b => b.classList.toggle('active', b === btn));
            currentDays = Number(btn.dataset.days);
            renderDetail();
        });
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && overlay.classList.contains('open')) closeDetail();
        });
    }

    function openDetail() {
        ensureOverlay();
        document.getElementById('thdOverlay').classList.add('open');
        renderDetail();
    }

    function closeDetail() {
        const overlay = document.getElementById('thdOverlay');
        if (overlay) overlay.classList.remove('open');
    }

    // ===== 样式自注入（依赖页面 CSS 变量）=====
    const STYLE = `
        /* 近 30 日 token 用量热力矩阵（GitHub 贡献图风格） */
        .token-heatmap {
            flex-shrink: 0;
            padding: 11px 4px 2px;
            border-top: 1px solid var(--border-color);
            margin-bottom: 9px;
        }
        .th-title {
            font-size: 10.5px;
            font-weight: 600;
            color: var(--text-muted);
            margin-bottom: 7px;
            display: flex;
            align-items: center;
            gap: 5px;
        }
        .th-title i {
            font-size: 9.5px;
            color: var(--accent-light);
        }
        .th-grid {
            display: grid;
            grid-template-columns: repeat(10, 1fr);
            gap: 4px;
        }
        .th-cell {
            aspect-ratio: 1;
            width: 100%;
            border-radius: 3px;
            background: var(--bg-hover);
            position: relative;
            transition: transform 0.12s, background 0.3s;
        }
        .th-grid .th-cell:hover {
            transform: scale(1.25);
            outline: 1px solid var(--accent-light);
            z-index: 2;
        }
        .th-cell.lv4 { box-shadow: 0 0 6px rgba(124, 108, 240, 0.45); }
        .th-cell.lv1 { background: rgba(124, 108, 240, 0.28); }
        .th-cell.lv2 { background: rgba(124, 108, 240, 0.5); }
        .th-cell.lv3 { background: rgba(124, 108, 240, 0.75); }
        .th-cell.lv4 { background: var(--accent); }
        /* hover 气泡：日期 + token 数 */
        .th-grid .th-cell::after {
            content: attr(data-tip);
            position: absolute;
            bottom: calc(100% + 5px);
            left: 50%;
            transform: translateX(-50%);
            padding: 4px 7px;
            border-radius: 5px;
            background: var(--bg-app);
            border: 1px solid var(--border-color);
            color: var(--text-primary);
            font-size: 10.5px;
            white-space: nowrap;
            pointer-events: none;
            opacity: 0;
            transition: opacity 0.15s;
            z-index: 10;
        }
        .th-grid .th-cell:hover::after {
            opacity: 1;
        }
        /* 前两列的气泡左对齐、末两列右对齐，避免溢出侧边栏 */
        .th-grid .th-cell:nth-child(10n + 1)::after,
        .th-grid .th-cell:nth-child(10n + 2)::after {
            left: 0;
            transform: none;
        }
        .th-grid .th-cell:nth-child(10n)::after,
        .th-grid .th-cell:nth-child(10n + 9)::after {
            left: auto;
            right: 0;
            transform: none;
        }
        .th-legend {
            display: flex;
            align-items: center;
            gap: 4px;
            justify-content: flex-end;
            margin-top: 7px;
            font-size: 9.5px;
            color: var(--text-muted);
        }
        .th-legend .th-cell {
            width: 8px;
            height: 8px;
            aspect-ratio: auto;
        }
        /* 整块可点击打开统计详情；标题右侧折线图标 hover 时点亮作为入口提示 */
        .token-heatmap { cursor: pointer; }
        .th-title .th-more {
            margin-left: auto;
            opacity: 0;
            transition: opacity 0.15s;
        }
        .token-heatmap:hover .th-more { opacity: 1; }

        /* ===== 统计详情面板（遮罩 + 卡片 + 折线图，尺寸规格对齐设置弹窗）===== */
        .thd-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.55);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
        }
        .thd-overlay.open { display: flex; }
        .thd-dialog {
            width: min(684px, calc(100vw - 48px));
            background: var(--bg-surface);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            box-shadow: var(--shadow);
            padding: 14px 18px 18px;
        }
        .thd-head {
            display: flex;
            align-items: center;
            gap: 11px;
            margin-bottom: 13px;
        }
        .thd-head h4 {
            font-size: 12.5px;
            font-weight: 600;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 7px;
            margin-right: auto;
        }
        .thd-head h4 i { color: var(--accent-light); font-size: 11.5px; }
        .thd-range {
            display: flex;
            gap: 3px;
            background: var(--bg-hover);
            padding: 3px;
            border-radius: 7px;
        }
        .thd-range button {
            border: none;
            background: transparent;
            color: var(--text-muted);
            font-size: 10.5px;
            padding: 4px 9px;
            border-radius: 5px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .thd-range button:hover { color: var(--text-primary); }
        .thd-range button.active {
            background: var(--bg-active);
            color: var(--accent-light);
            font-weight: 600;
        }
        .thd-close {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 12.5px;
            padding: 4px 7px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .thd-close:hover {
            background: var(--bg-hover);
            color: var(--text-primary);
        }
        /* 汇总卡片 */
        .thd-cards {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 9px;
            margin-bottom: 13px;
        }
        .thd-card {
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 9px;
            padding: 9px 11px;
        }
        .thd-card .num {
            font-family: 'JetBrains Mono', monospace;
            font-size: 15px;
            font-weight: 700;
            color: var(--text-primary);
        }
        .thd-card .lbl {
            font-size: 10.5px;
            color: var(--text-muted);
            margin-top: 3px;
        }
        /* 折线图 */
        .thd-chart { position: relative; }
        .thd-svg { display: block; width: 100%; height: auto; }
        .thd-gridline { stroke: var(--border-color); stroke-width: 1; }
        .thd-ytick { fill: var(--text-muted); font-size: 10px; text-anchor: end; }
        .thd-xtick { fill: var(--text-muted); font-size: 10px; text-anchor: middle; }
        .thd-cursor { stroke: var(--border-active); stroke-dasharray: 3 3; }
        .thd-dot { fill: var(--accent); stroke: var(--bg-surface); stroke-width: 2; }
        .thd-tip {
            position: absolute;
            display: none;
            transform: translate(-50%, -140%);
            padding: 4px 7px;
            border-radius: 5px;
            background: var(--bg-app);
            border: 1px solid var(--border-color);
            color: var(--text-primary);
            font-size: 10.5px;
            white-space: nowrap;
            pointer-events: none;
            z-index: 5;
        }
        .thd-empty {
            text-align: center;
            color: var(--text-muted);
            font-size: 11px;
            padding: 43px 0;
        }
    `;

    function injectStyle() {
        if (document.getElementById('token-heatmap-style')) return;
        const style = document.createElement('style');
        style.id = 'token-heatmap-style';
        style.textContent = STYLE;
        document.head.appendChild(style);
    }
    injectStyle();

    return { init, refresh, openDetail };
})();
