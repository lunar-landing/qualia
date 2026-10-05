import { defineStore } from 'pinia'
import { getMessages } from '@/api/chat'
import type { AgentStep, ChatMessage, HistoryMessage, PendingAttachment, QueuedMessage } from '@/types'

/** 流式状态机状态（idle→connecting→streaming→answering→idle；error 终态可重入） */
export type StreamStatus = 'idle' | 'connecting' | 'streaming' | 'answering' | 'error'

/** per-session 流式状态（旧 getSessionState 的响应式版本；连接句柄由 useChatStream 持有，不入 store） */
export interface StreamState {
  status: StreamStatus
  /** 本轮活跃 assistant 消息 ID（messagesBySession 内条目） */
  msgId: string
  /** 本轮全部步骤（终端/变更面板/文件树徽标的数据源） */
  steps: AgentStep[]
  fullAnswer: string
  /** 无步骤时降级为普通气泡 */
  plainBubble: boolean
  /** 本轮用户消息文本 */
  userMessage: string
  error: string | null
}

export function emptyStreamState(): StreamState {
  return { status: 'idle', msgId: '', steps: [], fullAnswer: '', plainBubble: false, userMessage: '', error: null }
}

const EMPTY_STREAM: StreamState = emptyStreamState()

let seq = 0
function genId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${(++seq).toString(36)}`
}

/** 待发送队列上限与 localStorage key（qualia:queue:{sid}） */
const QUEUE_LIMIT = 10
const queueKey = (sid: string) => `qualia:queue:${sid}`

/** 历史消息转统一消息模型（id 稳定：hist-{sid}-{index}，供 v-memo 记忆） */
function toChatMessage(m: HistoryMessage, index: number, sid: string): ChatMessage {
  const msg: ChatMessage = {
    id: `hist-${sid}-${index}`,
    role: m.role === 'user' ? 'user' : 'assistant',
    content: m.content,
  }
  if (m.attachments?.length) msg.attachments = m.attachments
  if (m.steps?.length) msg.steps = m.steps
  return msg
}

/**
 * 历史加载去重：同一会话并发触发只发一次请求；
 * 请求失败移除记录允许重试；成功后本地消息即为源（活跃流会话跳过加载，本地已有实时消息）
 */
const pendingHistory = new Map<string, Promise<void>>()

export const useChatStore = defineStore('chat', {
  state: () => ({
    messagesBySession: {} as Record<string, ChatMessage[]>,
    streamBySession: {} as Record<string, StreamState>,
    /** 后台会话本轮完成未读标记（切入即清除；当前会话不标记） */
    unreadBySession: {} as Record<string, boolean>,
    /** per-session 待发送队列（流进行中入队，回复完成自动接发；localStorage 持久化） */
    queueBySession: {} as Record<string, QueuedMessage[]>,
    /** 队列条目行内编辑中：sid → 编辑条目 id（编辑中暂停自动接发） */
    queueEditing: {} as Record<string, string | null>,
    /** 队列是否来自刷新恢复（头部来源说明；入队即清除） */
    queueRestored: {} as Record<string, boolean>,
  }),

  getters: {
    /** 指定会话消息列表（未加载时为空数组） */
    messagesOf(state) {
      return (sid: string): ChatMessage[] => state.messagesBySession[sid] ?? []
    },
    streamOf(state) {
      return (sid: string): StreamState => state.streamBySession[sid] ?? EMPTY_STREAM
    },
    /** 会话是否有进行中的流（含 connecting） */
    isProcessing(state) {
      return (sid: string): boolean => {
        const s = state.streamBySession[sid]
        return !!s && s.status !== 'idle' && s.status !== 'error'
      }
    },
    /** 指定会话待发送队列（未加载时为空数组） */
    queueOf(state) {
      return (sid: string): QueuedMessage[] => state.queueBySession[sid] ?? []
    },
  },

  actions: {
    /** 首次查看会话时加载历史；活跃流会话跳过（sendMessage 已写入本地实时消息） */
    ensureHistory(sid: string): Promise<void> {
      let p = pendingHistory.get(sid)
      if (!p) {
        p = this.doLoadHistory(sid).catch(() => {
          pendingHistory.delete(sid)
        })
        pendingHistory.set(sid, p)
      }
      return p
    },

    async doLoadHistory(sid: string) {
      const stream = this.streamBySession[sid]
      if (stream && stream.status !== 'idle' && stream.status !== 'error') return
      const history = await getMessages(sid)
      this.messagesBySession[sid] = history.map((m, i) => toChatMessage(m, i, sid))
    },

    ensureArray(sid: string): ChatMessage[] {
      let list = this.messagesBySession[sid]
      if (!list) {
        list = []
        this.messagesBySession[sid] = list
      }
      return list
    },

    appendMessage(sid: string, msg: ChatMessage) {
      this.ensureArray(sid).push(msg)
    },

    patchMessage(sid: string, msgId: string, patch: Partial<ChatMessage>) {
      const msg = this.ensureArray(sid).find((m) => m.id === msgId)
      if (msg) Object.assign(msg, patch)
    },

    appendStep(sid: string, msgId: string, step: AgentStep) {
      const msg = this.ensureArray(sid).find((m) => m.id === msgId)
      if (!msg) return
      if (!msg.steps) msg.steps = []
      msg.steps.push(step)
    },

    /** 发送前建立本轮流式状态并落位用户/助手占位消息 */
    beginStream(sid: string, text: string, attachments: ChatMessage['attachments']) {
      const msgId = genId('msg')
      const user: ChatMessage = { id: genId('u'), role: 'user', content: text }
      if (attachments?.length) user.attachments = attachments
      const assistant: ChatMessage = { id: msgId, role: 'assistant', content: '' }
      this.appendMessage(sid, user)
      this.appendMessage(sid, assistant)
      this.streamBySession[sid] = {
        ...emptyStreamState(),
        status: 'connecting',
        msgId,
        userMessage: text,
      }
      return msgId
    },

    setStreamStatus(sid: string, status: StreamStatus) {
      const s = this.streamBySession[sid]
      if (s) s.status = status
    },

    /** 流收尾：error 为空表示正常完成（消息本体在帧处理时已写好） */
    endStream(sid: string, error?: string) {
      const s = this.streamBySession[sid]
      if (!s) return
      s.status = error ? 'error' : 'idle'
      s.error = error ?? null
    },

    /** 错误气泡（无步骤无答案时流失败的回复形态，对齐旧版 handleStreamError） */
    appendErrorMessage(sid: string, text: string) {
      this.appendMessage(sid, { id: genId('err'), role: 'assistant', content: text })
    },

    /** 空轮次清理占位消息（对齐旧版：无步骤无答案时不产生任何消息） */
    removeMessage(sid: string, msgId: string) {
      const list = this.messagesBySession[sid]
      if (!list) return
      const idx = list.findIndex((m) => m.id === msgId)
      if (idx >= 0) list.splice(idx, 1)
    },

    /** 后台会话本轮有产出时标记未读（由流收尾调用，当前会话不标） */
    markUnread(sid: string) {
      this.unreadBySession[sid] = true
    },

    /** 切入会话即视为已读 */
    clearUnread(sid: string) {
      delete this.unreadBySession[sid]
    },

    // ===== 待发送队列（v3 稿 docs/design/queued-messages-inputarea-v3.html） =====

    /** 切入会话时惰性恢复队列（内存已有则跳过）；非空置恢复来源标记 */
    ensureQueue(sid: string) {
      if (this.queueBySession[sid]) return
      let list: QueuedMessage[] = []
      try {
        list = JSON.parse(localStorage.getItem(queueKey(sid)) ?? '[]') as QueuedMessage[]
      } catch {
        list = []
      }
      this.queueBySession[sid] = Array.isArray(list) ? list : []
      if (this.queueBySession[sid]!.length) this.queueRestored[sid] = true
    },

    /** 增删改后统一落盘（空队列移除 key） */
    persistQueue(sid: string) {
      const q = this.queueBySession[sid]
      if (q?.length) localStorage.setItem(queueKey(sid), JSON.stringify(q))
      else localStorage.removeItem(queueKey(sid))
    },

    /** 入队（FIFO，上限保护）；入队即用户主动操作，清除恢复标记 */
    enqueue(sid: string, text: string, attachments: PendingAttachment[] = []): boolean {
      this.ensureQueue(sid)
      const q = this.queueBySession[sid]!
      if (q.length >= QUEUE_LIMIT) return false
      q.push({
        id: genId('q'),
        text,
        attachments: attachments.length
          ? attachments.map((a) => ({ id: a.id, name: a.name, type: a.type, size: a.size }))
          : undefined,
      })
      this.queueRestored[sid] = false
      this.persistQueue(sid)
      return true
    },

    /** 出队首条（由 useChatStream 的 consumeQueue 在会话空闲时调用） */
    dequeue(sid: string): QueuedMessage | null {
      const q = this.queueBySession[sid]
      if (!q?.length) return null
      const next = q.shift()!
      this.persistQueue(sid)
      return next
    },

    removeQueued(sid: string, id: string) {
      const q = this.queueBySession[sid]
      if (!q) return
      const idx = q.findIndex((m) => m.id === id)
      if (idx < 0) return
      q.splice(idx, 1)
      this.persistQueue(sid)
      if (this.queueEditing[sid] === id) this.queueEditing[sid] = null
    },

    startQueuedEdit(sid: string, id: string) {
      this.queueEditing[sid] = id
    },

    /** 行内编辑保存（空文本视为取消）；保存后解除编辑态恢复接发 */
    saveQueuedEdit(sid: string, id: string, text: string) {
      const msg = this.queueBySession[sid]?.find((m) => m.id === id)
      if (msg && text.trim()) msg.text = text.trim()
      if (this.queueEditing[sid] === id) this.queueEditing[sid] = null
      this.persistQueue(sid)
    },

    cancelQueuedEdit(sid: string, id: string) {
      if (this.queueEditing[sid] === id) this.queueEditing[sid] = null
    },

    clearQueue(sid: string) {
      delete this.queueBySession[sid]
      delete this.queueEditing[sid]
      delete this.queueRestored[sid]
      localStorage.removeItem(queueKey(sid))
    },

    /** 删除会话时清理全部内存态与队列持久化 */
    clearSession(sid: string) {
      delete this.messagesBySession[sid]
      delete this.streamBySession[sid]
      delete this.unreadBySession[sid]
      this.clearQueue(sid)
      pendingHistory.delete(sid)
    },
  },
})
