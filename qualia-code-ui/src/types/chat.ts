/** 后端 AgentStep.StepType 枚举序列化名 */
export type StepType = 'THOUGHT' | 'ACTION' | 'OBSERVATION' | 'ANSWER' | 'ERROR' | 'COMPRESS'

/** Agent 执行步骤（SSE step 事件与历史消息 steps 共用同一结构） */
export interface AgentStep {
  content: string
  stepType: StepType
  /** 仅 ACTION 类型有值 */
  toolName?: string
  /** 仅 ACTION 类型有值 */
  toolArgs?: Record<string, unknown>
  timestamp: number
}

/** SSE JSON 负载（后端 AgentResponse 的 fastjson 序列化） */
export interface StreamEvent {
  responseType: 'step' | 'answer'
  /** responseType=answer 时的最终答案 */
  answer?: string
  /** responseType=step 时为单元素数组 */
  steps?: AgentStep[]
  reasoningContent?: string
  totalSteps?: number
  errorMessage?: string
  usage?: Record<string, number>
}

/** 会话信息（后端 SessionInfo） */
export interface ChatSession {
  id: string
  title: string
  createdAt: number
}

/** 历史消息内附件引用（core 层 Attachment record 序列化） */
export interface HistoryAttachment {
  name: string
  type: 'IMAGE' | 'DOCUMENT'
  /** 图片直传视觉模型时的 dataUrl，历史回显优先用它，缺省走 /api/attachments/{sid}/file/{name} */
  dataUrl?: string | null
  /** 文档解析正文（前端不展示） */
  parsedContent?: string | null
}

/** 历史消息（GET /api/chat/sessions/{id}/messages） */
export interface HistoryMessage {
  role: string
  content: string
  attachments?: HistoryAttachment[]
  steps?: AgentStep[]
}

/** 前端统一消息模型：历史消息与流式期间的活跃消息合并为单轨（消除旧 DOM/Map 双轨） */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  attachments?: HistoryAttachment[]
  /** assistant 消息的执行步骤（ToolChip 列表数据源） */
  steps?: AgentStep[]
}

/** 每日 token 用量（GET /api/chat/stats/tokens） */
export interface TokenStat {
  date: string
  tokens: number
}

/** 待发送队列附件（上传回执元数据；刷新恢复后预览走会话回显接口，不存 objectUrl） */
export interface QueuedAttachment {
  id: string
  name: string
  type: string
  size: number
}

/** 待发送队列条目（per-session FIFO，持久化于 localStorage qualia:queue:{sid}） */
export interface QueuedMessage {
  id: string
  text: string
  attachments?: QueuedAttachment[]
}
