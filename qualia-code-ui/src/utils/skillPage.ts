/**
 * 技能详情页（工作区「浏览器」Tab）HTML 构建
 * 对齐旧 tool-chip.js 的 buildSkillPage：自包含 HTML，配色跟随当前明暗主题，
 * 页面内监听 qualia-theme 消息与浏览器 Tab 预览共用同一套切换机制
 */
import { escapeHtml as esc } from './markdown'
import { i18n } from '@/i18n'
import type { SkillScriptRef } from './chip'

export interface SkillRecord {
  name: string
  desc: string
  /** 技能说明（skill.md 原文，可能带 YAML frontmatter） */
  doc: string
  scripts: SkillScriptRef[]
  refs: string[]
}

/** 剥离 YAML frontmatter */
function stripFrontmatter(text: string): string {
  return String(text ?? '').replace(/^---\s*\n[\s\S]*?\n---\s*\n?/, '')
}

/** 轻量行内 Markdown（行内代码与加粗），仅供技能详情页使用 */
function mdInline(s: string): string {
  return esc(s).replace(/`([^`]+)`/g, '<code>$1</code>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

/** 轻量块级 Markdown（标题 / 列表 / 段落） */
function mdToHtml(text: string): string {
  const lines = stripFrontmatter(text).replace(/\r\n?/g, '\n').split('\n')
  const out: string[] = []
  let list: 'ul' | 'ol' | null = null
  const closeList = () => {
    if (list) {
      out.push(`</${list}>`)
      list = null
    }
  }
  for (const raw of lines) {
    const line = raw.trim()
    const h = line.match(/^(#{1,4})\s+(.*)$/)
    if (h?.[1] && h[2] !== undefined) {
      closeList()
      const lv = h[1].length + 1
      out.push(`<h${lv}>${mdInline(h[2])}</h${lv}>`)
      continue
    }
    const ul = line.match(/^[-*]\s+(.*)$/)
    const ol = line.match(/^\d+[.)]\s+(.*)$/)
    const liText = ul?.[1] ?? ol?.[1]
    if (liText !== undefined) {
      const type = ul ? 'ul' : 'ol'
      if (list !== type) {
        closeList()
        out.push(`<${type}>`)
        list = type
      }
      out.push(`<li>${mdInline(liText)}</li>`)
      continue
    }
    if (!line) {
      closeList()
      continue
    }
    // 列表项续行（原文带缩进）并入上一项
    if (list && /^\s/.test(raw) && out.length) {
      const prev = out[out.length - 1]
      if (prev !== undefined) out[out.length - 1] = prev.replace(/<\/li>$/, ' ' + mdInline(line) + '</li>')
      continue
    }
    closeList()
    out.push(`<p>${mdInline(line)}</p>`)
  }
  closeList()
  return out.join('')
}

/** 自包含技能详情页（rec 由 ChipDetailSkill 组装传入） */
export function buildSkillPage(rec: SkillRecord, light: boolean): string {
  const scriptRows = rec.scripts
    .map(
      (s) => `
            <div class="res-item">
                <span class="res-icon">⚡</span>
                <div class="res-main">
                    <span class="res-name">${esc(s.name)}</span>
                    ${s.desc ? `<span class="res-desc">${esc(s.desc)}</span>` : ''}
                </div>
            </div>`,
    )
    .join('')
  const refRows = rec.refs
    .map(
      (r) => `
            <div class="res-item">
                <span class="res-icon">📄</span>
                <div class="res-main"><span class="res-name">${esc(r)}</span></div>
            </div>`,
    )
    .join('')
  const t = i18n.global.t
  const metaParts = [
    `<span class="src">${t('skillPage.skill')}</span>`,
    `<span>${t('skillPage.scripts', { n: rec.scripts.length })}</span>`,
    `<span>${t('skillPage.refs', { n: rec.refs.length })}</span>`,
  ].join('<span class="sep">·</span>')
  return `<!DOCTYPE html>
