import { marked } from 'marked'
import hljs from 'highlight.js/lib/common'
import { i18n } from '@/i18n'

// 旧版配置：breaks 支持换行符，gfm 支持 GitHub 风格 markdown
marked.setOptions({ breaks: true, gfm: true })

export function escapeHtml(str: unknown): string {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** markdown 转 HTML；解析失败回退转义原文 */
export function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    return escapeHtml(text)
  }
}

/** 沙箱策略（平移旧版）：srcdoc 自建页绝不给 allow-same-origin；外部 URL 需要它 */
export const SRCDOC_SANDBOX = 'allow-scripts allow-popups allow-popups-to-escape-sandbox'
export const URL_SANDBOX =
  'allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox'

/**
 * 代码块增强（平移旧 enhanceCodeBlocks）：hljs 高亮 + code-wrap 工具条 + 复制/HTML 预览按钮。
 * dataset.enhanced 防重；onPreview 注入浏览器 Tab 预览动作（preview store）
 */
export function enhanceCodeBlocks(scope: HTMLElement, onPreview: (html: string) => void) {
  scope.querySelectorAll<HTMLElement>('pre code').forEach((code) => {
    if (code.dataset.enhanced) return
    code.dataset.enhanced = '1'

    try {
      hljs.highlightElement(code as HTMLElement)
    } catch {
      // 高亮失败保持原样
    }

    const pre = code.parentElement
    if (!pre || pre.tagName !== 'PRE') return

    const lang = (code.className.match(/language-([\w#+-]+)/) || [])[1] || 'code'
    const canPreview = /^(html|htm|xhtml)$/i.test(lang)
    const t = i18n.global.t
    const wrap = document.createElement('div')
    wrap.className = 'code-wrap'
    wrap.innerHTML = `
      <div class="code-head">
        <span class="dots"><i></i><i></i><i></i></span>
        <span class="code-lang">${escapeHtml(lang)}</span>
        <span class="head-slot">
          ${canPreview ? `<button class="code-preview" title="${t('md.preview')}"><i class="far fa-eye"></i></button>` : ''}
          <button class="code-copy" title="${t('md.copyCode')}"><i class="far fa-copy"></i></button>
        </span>
      </div>
    `
    pre.parentNode?.insertBefore(wrap, pre)
    wrap.appendChild(pre)

    const previewBtn = wrap.querySelector('.code-preview')
    if (previewBtn) {
      previewBtn.addEventListener('click', () => onPreview(code.textContent ?? ''))
    }

    wrap.querySelector('.code-copy')?.addEventListener('click', (e) => {
      const btn = e.currentTarget as HTMLElement
      void navigator.clipboard.writeText(code.textContent ?? '').then(() => {
        btn.classList.add('copied')
        btn.innerHTML = '<i class="fas fa-check"></i>'
        window.setTimeout(() => {
          btn.classList.remove('copied')
          btn.innerHTML = '<i class="far fa-copy"></i>'
        }, 1500)
      })
    })
  })
}
