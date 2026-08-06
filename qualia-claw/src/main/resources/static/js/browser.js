/**
 * browser.js（浏览器）—— tool chip 联网类工具详情渲染，合并自 web-search-detail.js 与 web-fetch-detail.js
 *
 * 被 js/tool-chip.js 引用，对外暴露两个 API（保持原名，调用方无需改动）：
 *
 * window.WebSearchDetail —— 网页搜索详情（紧凑列表式）
 *   matches(toolName)                 是否为网页搜索工具
 *   meta()                            chip 摘要元信息（地球图标 / 联网搜索 / query 摘要）
 *   render(toolName, args, output)    详情 HTML，output 为 null 表示执行中
 *   openAll(id)                       在工作区「浏览器」Tab 展示全部结果（依赖 window.openHtmlPreview）
 *
 * window.WebFetchDetail —— 网页抓取（web_fetch）详情
 *   matches(toolName)                 是否为 web_fetch
 *   meta()                            chip 摘要元信息（📄 抓取网页 · 域名）
 *   render(toolName, args, output)    详情 HTML，output 为 null 表示执行中
 *   openSource(id)                    在工作区「浏览器」Tab 加载源网页（依赖 window.openUrlPreview）
 *   openReader(id)                    在工作区「浏览器」Tab 打开阅读模式页（依赖 window.openHtmlPreview）
 *
 * 搜索解析器兼容两种后端输出：
 *   - 拼接文本（baidu/bing/google/duckduckgo）："N. 标题 / 链接: URL / 摘要: ..."
 *   - Tavily JSON：{source, query, count, items:[{title,url,content,score}], response_time}
 * 抓取后端输出为 JSON：{url, status, content_type, title?, content, extracted_length, error?, message?}
 * 解析失败均降级为 tc-code 纯文本块。
 *
 * 浏览器 Tab 内页（搜索结果页 / 阅读模式页）内嵌明暗两套 CSS 变量，
 * 父页切主题时通过 postMessage({type:'qualia-theme'}) 实时切换。
 *
 * 样式自注入，仅新增 tc-web-* / tc-fetch-* 类；tc-badge / tc-code / tc-running 复用 tool-chip.js 已有样式。
 */
