import type { AgentStep } from '@/types'

/** 路径归一化（平移旧 changes-panel.normPath）：反斜杠转斜杠、去 ./ 前缀 */
export function normPath(p: unknown): string {
  return String(p ?? '')
    .replace(/\\/g, '/')
    .replace(/^\.\//, '')
}

export function baseName(p: string): string {
  const parts = p.split('/').filter(Boolean)
  return parts[parts.length - 1] || p
}

/** 步骤参数中的文件路径（file_path 优先，兼容 path） */
export function stepFilePath(step: AgentStep): string {
  const args = (step.toolArgs ?? {}) as Record<string, unknown>
  return normPath(args.file_path ?? args.path) || ''
}

/** 提取思考内容（THOUGHT 步骤的 content 为 {"thought": "..."} JSON） */
export function extractThought(step: AgentStep): string {
  try {
    const content: unknown = JSON.parse(step.content)
    if (content && typeof content === 'object' && 'thought' in content) {
      const t = (content as { thought?: unknown }).thought
      if (typeof t === 'string') return t
    }
  } catch {
    // 非 JSON 思考内容原样返回
    return step.content || ''
  }
  return ''
}

export interface TerminalBlock {
  cmd: string
  /** null 表示命令仍在执行（后面既无输出也无新动作） */
  output: string | null
}

/** 终端面板块提取（平移旧 terminal-panel.extractBlocks）：bash ACTION 与紧随 OBSERVATION 配对 */
export function extractBlocks(steps: AgentStep[]): TerminalBlock[] {
  const blocks: TerminalBlock[] = []
  steps.forEach((step, i) => {
    if (step.stepType !== 'ACTION' || step.toolName !== 'bash') return
    const args = (step.toolArgs ?? {}) as Record<string, unknown>
    const cmd = typeof args.command === 'string' ? args.command : ''
    let output: string | null = null
    for (let j = i + 1; j < steps.length; j++) {
      const next = steps[j]
      if (!next) break
      if (next.stepType === 'OBSERVATION') {
        output = next.content || ''
        break
      }
      if (next.stepType === 'ACTION') {
        output = ''
        break
      }
    }
    blocks.push({ cmd, output })
  })
  return blocks
}

/** 可记录为文件变更的工具（平移旧 CHANGE_TOOLS） */
export const CHANGE_TOOLS = ['edit', 'write', 'delete']

/** 步骤是否为可记录的文件变更，是则返回归一化路径，否则返回空串 */
export function changeKeyOf(step: AgentStep): string {
  if (step.stepType !== 'ACTION' || !step.toolName || !CHANGE_TOOLS.includes(step.toolName)) return ''
  return stepFilePath(step)
}
