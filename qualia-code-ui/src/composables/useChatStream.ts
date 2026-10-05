import { openStream } from '@/api/stream'
import type { StreamConnection, StreamFrame } from '@/api/stream'
import { i18n } from '@/i18n'
import { useChatStore } from '@/stores/chat'
import { useConfigStore } from '@/stores/config'
import { DEFAULT_SESSION_TITLE, useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { CHANGE_TOOLS, stepFilePath } from '@/utils/steps'
import { parseSkillSlash, rewriteSkillMessage } from '@/utils/skillSlash'
import type { AgentStep, HistoryAttachment, PendingAttachment, StreamEvent } from '@/types'

/** 流连接注册表：sid → 连接句柄。多会话并行流的句柄管理，连接对象不可序列化故不入 Pinia */
const connections = new Map<string, StreamConnection>()

/** 文件树瞬时读操作（read ACTION 置 R 徽标，对应 OBSERVATION 到达时清除） */
let pendingFileAction: { tool: string; path: string } | null = null

/** 接发防重入：send 在 ensureHistory await 窗口内尚未置流状态，连点立即发送会双发（模块级，跨组件共享） */
const consuming = new Set<string>()

/**
 * SSE 流式核心：旧 handleStreamEvent/streamChatPost/stopGeneration 的状态机化。
 * 流事件只写 store，无 sid === currentSessionId 判断——后台会话并行流天然支持，
 * 切回会话由响应式自动呈现（本次重构最大的简化点）。
 */
export function useChatStream() {
  /** 全局 Composer 实例：useI18n() 仅限 setup 栈内，而 tryConsumeQueue 会在 SSE 回调/按钮点击等非 setup 上下文重建本工厂 */
  const { t } = i18n.global
  /** 发送消息：无会话时先创建；带附件走 POST 通道，纯文本走 EventSource */
  async function send(text: string, pending: PendingAttachment[] = []) {
    const sessionStore = useSessionStore()
    const chat = useChatStore()
    const config = useConfigStore()

    if (!text.trim() && pending.length === 0) return

    // /技能 语法：发送前改写为标准指令文本（前端单点改写，实时回显/历史回读与发送内容天然一致）
    const slash = parseSkillSlash(text, config.skills)
    if (slash) text = rewriteSkillMessage(slash.rest, t('input.skillRewrite', { name: slash.name }))

    let sid = sessionStore.currentSessionId
    if (!sid) {
      sid = (await sessionStore.createSession()).id
    }
    // 同会话流进行中禁止重入（旧版 isProcessing 拦截）
    if (chat.isProcessing(sid)) return

    // 确保历史已落地，避免懒加载竞态覆盖本轮本地消息
    await chat.ensureHistory(sid)

    // 默认标题「新会话」就地替换为首条消息截断文本（与后端 getSessions 自动标题规则一致，免刷新同步）
    const session = sessionStore.sessions.find((s) => s.id === sid)
    if (session && (!session.title || session.title === DEFAULT_SESSION_TITLE)) {
      session.title = text.length > 20 ? `${text.slice(0, 20)}...` : text
    }

    const refs: HistoryAttachment[] = pending.map((a) => ({
      name: a.name,
      type: a.type === 'image' ? 'IMAGE' : 'DOCUMENT',
      dataUrl: a.objectUrl ?? null,
    }))

    chat.beginStream(sid, text, refs.length ? refs : undefined)
    useWorkspaceStore().clearTransientBadges()

    const conn = openStream(
      {
        sessionId: sid,
        message: text,
        model: config.selectedModel || undefined,
        attachmentIds: pending.length ? pending.map((a) => a.id) : undefined,
        readOnly: config.readOnly || undefined,
      },
      (frame) => handleFrame(sid, frame),
    )
    connections.set(sid, conn)
  }

  /** 停止当前会话生成（仅断开前端流，后端任务继续，对齐旧版 stopGeneration） */
  function stop() {
    const sid = useSessionStore().currentSessionId
    if (sid) stopSession(sid)
  }

  function stopSession(sid: string) {
    closeConn(sid)
    useChatStore().endStream(sid)
    useWorkspaceStore().clearTransientBadges()
  }

  /** 队列接发：会话空闲且无条目编辑时出队首条发送（finish 收尾 / 立即发送 / 入队后共用） */
  function consumeQueue(sid: string) {
    const chatStore = useChatStore()
    if (consuming.has(sid)) return
    if (chatStore.isProcessing(sid) || chatStore.queueEditing[sid]) return
    const next = chatStore.dequeue(sid)
    if (!next) return
    consuming.add(sid)
    void send(next.text, next.attachments ?? []).finally(() => consuming.delete(sid))
  }

  return { send, stop, stopSession, closeAll, consumeQueue }
}

/** 模块级接发入口（无 setup 上下文可用）：流收尾、面板按钮、入队后触发 */
export function tryConsumeQueue(sid: string) {
  useChatStream().consumeQueue(sid)
}

function closeConn(sid: string) {
  connections.get(sid)?.close()
  connections.delete(sid)
}

/** 工作区切换时关闭全部会话的流连接并收尾状态（旧 onWorkspaceSwitched 的连接清理部分） */
function closeAll() {
  const chat = useChatStore()
  for (const [sid, conn] of connections) {
    conn.close()
    chat.endStream(sid)
  }
  connections.clear()
  pendingFileAction = null
}

function handleFrame(sid: string, frame: StreamFrame) {
  switch (frame.kind) {
    case 'event':
      handleEvent(sid, frame.event)
      break
    case 'error':
      fail(sid, frame.message)
      break
    case 'done':
      finish(sid)
      break
  }
}

function handleEvent(sid: string, evt: StreamEvent) {
  const chat = useChatStore()
  const st = chat.streamBySession[sid]
  if (!st || (st.status !== 'connecting' && st.status !== 'streaming' && st.status !== 'answering')) return

  if (evt.responseType === 'answer' && evt.answer) {
    // 最终答案：写入活动消息；无步骤时降级为普通气泡
    st.fullAnswer = evt.answer
    chat.patchMessage(sid, st.msgId, { content: evt.answer })
    if (st.steps.length === 0) st.plainBubble = true
    st.status = 'answering'
  } else if (evt.responseType === 'step' && evt.steps?.length) {
    const step = evt.steps[0]
    if (!step) return
    st.steps.push(step)
    chat.appendStep(sid, st.msgId, step)
    if (st.status === 'connecting') st.status = 'streaming'
    applyStepSideEffects(step)
  }
}

/** 流正常收尾（[DONE]）：对齐旧版 done 分支 */
function finish(sid: string) {
  const chat = useChatStore()
  const st = chat.streamBySession[sid]
  closeConn(sid)
  // 空轮次清理占位消息（旧版不产生任何消息）
  if (st && !st.fullAnswer && st.steps.length === 0) {
    chat.removeMessage(sid, st.msgId)
  }
  const hasContent = !!st && (st.steps.length > 0 || !!st.fullAnswer)
  chat.endStream(sid)
  markUnreadIfBackground(sid, hasContent)
  useWorkspaceStore().clearTransientBadges()
  // 正常完成 → 自动接发待发送队列（错误/停止不触发，队列挂起待手动恢复）
  tryConsumeQueue(sid)
}

/** 流错误收尾：无内容时错误直接作为回复展示（对齐旧版 handleStreamError） */
function fail(sid: string, message: string) {
  const chat = useChatStore()
  const st = chat.streamBySession[sid]
  closeConn(sid)
  if (st && st.steps.length === 0 && !st.fullAnswer) {
    chat.appendErrorMessage(sid, message || '连接失败，请重试')
  }
  chat.endStream(sid, message)
  // 错误总视为有产出（错误气泡需要用户回来查看）
  markUnreadIfBackground(sid, true)
  useWorkspaceStore().clearTransientBadges()
}

/** 后台会话本轮有产出时标未读，切回时由 switchTo 清除；当前会话不标 */
function markUnreadIfBackground(sid: string, hasContent: boolean) {
  if (!hasContent) return
  if (useSessionStore().currentSessionId !== sid) {
    useChatStore().markUnread(sid)
  }
}

/**
 * 文件树活动徽标（平移旧 updateTreeActivity）：
 * read → R（瞬时，OBSERVATION 时清除）；write/edit → W，落定后转 M
 */
function applyStepSideEffects(step: AgentStep) {
  const ws = useWorkspaceStore()
  if (step.stepType === 'ACTION') {
    // 审查/终端 Tab 未激活时打提示圆点（对齐旧 record→notifyChangesTab / renderTerminal→dot；历史重建不打点）
    if (step.toolName && CHANGE_TOOLS.includes(step.toolName) && ws.activeTab !== 'wsChanges') {
      ws.tabDots.wsChanges = true
    }
    if (step.toolName === 'bash' && ws.activeTab !== 'wsTerm') {
      ws.tabDots.wsTerm = true
    }
    const fp = stepFilePath(step)
    if (!fp) {
      pendingFileAction = null
      return
    }
    if (step.toolName === 'read') {
      ws.setBadge(fp, 'R')
      pendingFileAction = { tool: 'read', path: fp }
    } else if (step.toolName === 'write' || step.toolName === 'edit') {
      ws.setBadge(fp, 'W')
      pendingFileAction = { tool: step.toolName, path: fp }
    } else {
      pendingFileAction = null
    }
  } else if (step.stepType === 'OBSERVATION' && pendingFileAction) {
    if (pendingFileAction.tool === 'read') {
      ws.removeBadge(pendingFileAction.path)
    } else {
      ws.setBadge(pendingFileAction.path, 'M')
    }
    pendingFileAction = null
  }
}
