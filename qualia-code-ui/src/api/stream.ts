import type { StreamEvent } from '@/types'

/**
 * 归一化 SSE 帧：上层状态机（useChatStream）只消费这一层，
 * 不感知 EventSource / fetch 双通道差异。
 */
export type StreamFrame =
  | { kind: 'event'; event: StreamEvent }
  | { kind: 'error'; message: string }
  | { kind: 'done' }

export interface OpenStreamOptions {
  sessionId: string
  message: string
  model?: string
  /** 非空走 POST fetch 通道，否则走 GET EventSource */
  attachmentIds?: string[]
  /** 只读问答模式：仅查询类工具可用（GET 走 query 参数，POST 走 body 字段） */
  readOnly?: boolean
  signal?: AbortSignal
}

export interface StreamConnection {
  /** 主动断开（停止生成）。done/error 帧到达后通道已自动关闭 */
  close: () => void
}

/**
 * 打开流式连接（与后端协议一致的双通道自动选择）：
 * - 带附件：POST /api/chat/stream（fetch ReadableStream + \n\n 分帧）
 * - 纯文本：GET /api/chat/stream（EventSource）
 */
export function openStream(opts: OpenStreamOptions, onFrame: (frame: StreamFrame) => void): StreamConnection {
  return opts.attachmentIds?.length ? openPostStream(opts, onFrame) : openEventSource(opts, onFrame)
}

/**
 * 解析单个 SSE data 负载为归一化帧。
 * 负载三种形态：JSON 事件 / {"error":"..."} / 纯文本错误（"发生错误: 原因"）/ [DONE]
 */
export function parseSsePayload(payload: string): StreamFrame {
  const text = payload.trim()
  if (text === '[DONE]') return { kind: 'done' }
  if (!text.startsWith('{')) {
    // failFast 纯文本错误；前缀剥离后保留原因
    const message = text.startsWith('发生错误') ? text.replace(/^发生错误[:：]\s*/, '') : text
    return { kind: 'error', message: message || '未知错误' }
  }
  try {
    const data = JSON.parse(text) as StreamEvent & { error?: string }
    if (typeof data.error === 'string') return { kind: 'error', message: data.error }
    return { kind: 'event', event: data }
  } catch {
    return { kind: 'error', message: text || '消息解析失败' }
  }
}

/** GET 通道：EventSource（浏览器自动分帧；服务端 complete 后关闭防自动重连） */
function openEventSource(opts: OpenStreamOptions, onFrame: (frame: StreamFrame) => void): StreamConnection {
  const params = new URLSearchParams({ sessionId: opts.sessionId, message: opts.message })
  if (opts.model) params.set('model', opts.model)
  if (opts.readOnly) params.set('readOnly', 'true')
  const es = new EventSource(`/api/chat/stream?${params.toString()}`)

  let finished = false
  let endedByFrame = false

  const finish = () => {
    if (finished) return
    finished = true
    es.close()
  }

  es.onmessage = (e) => {
    const frame = parseSsePayload(e.data)
    onFrame(frame)
    if (frame.kind !== 'event') {
      endedByFrame = true
      finish()
    }
  }
  es.onerror = () => {
    finish()
    // 未收到过结束帧就断开：视为连接中断（正常结束后的连接关闭静默处理）
    if (!endedByFrame) onFrame({ kind: 'error', message: '连接中断' })
  }
  if (opts.signal) {
    opts.signal.addEventListener('abort', finish, { once: true })
  }
  return { close: finish }
}

/** POST 通道：fetch ReadableStream 手动分帧（逻辑平移旧 streamChatPost） */
function openPostStream(opts: OpenStreamOptions, onFrame: (frame: StreamFrame) => void): StreamConnection {
  const controller = new AbortController()
  const signal = combineSignals(opts.signal, controller.signal)

  let finished = false
  let endedByFrame = false

  const finish = () => {
    if (finished) return
    finished = true
    controller.abort()
  }

  void (async () => {
    try {
      const res = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: opts.sessionId,
          message: opts.message,
          model: opts.model,
          attachmentIds: opts.attachmentIds,
          readOnly: opts.readOnly ?? false,
        }),
        signal,
      })
      if (!res.ok || !res.body) {
        onFrame({ kind: 'error', message: `请求失败（${res.status}）` })
        return
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buf = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        let idx: number
        while ((idx = buf.indexOf('\n\n')) >= 0) {
          const rawFrame = buf.slice(0, idx)
          buf = buf.slice(idx + 2)
          const payload = extractDataPayload(rawFrame)
          if (payload === null) continue
          const frame = parseSsePayload(payload)
          onFrame(frame)
          if (frame.kind !== 'event') {
            endedByFrame = true
            return
          }
        }
      }
      // 服务端 complete 关闭连接：若从未收到结束帧（如中途断连），按错误收尾
      if (!endedByFrame) onFrame({ kind: 'error', message: '连接中断' })
    } catch (err) {
      // 主动 close()/外部 signal 中止：上层自行处理，此处静默
      if (!endedByFrame && (err as Error)?.name !== 'AbortError') {
        onFrame({ kind: 'error', message: (err as Error)?.message || '网络错误' })
      }
    }
  })()

  return { close: finish }
}

/** 提取 SSE 帧内全部 data: 行内容（payload 含换行时被拆成多行，按规范还原） */
function extractDataPayload(rawFrame: string): string | null {
  const lines = rawFrame.split('\n').filter((line) => line.startsWith('data:'))
  if (lines.length === 0) return null
  return lines.map((line) => line.slice(5).replace(/^ /, '')).join('\n')
}

/** 合并外部 signal 与内部 controller（AbortSignal.any 的保守替代，兼容旧 WebView） */
function combineSignals(external: AbortSignal | undefined, internal: AbortSignal): AbortSignal {
  if (!external) return internal
  const combined = new AbortController()
  const abort = () => combined.abort()
  if (external.aborted || internal.aborted) {
    combined.abort()
    return combined.signal
  }
  external.addEventListener('abort', abort, { once: true })
  internal.addEventListener('abort', abort, { once: true })
  return combined.signal
}
