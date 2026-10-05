import { defineStore } from 'pinia'
import { createSession as apiCreate, deleteSession as apiDelete, listSessions, renameSession as apiRename } from '@/api/chat'
import { useChatStore } from '@/stores/chat'
import type { ChatSession } from '@/types'

/** 新会话默认标题（创建时占位，首轮发送后就地替换为消息截断文本） */
export const DEFAULT_SESSION_TITLE = '新会话'

/** 会话列表与切换（对齐旧版：删除当前会话后回到欢迎态，重命名空标题恢复自动标题） */
export const useSessionStore = defineStore('session', {
  state: () => ({
    sessions: [] as ChatSession[],
    currentSessionId: null as string | null,
    loading: false,
  }),

  getters: {
    currentSession: (state): ChatSession | null =>
      state.sessions.find((s) => s.id === state.currentSessionId) ?? null,
  },

  actions: {
    async loadSessions() {
      this.loading = true
      try {
        this.sessions = await listSessions()
      } finally {
        this.loading = false
      }
    },

    /** 调后端创建（占位标题，首轮发送后就地更新），置为当前会话 */
    async createSession(title = DEFAULT_SESSION_TITLE): Promise<ChatSession> {
      const session = await apiCreate(title)
      this.sessions.unshift(session)
      this.currentSessionId = session.id
      return session
    },

    switchTo(sessionId: string) {
      this.currentSessionId = sessionId
      // 切入即视为已读：清除后台会话完成提示
      useChatStore().clearUnread(sessionId)
      // 历史懒加载由 ChatArea 渲染时触发（ensureHistory），store 不耦合加载时序
    },

    async removeSession(sessionId: string) {
      await apiDelete(sessionId)
      const idx = this.sessions.findIndex((s) => s.id === sessionId)
      if (idx >= 0) this.sessions.splice(idx, 1)
      if (this.currentSessionId === sessionId) this.currentSessionId = null
      useChatStore().clearSession(sessionId)
    },

    /** 空标题/null 恢复自动标题：重拉列表取后端生成的标题 */
    async renameSession(sessionId: string, title: string | null) {
      await apiRename(sessionId, title || null)
      const target = this.sessions.find((s) => s.id === sessionId)
      if (!target) return
      if (title) {
        target.title = title
      } else {
        await this.loadSessions()
      }
    },
  },
})
