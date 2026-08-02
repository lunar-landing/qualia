import { ref } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

export function useMarkdown() {
  const md = new MarkdownIt({
    html: true,
    linkify: true,
    typographer: true,
    highlight: function (str, lang) {
      if (lang && hljs.getLanguage(lang)) {
        try {
          return hljs.highlight(str, { language: lang }).value
        } catch (__) {}
      }
      return '' // 使用外部默认转义
    }
  })

  // 自定义渲染规则
  md.renderer.rules.table_open = function () {
    return '<div class="table-wrapper"><table>'
  }

  md.renderer.rules.table_close = function () {
    return '</table></div>'
  }

  // 添加标题 ID 用于锚点链接
  md.renderer.rules.heading_open = function (tokens, idx) {
    const token = tokens[idx]
    const level = token.tag
    const nextToken = tokens[idx + 1]
    const title = nextToken.children
      .map(t => t.content)
      .join('')
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[，。！？；：''（）【】《》、]/g, '')
      .replace(/[^\w\u4e00-\u9fa5-]/g, '')
    
    return `<${level} id="${title}">`
  }

  const parseMarkdown = (content) => {
    if (!content) {
      return { html: '', title: '' }
    }

    // 提取标题（第一个 h1）
    const titleMatch = content.match(/^#\s+(.+)$/m)
    const title = titleMatch ? titleMatch[1] : ''

    // 移除第一个 h1 标题，避免页面重复显示
    let contentWithoutTitle = content
    if (titleMatch) {
      contentWithoutTitle = content.replace(/^#\s+.+\n?/, '')
    }

    // 解析 Markdown
    const html = md.render(contentWithoutTitle)

    return { html, title }
  }

  const extractHeadings = (content) => {
    if (!content) return []

    const headings = []
    const lines = content.split('\n')
    
    lines.forEach((line) => {
      // 移除行尾的 \r（Windows 换行符）
      const cleanLine = line.replace(/\r$/, '')
      const match = cleanLine.match(/^(#{1,6})\s+(.+)$/)
      if (match) {
        const level = match[1].length
        const text = match[2].trim()
        const id = text
          .toLowerCase()
          .replace(/\s+/g, '-')
          .replace(/[，。！？；：''（）【】《》、]/g, '')
          .replace(/[^\w\u4e00-\u9fa5-]/g, '')
        
        headings.push({ level, text, id })
      }
    })

    return headings
  }

  return {
    parseMarkdown,
    extractHeadings,
    markdownIt: md
  }
}