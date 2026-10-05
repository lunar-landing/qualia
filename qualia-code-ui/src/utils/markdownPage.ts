/**
 * Markdown 阅读页（工作区「浏览器」Tab）HTML 构建：
 * md 原文 → renderMarkdown 片段 + 自包含文档骨架（阅读排版 + 明暗两套 CSS 变量），
 * 初始主题由调用方传入；运行时父页切主题通过 postMessage({type:'qualia-theme'}) 实时同步
 * （theme.ts 的 apply 已向 .ws-preview-frame 广播），与技能详情/阅读模式页共用同一机制。
 */
import { renderMarkdown } from './markdown'

/** markdown 文件判定（cfd 预览路由与 BrowserPane 刷新共用） */
export function isMarkdownFile(path: string): boolean {
  return /\.(md|markdown)$/i.test(path)
}

/** 自包含 Markdown 阅读页（md 为原文，light 为初始主题） */
export function buildMarkdownPage(md: string, light: boolean): string {
  const body = renderMarkdown(md)
  return `<!DOCTYPE html>
<html${light ? ' class="light"' : ''}>
<head>
<meta charset="utf-8">
<style>
  :root { color-scheme: dark; --mp-bg:#0d1117; --mp-fg:#e6edf3; --mp-mut:#8b949e; --mp-bd:#30363d;
          --mp-code-bg:#161b22; --mp-link:#58a6ff; }
  .light { color-scheme: light; --mp-bg:#ffffff; --mp-fg:#1f2328; --mp-mut:#656d76; --mp-bd:#d0d7de;
           --mp-code-bg:#f6f8fa; --mp-link:#0969da; }
  * { box-sizing: border-box; }
  body { margin:0; padding:32px 24px 64px; background:var(--mp-bg); color:var(--mp-fg);
         font:15px/1.75 -apple-system,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif; }
  .md-body { max-width:780px; margin:0 auto; }
  .md-body h1,.md-body h2,.md-body h3,.md-body h4 { margin:1.6em 0 .6em; line-height:1.35; }
  .md-body h1,.md-body h2 { padding-bottom:.3em; border-bottom:1px solid var(--mp-bd); }
  .md-body h1 { font-size:1.9em; } .md-body h2 { font-size:1.45em; } .md-body h3 { font-size:1.2em; }
  .md-body p { margin:.7em 0; }
  .md-body a { color:var(--mp-link); text-decoration:none; }
  .md-body a:hover { text-decoration:underline; }
  .md-body ul,.md-body ol { padding-left:1.6em; margin:.6em 0; }
  .md-body li { margin:.25em 0; }
  .md-body blockquote { margin:.9em 0; padding:.1em 1em; border-left:3px solid var(--mp-mut); color:var(--mp-mut); }
  .md-body code { font-family:'JetBrains Mono',ui-monospace,Consolas,monospace; font-size:.88em;
                  background:var(--mp-code-bg); border:1px solid var(--mp-bd); border-radius:4px; padding:.12em .38em; }
  .md-body pre { background:var(--mp-code-bg); border:1px solid var(--mp-bd); border-radius:8px;
                 padding:13px 15px; overflow:auto; }
  .md-body pre code { background:none; border:0; padding:0; font-size:12.8px; line-height:1.6; }
  .md-body table { border-collapse:collapse; margin:.9em 0; max-width:100%; display:block; overflow:auto; }
  .md-body th,.md-body td { border:1px solid var(--mp-bd); padding:6px 13px; }
  .md-body th { background:var(--mp-code-bg); font-weight:600; }
  .md-body img { max-width:100%; }
  .md-body hr { border:0; border-top:1px solid var(--mp-bd); margin:1.8em 0; }
</style>
</head>
<body>
<div class="md-body">${body}</div>
<script>
window.addEventListener('message', function (e) {
  if (e.data && e.data.type === 'qualia-theme') {
    document.documentElement.classList.toggle('light', !!e.data.light);
  }
});
</script>
</body>
</html>`
}
