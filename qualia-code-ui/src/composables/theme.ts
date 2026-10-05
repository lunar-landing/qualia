import { ref } from 'vue'

// 全局明暗主题（沿用旧实现约定：body.light-theme + localStorage['codex-theme']）
// hljs 高亮主题在构建期取本地资源（无 CDN），按主题动态切换 link href
// 2026-09 墨线加重定稿：atom-one → GitHub 官方色板；函数/类名的紫色由 index.css 去紫化覆盖接管
import hljsDarkUrl from 'highlight.js/styles/github-dark.css?url'
import hljsLightUrl from 'highlight.js/styles/github.css?url'

const isLight = ref(false)
let inited = false

function apply(light: boolean) {
  document.body.classList.toggle('light-theme', light)
  const link = document.getElementById('hljsTheme') as HTMLLinkElement | null
  if (link) link.href = light ? hljsLightUrl : hljsDarkUrl
  // 通知浏览器 Tab 内的预览页（搜索结果/阅读模式/技能详情页）同步切主题
  document.querySelectorAll<HTMLIFrameElement>('.ws-preview-frame').forEach((frame) => {
    frame.contentWindow?.postMessage({ type: 'qualia-theme', light }, '*')
  })
}

/** 应用启动时调用一次：恢复持久化的主题偏好 */
export function initTheme() {
  if (inited) return
  inited = true
  isLight.value = localStorage.getItem('codex-theme') === 'light'
  apply(isLight.value)
}

export function useTheme() {
  function toggleTheme() {
    isLight.value = !isLight.value
    localStorage.setItem('codex-theme', isLight.value ? 'light' : 'dark')
    apply(isLight.value)
  }
  return { isLight, toggleTheme }
}
