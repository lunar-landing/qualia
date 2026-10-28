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

/** 会话上下文视图数据（输入框信号条，GET /api/chat/context） */
export interface ContextInfo {
  /** 历史消息估算占用（字符数×2，对齐后端 ContextManager 口径），供信号条点亮比例 */
  usedTokens: number
  /** 对话轮数（assistant 消息数） */
  turnCount: number
  /** 全会话真实 token 消耗（assistant 消息 totalTokens 累加） */
  sessionTotalTokens: number
  /** 当前模型上下文窗口上限（模型配置可选字段，未配置不返回 → 前端按默认值估算） */
  contextWindow?: number
}

/** 会话信息（后端 SessionInfo） */
export interface ChatSession {
  id: string
  title: string
  createdAt: number
  /** 绑定工作区绝对路径；null/缺省 = 派生沙箱（~/.qualia/code/workspaces/{sid}，可配置位置） */
  workspace?: string | null
  /** 工作区锁定：已绑定且已有对话，后端拒绝换绑/解绑（后端计算回传） */
  locked?: boolean
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
  /** 服务端消息 id（定时任务执行记录按此匹配渲染标注；缺省时前端生成稳定占位 id） */
  id?: string
  createdAt?: number
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

/** 定时任务执行记录（GET /api/schedules 任务内嵌，最近 20 条） */
export interface ScheduleRun {
  runAt: number
  status: 'ok' | 'error' | 'running'
  durationMs: number
  /** AI 回复首行摘要（后端截断，仅展示） */
  summary?: string | null
  /** 本轮新建的运行会话 id（查看轮次跳转目标） */
  sessionId?: string | null
  /** 本轮触发的 user 消息 id（会话流虚线气泡标注按此精确匹配） */
  userMsgId?: string | null
  /** 本轮 AI 回复消息 id（尾部执行摘要行按此精确匹配） */
  assistantMsgId?: string | null
  error?: string | null
}

/** 定时任务定义（标题 + 一条固定提示词 + cron 周期；运行会话由调度器每轮新建） */
export interface ScheduleDef {
  id: string
  /** 任务标题（卡面主信息；旧数据由后端按提示词首行补齐） */
  title: string
  prompt: string
  /** 标准 6 段 cron（秒 分 时 日 月 周，Spring CronExpression 方言） */
  cron: string
  /** 周期展示文本（如「每天 09:30」） */
  cronText: string
  enabled: boolean
  createdAt: number
  lastRunAt?: number | null
  lastStatus?: ScheduleRun['status'] | null
  nextRunAt?: number | null
  runs: ScheduleRun[]
}
