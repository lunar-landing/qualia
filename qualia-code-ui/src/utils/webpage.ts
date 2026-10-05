/**
 * 联网工具内嵌预览页生成（平移旧 browser.js 的 buildPage/buildReader）：
 * 自包含 HTML，内嵌明暗两套 CSS 变量（取值对齐主应用色板），
 * 初始主题由调用方传入；运行时父页切主题通过 postMessage({type:'qualia-theme'}) 实时同步
 * （theme.ts 的 apply 已向 .ws-preview-frame 广播）。
 */
import { displayUrl, domainOf, escHtml } from '@/utils/chip'
import { i18n } from '@/i18n'

const AVATAR_COLORS = ['#6366f1', '#f59e0b', '#ef4444', '#0ea5e9', '#8b5cf6', '#10b981', '#ec4899', '#14b8a6']

/** 域名哈希取色，同一站点颜色稳定 */
function avatarColor(domain: string): string {
  let h = 0
  for (let i = 0; i < domain.length; i++) h = (h * 31 + domain.charCodeAt(i)) >>> 0
  return AVATAR_COLORS[h % AVATAR_COLORS.length] ?? '#6366f1'
}

function siteName(url: string): string {
  return domainOf(url).replace(/^www\./i, '')
}

export interface SearchPageRecord {
  query: string
  source: string
  items: { title: string; url: string; snippet: string }[]
  time?: string
}

/** 自包含搜索结果页：sticky 查询头 + 站点行 + 标题 + 两行摘要，通栏分隔线，尾部尽头提示 */
export function buildSearchPage(rec: SearchPageRecord, light: boolean): string {
  const rows = rec.items
    .map((it) => {
      const href = it.url ? (/^https?:\/\//i.test(it.url) ? it.url : 'https://' + it.url) : ''
      const site = siteName(it.url || '')
      const siteRow = site
        ? `
                        <span class="site-row">
                            <span class="avatar" style="background:${avatarColor(site)}">${escHtml(site.charAt(0).toUpperCase())}</span>
                            <span class="site-name">${escHtml(site)}</span>
                            <span class="site-url">${escHtml(displayUrl(it.url))}</span>
                        </span>`
        : ''
      const hrefAttr = href ? ` href="${escHtml(href)}" target="_blank" rel="noopener"` : ''
      return `
                    <a class="item"${hrefAttr}>${siteRow}
                        <span class="title">${escHtml(it.title)}</span>
                        ${it.snippet ? `<span class="snippet">${escHtml(it.snippet)}</span>` : ''}
                    </a>
                `
    })
    .join('')
  const t = i18n.global.t
  const metaParts = [
    `<span class="src">${escHtml(rec.source || '')}</span>`,
    `<span>${t('webPage.results', { n: rec.items.length })}</span>`,
  ]
    .concat(rec.time ? [`<span>${escHtml(rec.time)}</span>`] : [])
    .join('<span class="sep">·</span>')
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
            <div class="query-row"><span class="query-icon">🌐</span><span class="query-text">${escHtml(rec.query)}</span></div>
            <div class="meta-row">${metaParts}</div>
        </div>
    </div>
    <div class="results">${rows}</div>
    <div class="end-tip">${t('webPage.allShown', { n: rec.items.length })}</div>
<script>
    window.addEventListener('message', function (e) {
        if (e.data && e.data.type === 'qualia-theme') {
            document.documentElement.classList.toggle('light', !!e.data.light);
            document.body.classList.toggle('light', !!e.data.light);
        }
    });
</` + `script>
</body>
</html>`
}

export interface ReaderPageRecord {
  title: string
  url: string
  content: string
  type: string
  length: number
}

/** 自包含阅读模式页：正文按空行分段，站点禁止 iframe 嵌入时的降级阅读视图 */
export function buildReaderPage(rec: ReaderPageRecord, light: boolean): string {
  const href = rec.url ? (/^https?:\/\//i.test(rec.url) ? rec.url : 'https://' + rec.url) : ''
  const paras = rec.content
    .split(/\n{2,}/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => `<p>${escHtml(p).replace(/\n/g, '<br>')}</p>`)
    .join('')
  const t = i18n.global.t
  const metaParts = [
    href ? `<a href="${escHtml(href)}" target="_blank" rel="noopener">🔗 ${escHtml(displayUrl(rec.url))}</a>` : '',
    `<span>${t('webPage.chars', { n: rec.length.toLocaleString() })}</span>`,
    rec.type ? `<span>${escHtml(rec.type)}</span>` : '',
  ]
    .filter(Boolean)
    .join('<span class="dot">·</span>')
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
        <h1>${escHtml(rec.title || displayUrl(rec.url))}</h1>
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
</` + `script>
</body>
</html>`
}
