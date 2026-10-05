import { ref } from 'vue'

// 聊天区视图模式（对齐 theme.ts 约定：模块级单例 + localStorage 持久化）
// 'bubble' = 气泡聊天区（ChatArea）；'terminal' = 终端形态聊天区（ChatTerminal）
// 两组件在 App 内 v-show 并存保活：切换不卸载，输入草稿与后台流均不断

export type ChatViewMode = 'bubble' | 'terminal'

const STORAGE_KEY = 'codex-chat-view'

const view = ref<ChatViewMode>('bubble')
let inited = false

function restore(): ChatViewMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'terminal' ? 'terminal' : 'bubble'
  } catch {
    return 'bubble'
  }
}

/** 应用启动时调用一次：恢复持久化的视图偏好 */
export function initChatView() {
  if (inited) return
  inited = true
  view.value = restore()
}

export function useChatView() {
  function toggleView() {
    view.value = view.value === 'terminal' ? 'bubble' : 'terminal'
    try {
      localStorage.setItem(STORAGE_KEY, view.value)
    } catch {
      /* 隐私模式等存储失败时仅内存生效 */
    }
  }
  return { view, toggleView }
}
