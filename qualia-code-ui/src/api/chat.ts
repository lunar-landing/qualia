import { http } from './http'
import type { ChatSession, ContextInfo, HistoryMessage, TokenStat } from '@/types'

export function listSessions(): Promise<ChatSession[]> {
  return http.get('/api/chat/sessions')
}

export function createSession(title?: string): Promise<ChatSession> {
  return http.post('/api/chat/sessions', title ? { title } : {})
}

export function deleteSession(sessionId: string): Promise<{ success: boolean }> {
  return http.delete(`/api/chat/sessions/${encodeURIComponent(sessionId)}`)
}

/** 重命名会话；title 传 null/空串恢复自动标题 */
export function renameSession(sessionId: string, title: string | null): Promise<{ success: boolean }> {
  return http.put(`/api/chat/sessions/${encodeURIComponent(sessionId)}/title`, { title })
}

export function getMessages(sessionId: string): Promise<HistoryMessage[]> {
  return http.get(`/api/chat/sessions/${encodeURIComponent(sessionId)}/messages`)
}

/**
 * 绑定/解绑工作区：唯一写入口（binding.json 后端权威）。
 * workspace 为 null/undefined = 解绑回派生沙箱；create=true 时目录不存在会先创建；
 * 流式期间后端 409（ApiError.message 为原因）。
 */
export function bindSession(
  sessionId: string,
  workspace: string | null,
  create = false,
): Promise<{ success: boolean; session: ChatSession }> {
  return http.post(`/api/chat/sessions/${encodeURIComponent(sessionId)}/bind`, { workspace, create })
}

export function getTokenStats(days = 30): Promise<TokenStat[]> {
  return http.get(`/api/chat/stats/tokens?days=${days}`)
}

/** 会话上下文视图数据（输入框底部只读状态条） */
export function getContextInfo(sessionId: string): Promise<ContextInfo> {
  return http.get(`/api/chat/context?sessionId=${encodeURIComponent(sessionId)}`)
}
