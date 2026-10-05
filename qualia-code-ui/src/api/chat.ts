import { http } from './http'
import type { ChatSession, HistoryMessage, TokenStat } from '@/types'

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

export function getTokenStats(days = 30): Promise<TokenStat[]> {
  return http.get(`/api/chat/stats/tokens?days=${days}`)
}