(function () {
    'use strict';

    // ===== 共享工具函数 =====
    const OUT_LIMIT = 4000;

    function esc(s) {
        const div = document.createElement('div');
        div.textContent = String(s == null ? '' : s);
        return div.innerHTML;
    }

    function truncate(s, n) {
        s = String(s || '');
        return s.length > n ? s.slice(0, n) + '…' : s;
    }

    function displayUrl(url) {
        return String(url || '').replace(/^https?:\/\//i, '').replace(/\/$/, '');
    }

    function domainOf(url) {
        const m = String(url || '').match(/^(?:https?:\/\/)?([^/?#]+)/i);
        return m ? m[1] : String(url || '');
    }

    function codeBlock(text) {
        return `<div class="tc-code">${esc(truncate(text, OUT_LIMIT))}</div>`;
    }

    // =====================================================================
    // 网页搜索详情（window.WebSearchDetail）
    // =====================================================================
    window.WebSearchDetail = (function () {

        // 工具名 -> 来源徽章文案
        const TOOLS = {
            baidu_search: 'baidu',
            bing_search: 'bing',
            google_search: 'google',
            duckduckgo_search: 'duckduckgo',
            tavily_search: 'tavily'
        };
        // chip 详情默认展示条数，其余通过「查看全部」在工作区预览
        const LIST_LIMIT = 3;
        // 「查看全部」用的结果暂存（下标即 openAll 的 id）
        const STORE = [];

        function matches(toolName) {
            return Object.prototype.hasOwnProperty.call(TOOLS, toolName);
        }

        function meta() {
            return { icon: 'fa-globe', verb: '联网搜索', arg: a => truncate(a.query || '', 36) };
        }

        // ===== 输出解析：成功返回 {items:[{title,url,snippet}], time?}，失败返回 null =====
        function parse(output) {
            const t = String(output || '').trim();
            if (!t) return null;
            return t.startsWith('{') ? parseTavily(t) : parseText(t);
        }

        // 拼接文本格式；行首缩进容忍，摘要支持多行续写
        function parseText(t) {
            const items = [];
            let cur = null;
            let inSnippet = false;
            for (const raw of t.split('\n')) {
                const line = raw.trim();
                let m;
                if ((m = line.match(/^(\d+)\.\s+(.+)$/))) {
                    cur = { title: m[2], url: '', snippet: '' };
                    items.push(cur);
                    inSnippet = false;
                } else if (cur && (m = line.match(/^链接[:：]\s*(.*)$/))) {
                    cur.url = m[1];
                    inSnippet = false;
                } else if (cur && (m = line.match(/^摘要[:：]\s*(.*)$/))) {
                    cur.snippet = m[1];
                    inSnippet = true;
                } else if (cur && inSnippet && line) {
                    cur.snippet += ' ' + line;
                }
            }
            return items.length ? { items } : null;
        }

        // Tavily JSON 格式；error=true 或空 items 走降级
        function parseTavily(t) {
            try {
                const o = JSON.parse(t);
                if (o.error) return null;
                const arr = Array.isArray(o.items) ? o.items : [];
                const items = arr.map(it => ({
                    title: it.title || it.url || '',
                    url: it.url || '',
                    snippet: it.content || it.snippet || ''
                }));
                if (!items.length) return null;
                const r = { items };
                if (o.response_time != null) r.time = Number(o.response_time).toFixed(1) + 's';
                return r;
            } catch (e) {
                return null;
            }
        }

        // ===== 渲染片段 =====
        function head(query, badges) {
            const badgeHtml = (badges || []).filter(Boolean)
                .map(b => `<span class="tc-badge">${esc(b)}</span>`).join('');
            return `
                <div class="tc-web-head">
                    <i class="fas fa-globe"></i>
                    <span class="tc-web-query">${esc(query || '')}</span>
                    ${badgeHtml}
                </div>
            `;
        }

        function itemHtml(it) {
            const href = it.url ? (/^https?:\/\//i.test(it.url) ? it.url : 'https://' + it.url) : '';
            return `
                <a class="tc-web-item"${href ? ` href="${esc(href)}" target="_blank" rel="noopener"` : ''}>
                    <span class="tc-web-main">
                        <span class="tc-web-title">${esc(it.title)}</span>
                        ${it.url ? `<span class="tc-web-url">${esc(displayUrl(it.url))}</span>` : ''}
                        ${it.snippet ? `<span class="tc-web-snippet">${esc(it.snippet)}</span>` : ''}
                    </span>
                    <i class="fas fa-arrow-up-right-from-square tc-web-ext"></i>
                </a>
            `;
        }

        function render(toolName, args, output) {
            const source = TOOLS[toolName];
            if (output === null) {
                return head(args.query, [source])
                    + `<div class="tc-running"><i class="fas fa-circle-notch fa-spin"></i><span>搜索中…</span></div>`;
            }
            const parsed = parse(output);
            if (!parsed) {
                // 错误 / 未找到 / 解析失败：查询头 + 纯文本降级
                return head(args.query, [source]) + codeBlock(output || '（无输出）');
            }
            const total = parsed.items.length;
            const badges = [source, total + ' 条'];
            if (parsed.time) badges.push(parsed.time);
            const shown = parsed.items.slice(0, LIST_LIMIT);
            let moreHtml = '';
            if (total > LIST_LIMIT) {
                const id = STORE.push({ query: args.query || '', source, items: parsed.items, time: parsed.time }) - 1;
                moreHtml = `<button class="tc-web-more" onclick="WebSearchDetail.openAll(${id})">`
                    + `<i class="fas fa-angles-right"></i> 查看全部 ${total} 条结果</button>`;
            }
            return head(args.query, badges)
                + `<div class="tc-web-list">${shown.map(itemHtml).join('')}</div>`
                + moreHtml;
        }

        // ===== 「查看全部」：生成完整结果页，交给工作区「浏览器」Tab =====
        function openAll(id) {
            const rec = STORE[id];
            if (!rec || typeof window.openHtmlPreview !== 'function') return;
            window.openHtmlPreview(buildPage(rec));
        }

        // 自包含 HTML 页面，配色跟随当前明暗主题
        // 结构：sticky 查询头 + 站点行（字母头像/站点名/URL）+ 标题 + 两行摘要，通栏分隔线，尾部尽头提示
        const AVATAR_COLORS = ['#6366f1', '#f59e0b', '#ef4444', '#0ea5e9', '#8b5cf6', '#10b981', '#ec4899', '#14b8a6'];

        // 域名哈希取色，同一站点颜色稳定
        function avatarColor(domain) {
            let h = 0;
            for (let i = 0; i < domain.length; i++) h = (h * 31 + domain.charCodeAt(i)) >>> 0;
            return AVATAR_COLORS[h % AVATAR_COLORS.length];
        }

        function siteName(url) {
            return domainOf(url).replace(/^www\./i, '');
        }

        function buildPage(rec) {
            const light = document.body.classList.contains('light-theme');
            const rows = rec.items.map(it => {
                const href = it.url ? (/^https?:\/\//i.test(it.url) ? it.url : 'https://' + it.url) : '';
                const site = siteName(it.url || '');
                const siteRow = site ? `
                        <span class="site-row">
                            <span class="avatar" style="background:${avatarColor(site)}">${esc(site.charAt(0).toUpperCase())}</span>
                            <span class="site-name">${esc(site)}</span>
                            <span class="site-url">${esc(displayUrl(it.url))}</span>
                        </span>` : '';
                return `
                    <a class="item"${href ? ` href="${esc(href)}" target="_blank" rel="noopener"` : ''}>${siteRow}
                        <span class="title">${esc(it.title)}</span>
                        ${it.snippet ? `<span class="snippet">${esc(it.snippet)}</span>` : ''}
                    </a>
                `;
            }).join('');
            const metaParts = [`<span class="src">${esc(rec.source || '')}</span>`, `<span>${rec.items.length} 条结果</span>`]
                .concat(rec.time ? [`<span>${esc(rec.time)}</span>`] : [])
                .join('<span class="sep">·</span>');
            return `<!DOCTYPE html>
<html lang="zh-CN"${light ? ' class="light"' : ''}>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
    /* 两套主题变量，取值对齐主应用色板（index.css），父页切换时通过 postMessage 实时切换 body.light */
    :root {
        color-scheme: dark;
        --bg: #0d0f16; --text: #e4e9f2; --sub: #8f97ae; --muted: #4f566b;
        --url: #7ddfb0; --border: rgba(255, 255, 255, 0.06); --hover: rgba(255, 255, 255, 0.04);
        --accent: #b3a8ff; --icon-bg: rgba(124, 108, 240, 0.14); --scrollbar: #2e3546;
    }
    /* 主题类同时挂在 html 与 body 上：视口滚动条属于 html，变量必须在 html 层级生效 */
    .light {
        color-scheme: light;
        --bg: #ffffff; --text: #1e232e; --sub: #5f6883; --muted: #8f97ae;
        --url: #0f7b4f; --border: rgba(0, 0, 0, 0.07); --hover: rgba(0, 0, 0, 0.04);
        --accent: #7c6cf0; --icon-bg: rgba(124, 108, 240, 0.10); --scrollbar: #cbd0db;
    }
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html { background: var(--bg); }
    ::-webkit-scrollbar { width: 5px; height: 5px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: var(--scrollbar); border-radius: 8px; }
    body { background: var(--bg); color: var(--text); font-family: -apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif; transition: background 0.2s; }
    /* sticky 查询头 */
    .search-head { position: sticky; top: 0; background: var(--bg); padding: 18px 28px 0; z-index: 10; }
    .search-head-inner { max-width: 680px; margin: 0 auto; padding-bottom: 14px; border-bottom: 1px solid var(--border); }
    .query-row { display: flex; align-items: center; gap: 10px; }
    .query-icon { width: 30px; height: 30px; border-radius: 8px; background: var(--icon-bg); display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0; }
    .query-text { font-size: 17px; font-weight: 700; letter-spacing: 0.2px; }
    .meta-row { display: flex; align-items: center; gap: 6px; margin-top: 8px; padding-left: 40px; font-size: 11px; color: var(--muted); }
    .meta-row .sep { color: var(--border); }
    .meta-row .src { color: var(--accent); font-weight: 600; }
    /* 结果列表：无序号无卡片框，通栏分隔线 */
    .results { max-width: 680px; margin: 0 auto; padding: 4px 28px 24px; }
    .item { display: block; padding: 16px 12px; margin: 0 -12px; border-radius: 10px; text-decoration: none; transition: background 0.15s; }
    .item + .item { border-top: 1px solid var(--border); }
    .item:hover { background: var(--hover); }
    .site-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; min-width: 0; }
    .avatar { width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 700; color: #fff; flex-shrink: 0; }
    .site-name { font-size: 12px; color: var(--text); font-weight: 500; flex-shrink: 0; }
    .site-url { font-family: Consolas, monospace; font-size: 11px; color: var(--url); opacity: 0.8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .title { display: block; font-size: 15px; font-weight: 600; color: var(--text); line-height: 1.5; margin-bottom: 4px; }
    .item:hover .title { color: var(--accent); text-decoration: underline; text-underline-offset: 3px; }
    .snippet { font-size: 12.5px; line-height: 1.7; color: var(--sub); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    /* 尽头提示 */
    .end-tip { max-width: 680px; margin: 0 auto; padding: 0 28px 40px; text-align: center; font-size: 11.5px; color: var(--muted); }
    .end-tip::before { content: '— '; color: var(--border); }
    .end-tip::after { content: ' —'; color: var(--border); }
</style>
</head>
<body${light ? ' class="light"' : ''}>
    <div class="search-head">
        <div class="search-head-inner">
            <div class="query-row"><span class="query-icon">🌐</span><span class="query-text">${esc(rec.query)}</span></div>
            <div class="meta-row">${metaParts}</div>
        </div>
    </div>
    <div class="results">${rows}</div>
    <div class="end-tip">已展示全部 ${rec.items.length} 条结果</div>
<script>
    window.addEventListener('message', function (e) {
        if (e.data && e.data.type === 'qualia-theme') {
            document.documentElement.classList.toggle('light', !!e.data.light);
            document.body.classList.toggle('light', !!e.data.light);
        }
    });
</script>
</body>
</html>`;
        }

        return { matches, meta, render, openAll };
    })();

    // =====================================================================
    // 网页抓取详情（window.WebFetchDetail）
    // =====================================================================
    window.WebFetchDetail = (function () {

        // 正文预览截取长度（详情卡片内不可滚动，完整阅读走浏览器 Tab）
        const PREVIEW_CHARS = 600;
        // 「阅读全文」用的结果暂存（下标即 openReader 的 id）
        const STORE = [];

        function matches(toolName) {
            return toolName === 'web_fetch';
        }

        function meta() {
            return { icon: 'fa-file-lines', verb: '抓取网页', arg: a => truncate(domainOf(a.url), 36) };
        }

        // ===== 输出解析：成功返回结果对象，失败返回 null =====
        function parse(output) {
            const t = String(output || '').trim();
            if (!t.startsWith('{')) return null;
            try {
                return JSON.parse(t);
            } catch (e) {
                return null;
            }
        }

        // content-type 去掉 charset 等参数，只留主类型
        function shortType(ct) {
            return String(ct || '').split(';')[0].trim();
        }

        // ===== 渲染片段 =====
        function head(title, url, badges) {
            const badgeHtml = (badges || []).filter(Boolean).join('');
            const href = url ? (/^https?:\/\//i.test(url) ? url : 'https://' + url) : '';
            return `
                <div class="tc-fetch-head">
                    <div class="tc-fetch-title-row">
                        <i class="fas fa-file-lines"></i>
                        <span class="tc-fetch-title">${esc(title || displayUrl(url))}</span>
                        ${badgeHtml}
                    </div>
                    ${href ? `<a class="tc-fetch-url" href="${esc(href)}" target="_blank" rel="noopener">${esc(displayUrl(url))}</a>` : ''}
                </div>
            `;
        }

        function badge(text, cls) {
            return `<span class="tc-badge${cls ? ' ' + cls : ''}">${esc(text)}</span>`;
        }

        // 正文预览：截前 PREVIEW_CHARS 字符，换行压成空格，作为连续一段展示（不分段）
        function previewHtml(content) {
            const text = String(content).slice(0, PREVIEW_CHARS).replace(/\s+/g, ' ').trim();
            return `<div class="tc-fetch-preview">${esc(text)}</div>`;
        }

        function render(toolName, args, output) {
            const url = args.url || '';
            if (output === null) {
                return head('', url, [])
                    + `<div class="tc-running"><i class="fas fa-circle-notch fa-spin"></i><span>抓取中…</span></div>`;
            }
            const r = parse(output);
            if (!r) {
                // 非 JSON 输出：查询头 + 纯文本降级
                return head('', url, []) + codeBlock(output || '（无输出）');
            }
            if (r.error || r.status >= 400) {
                const badges = r.status ? [badge(r.status, 'err')] : [];
                return head(r.title, r.url || url, badges)
                    + `<div class="tc-fetch-error"><i class="fas fa-triangle-exclamation"></i><span>${esc(r.message || '抓取失败')}</span></div>`;
            }
            const content = String(r.content || '');
            const badges = [
                r.status ? badge(r.status, 'ok') : '',
                r.content_type ? badge(shortType(r.content_type)) : '',
                r.extracted_length ? badge(r.extracted_length.toLocaleString() + ' 字') : ''
            ];
            if (!content.trim()) {
                return head(r.title, r.url || url, badges) + codeBlock(r.message || '网页内容为空');
            }
            const id = STORE.push({
                title: r.title || '', url: r.url || url, content: content,
                type: shortType(r.content_type), length: r.extracted_length || content.length
            }) - 1;
            // SPA 等场景后端会附带说明，以提示条展示
            const hint = r.message ? `<div class="tc-fetch-hint"><i class="fas fa-circle-info"></i><span>${esc(r.message)}</span></div>` : '';
            return head(r.title, r.url || url, badges)
                + hint
                + previewHtml(content)
                + `<div class="tc-fetch-actions">`
                + `<button class="tc-fetch-open" onclick="WebFetchDetail.openSource(${id})">`
                + `<i class="fas fa-globe"></i> 在浏览器中打开源网页</button>`
                + `<button class="tc-fetch-open" onclick="WebFetchDetail.openReader(${id})" title="站点禁止嵌入时用阅读模式查看提取正文">`
                + `<i class="fas fa-book-open"></i> 阅读模式</button>`
                + `</div>`;
        }

        // ===== 「源网页」：浏览器 Tab 直接加载原页（SPA 也能完整渲染；禁止嵌入的站点会空白，改用阅读模式）=====
        function openSource(id) {
            const rec = STORE[id];
            if (!rec || typeof window.openUrlPreview !== 'function') return;
            const href = rec.url ? (/^https?:\/\//i.test(rec.url) ? rec.url : 'https://' + rec.url) : '';
            if (href) window.openUrlPreview(href);
        }

        // ===== 「阅读全文」：生成阅读模式页，交给工作区「浏览器」Tab =====
        function openReader(id) {
            const rec = STORE[id];
            if (!rec || typeof window.openHtmlPreview !== 'function') return;
            window.openHtmlPreview(buildReader(rec));
        }

        // 自包含阅读页，内嵌明暗两套变量，父页切主题时通过 postMessage 实时切换（与搜索结果页同一套机制）
        function buildReader(rec) {
            const light = document.body.classList.contains('light-theme');
            const href = rec.url ? (/^https?:\/\//i.test(rec.url) ? rec.url : 'https://' + rec.url) : '';
            const paras = rec.content.split(/\n{2,}/)
                .map(p => p.trim()).filter(Boolean)
                .map(p => `<p>${esc(p).replace(/\n/g, '<br>')}</p>`).join('');
            const metaParts = [
                href ? `<a href="${esc(href)}" target="_blank" rel="noopener">🔗 ${esc(displayUrl(rec.url))}</a>` : '',
                `<span>${rec.length.toLocaleString()} 字</span>`,
                rec.type ? `<span>${esc(rec.type)}</span>` : ''
            ].filter(Boolean).join('<span class="dot">·</span>');
            return `<!DOCTYPE html>
<html lang="zh-CN"${light ? ' class="light"' : ''}>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
    :root { color-scheme: dark; --bg: #0d0f16; --text: #e4e9f2; --sub: #8f97ae; --url: #7ddfb0; --border: rgba(255, 255, 255, 0.06); --scrollbar: #2e3546; }
    .light { color-scheme: light; --bg: #ffffff; --text: #1e232e; --sub: #5f6883; --url: #0f7b4f; --border: rgba(0, 0, 0, 0.07); --scrollbar: #cbd0db; }
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html { background: var(--bg); }
    ::-webkit-scrollbar { width: 5px; height: 5px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: var(--scrollbar); border-radius: 8px; }
    body { background: var(--bg); color: var(--text); font-family: -apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif; padding: 26px 32px 40px; transition: background 0.2s; }
    .wrap { max-width: 720px; margin: 0 auto; }
    h1 { font-size: 19px; font-weight: 700; line-height: 1.4; margin-bottom: 8px; }
    .meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 11px; color: var(--sub); margin-bottom: 20px; padding-bottom: 14px; border-bottom: 1px solid var(--border); }
    .meta a { color: var(--url); text-decoration: none; font-family: Consolas, monospace; word-break: break-all; }
    .meta a:hover { text-decoration: underline; }
    .meta .dot { color: var(--border); }
    p { font-size: 13.5px; line-height: 1.85; color: var(--sub); margin-bottom: 14px; }
    p:first-of-type { color: var(--text); }
</style>
</head>
<body${light ? ' class="light"' : ''}>
    <div class="wrap">
        <h1>${esc(rec.title || displayUrl(rec.url))}</h1>
        <div class="meta">${metaParts}</div>
        ${paras}
    </div>
<script>
    window.addEventListener('message', function (e) {
        if (e.data && e.data.type === 'qualia-theme') {
            document.documentElement.classList.toggle('light', !!e.data.light);
            document.body.classList.toggle('light', !!e.data.light);
        }
    });
</script>
</body>
</html>`;
        }

        return { matches, meta, render, openSource, openReader };
    })();

    // ===== 样式自注入（依赖页面 CSS 变量；tc-badge 等复用 tool-chip.js）=====
    const STYLE = `
        /* ---------- 网页搜索（tc-web-*） ---------- */
        /* 查询头 */
        .tc-web-head {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
            min-width: 0;
        }
        .tc-web-head .fa-globe { font-size: 11px; color: var(--accent-light); }
        .tc-web-query {
            font-family: 'JetBrains Mono', monospace;
            font-size: 12px;
            font-weight: 600;
            color: var(--text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        /* 紧凑列表 */
        .tc-web-list {
            display: flex;
            flex-direction: column;
            max-height: 320px;
            overflow-y: auto;
        }
        .tc-web-list::-webkit-scrollbar { width: 5px; }
        .tc-web-list::-webkit-scrollbar-thumb { background: var(--border-active); border-radius: 4px; }
        .tc-web-item {
            display: flex;
            gap: 10px;
            padding: 11px 12px;
            text-decoration: none;
            transition: background 0.15s;
        }
        .tc-web-item:hover { background: var(--bg-hover); }
        .tc-web-item + .tc-web-item { border-top: 1px solid var(--border-color); }
        .tc-web-main { min-width: 0; flex: 1; }
        .tc-web-title {
            display: block;
            font-size: 12.5px;
            font-weight: 600;
            color: var(--text-primary);
            line-height: 1.45;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .tc-web-item:hover .tc-web-title { color: var(--accent-light); }
        .tc-web-url {
            display: block;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            color: #7ddfb0;
            margin: 2px 0 3px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            opacity: 0.85;
        }
        body.light-theme .tc-web-url { color: #0f7b4f; }
        .tc-web-snippet {
            font-size: 11.5px;
            line-height: 1.55;
            color: var(--text-secondary);
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .tc-web-ext {
            flex-shrink: 0;
            align-self: center;
            font-size: 10px;
            color: var(--text-muted);
            opacity: 0;
            transition: opacity 0.15s;
        }
        .tc-web-item:hover .tc-web-ext { opacity: 1; }

        /* 查看全部按钮 */
        .tc-web-more {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            width: 100%;
            margin-top: 6px;
            padding: 7px 0;
            border: 1px dashed var(--border-color);
            border-radius: 8px;
            background: transparent;
            color: var(--text-muted);
            font-size: 11.5px;
            cursor: pointer;
            transition: color 0.15s, border-color 0.15s, background 0.15s;
        }
        .tc-web-more:hover {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-hover);
        }
        .tc-web-more .fa-angles-right { font-size: 10px; }

        /* ---------- 网页抓取（tc-fetch-*） ---------- */
        /* 详情头：标题 + URL + 徽章 */
        .tc-fetch-head { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
        .tc-fetch-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
        .tc-fetch-title-row .fa-file-lines { font-size: 11px; color: var(--accent-light); }
        .tc-fetch-title {
            font-size: 12.5px;
            font-weight: 600;
            color: var(--text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            max-width: 380px;
        }
        .tc-fetch-url {
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            color: #7ddfb0;
            text-decoration: none;
            opacity: 0.9;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            padding-left: 19px;
        }
        body.light-theme .tc-fetch-url { color: #0f7b4f; }
        .tc-fetch-url:hover { text-decoration: underline; }
        .tc-badge.ok { color: #34d399; border-color: rgba(52, 211, 153, 0.25); }
        body.light-theme .tc-badge.ok { color: #059669; border-color: rgba(5, 150, 105, 0.25); }
        .tc-badge.err { color: #f87171; border-color: rgba(248, 113, 113, 0.3); }
        body.light-theme .tc-badge.err { color: #dc2626; border-color: rgba(220, 38, 38, 0.3); }

        /* 正文预览：连续一段不分段，固定高 + 底部渐隐，不可滚动 */
        .tc-fetch-preview {
            position: relative;
            font-size: 11.5px;
            line-height: 1.7;
            color: var(--text-secondary);
            background: var(--bg-codeblock);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 10px 12px;
            max-height: 96px;
            overflow: hidden;
        }
        .tc-fetch-preview::after {
            content: '';
            position: absolute;
            left: 0; right: 0; bottom: 0;
            height: 42px;
            background: linear-gradient(transparent, var(--bg-codeblock));
            border-radius: 0 0 8px 8px;
            pointer-events: none;
        }

        /* 按钮行：源网页 + 阅读模式（与搜索「查看全部」同款虚线框） */
        .tc-fetch-actions { display: flex; gap: 8px; }
        .tc-fetch-open {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            width: 100%;
            padding: 7px 0;
            border: 1px dashed var(--border-color);
            border-radius: 8px;
            background: transparent;
            color: var(--text-muted);
            font-size: 11.5px;
            cursor: pointer;
            transition: color 0.15s, border-color 0.15s, background 0.15s;
        }
        .tc-fetch-open:hover {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-hover);
        }
        .tc-fetch-open i { font-size: 10px; }

        /* 错误态 */
        .tc-fetch-error {
            display: flex;
            align-items: flex-start;
            gap: 8px;
            font-size: 11.5px;
            line-height: 1.6;
            color: #f87171;
            background: rgba(248, 113, 113, 0.06);
            border: 1px solid rgba(248, 113, 113, 0.2);
            border-radius: 8px;
            padding: 10px 12px;
        }
        body.light-theme .tc-fetch-error {
            color: #dc2626;
            background: rgba(220, 38, 38, 0.05);
            border-color: rgba(220, 38, 38, 0.2);
        }
        .tc-fetch-error i { padding-top: 2px; font-size: 11px; flex-shrink: 0; }

        /* 提示条（如 SPA 页面说明） */
        .tc-fetch-hint {
            display: flex;
            align-items: flex-start;
            gap: 8px;
            font-size: 11px;
            line-height: 1.6;
            color: var(--text-muted);
            background: var(--bg-codeblock);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 8px 12px;
        }
        .tc-fetch-hint i { padding-top: 2px; font-size: 10.5px; flex-shrink: 0; color: var(--accent-light); }
    `;

    function injectStyle() {
        if (document.getElementById('browser-style')) return;
        const style = document.createElement('style');
        style.id = 'browser-style';
        style.textContent = STYLE;
        document.head.appendChild(style);
    }
    injectStyle();
})();
