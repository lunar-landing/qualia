import { defineStore } from 'pinia'
import {
  bindSession as apiBind,
  createSession as apiCreate,
  deleteSession as apiDelete,
  listSessions,
  renameSession as apiRename,
} from '@/api/chat'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'
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
      // 新会话未绑定：工作区镜像必须清空——否则残留上一会话的绑定，chip 会误显示已选中
      useWorkspaceStore().applySessionWorkspace(session.workspace ?? null)
      return session
    },

    switchTo(sessionId: string) {
      this.currentSessionId = sessionId
      // 切入即视为已读：清除后台会话完成提示
      useChatStore().clearUnread(sessionId)
      // 绑定镜像同步 + 会话域数据失效（文件树随新会话的工作区重载）
      useWorkspaceStore().applySessionWorkspace(this.sessions.find((s) => s.id === sessionId)?.workspace)
      // 历史懒加载由 ChatArea 渲染时触发（ensureHistory），store 不耦合加载时序
    },

    /**
     * 换绑/解绑工作区（绑定唯一写入口 = 后端 bind API；流式 409 由后端裁决抛 ApiError）。
     * 用返回的 session 就地替换本地条目；当前会话同步镜像并失效会话域数据。
     */
    async bindSession(sessionId: string, workspace: string | null, create = false) {
      const { session } = await apiBind(sessionId, workspace, create)
      const idx = this.sessions.findIndex((s) => s.id === sessionId)
      if (idx >= 0) {
        // 新会话（尚无消息）后端仅返回轻量条目（title/workspace）：就地合并，缺失字段保留本地
        this.sessions[idx] = { ...this.sessions[idx], ...session, id: sessionId }
      }
      if (this.currentSessionId === sessionId) {
        // 回显以响应为准，请求参数兜底（响应缺 workspace 时本地仍能立即可见）
        useWorkspaceStore().applySessionWorkspace(session?.workspace ?? (workspace || null))
      }
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