<html lang="zh-CN"${light ? ' class="light"' : ''}>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
    :root {
        color-scheme: dark;
        --bg: #0d0f16; --text: #e4e9f2; --sub: #8f97ae; --muted: #4f566b;
        --border: rgba(255, 255, 255, 0.06); --hover: rgba(255, 255, 255, 0.04);
        --accent: #b3a8ff; --icon-bg: rgba(124, 108, 240, 0.14); --code-bg: rgba(255, 255, 255, 0.06); --scrollbar: #2e3546;
    }
    /* 主题类同时挂在 html 与 body 上：视口滚动条属于 html，变量必须在 html 层级生效 */
    .light {
        color-scheme: light;
        --bg: #ffffff; --text: #1e232e; --sub: #5f6883; --muted: #8f97ae;
        --border: rgba(0, 0, 0, 0.07); --hover: rgba(0, 0, 0, 0.04);
        --accent: #24292f; --icon-bg: rgba(31, 35, 40, 0.08); --code-bg: rgba(0, 0, 0, 0.05); --scrollbar: #c8d0d8;
    }
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html { background: var(--bg); }
    ::-webkit-scrollbar { width: 5px; height: 5px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: var(--scrollbar); border-radius: 8px; }
    body { background: var(--bg); color: var(--text); font-family: -apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif; transition: background 0.2s; }
    .skill-head { position: sticky; top: 0; background: var(--bg); padding: 18px 28px 0; z-index: 10; }
    .skill-head-inner { max-width: 680px; margin: 0 auto; padding-bottom: 14px; border-bottom: 1px solid var(--border); }
    .title-row { display: flex; align-items: center; gap: 10px; }
    .title-icon { width: 30px; height: 30px; border-radius: 8px; background: var(--icon-bg); display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0; }
    .title-text { font-size: 17px; font-weight: 700; letter-spacing: 0.2px; }
    .desc-row { margin-top: 8px; padding-left: 40px; font-size: 12.5px; line-height: 1.7; color: var(--sub); }
    .meta-row { display: flex; align-items: center; gap: 6px; margin-top: 8px; padding-left: 40px; font-size: 11px; color: var(--muted); }
    .meta-row .sep { color: var(--border); }
    .meta-row .src { color: var(--accent); font-weight: 600; }
    .content { max-width: 680px; margin: 0 auto; padding: 8px 28px 24px; }
    .sec-title { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.8px; color: var(--muted); margin: 26px 0 10px; }
    /* 技能说明（Markdown） */
    .md { font-size: 13.5px; line-height: 1.8; color: var(--text); }
    .md h2 { font-size: 16px; margin: 20px 0 8px; }
    .md h3 { font-size: 14px; margin: 18px 0 6px; }
    .md h4, .md h5 { font-size: 13px; margin: 14px 0 6px; color: var(--sub); }
    .md p { margin: 8px 0; }
    .md ul, .md ol { margin: 8px 0; padding-left: 22px; }
    .md li { margin: 4px 0; }
    .md code { font-family: Consolas, monospace; font-size: 12px; background: var(--code-bg); padding: 1px 5px; border-radius: 4px; }
    /* 脚本 / 文档资源行 */
    .res-item { display: flex; align-items: flex-start; gap: 10px; padding: 11px 12px; margin: 0 -12px; border-radius: 10px; transition: background 0.15s; }
    .res-item + .res-item { border-top: 1px solid var(--border); }
    .res-item:hover { background: var(--hover); }
    .res-icon { font-size: 13px; line-height: 1.5; flex-shrink: 0; }
    .res-main { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
    .res-name { font-family: Consolas, monospace; font-size: 13px; font-weight: 600; }
    .res-desc { font-size: 12px; line-height: 1.6; color: var(--sub); }
    .end-tip { max-width: 680px; margin: 0 auto; padding: 16px 28px 40px; text-align: center; font-size: 11.5px; color: var(--muted); }
    .end-tip::before { content: '— '; color: var(--border); }
    .end-tip::after { content: ' —'; color: var(--border); }
</style>
</head>
<body${light ? ' class="light"' : ''}>
    <div class="skill-head">
        <div class="skill-head-inner">
            <div class="title-row"><span class="title-icon">🧩</span><span class="title-text">${esc(rec.name)}</span></div>
            ${rec.desc ? `<div class="desc-row">${esc(rec.desc)}</div>` : ''}
            <div class="meta-row">${metaParts}</div>
        </div>
    </div>
    <div class="content">
        <div class="sec-title">${t('skillPage.doc')}</div>
        <div class="md">${mdToHtml(rec.doc)}</div>
        ${scriptRows ? `<div class="sec-title">${t('skillPage.availableScripts')}</div>${scriptRows}` : ''}
        ${refRows ? `<div class="sec-title">${t('skillPage.refDocs')}</div>${refRows}` : ''}
    </div>
    <div class="end-tip">${t('skillPage.endTip')}</div>
<script>
    window.addEventListener('message', function (e) {
        if (e.data && e.data.type === 'qualia-theme') {
            document.documentElement.classList.toggle('light', !!e.data.light);
            document.body.classList.toggle('light', !!e.data.light);
        }
    });
</script>
</body>
</html>`
}
