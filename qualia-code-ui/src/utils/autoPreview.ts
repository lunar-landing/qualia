import type { AgentStep } from '@/types'
import { stepFilePath } from '@/utils/steps'

/**
 * 回答完成后的自动预览候选提取：
 * 扫描本轮 steps 中的 write / edit 调用，挑出「写入与编辑行数最多」的 HTML 文件，
 * 供工作区浏览器区域（srcdoc）自动打开。非 HTML 不入候选——浏览器区域的渲染能力决定。
 */

/** 浏览器区域可渲染的扩展名 */
const HTML_EXT = /\.html?$/i

export interface HtmlWriteCandidate {
  /** 归一化的库内相对路径 */
  path: string
  /** 行数口径：write 记 content，edit 记 new_text */
  lines: number
  /** write（overwrite）的完整内容——免读盘直接 srcdoc；edit 为 null 需读工作区文件 */
  content: string | null
}

/** ACTION 的执行是否落盘：紧随 OBSERVATION 存在且非「错误：」开头（WriteTool/EditTool 失败文案） */
function succeeded(steps: AgentStep[], index: number): boolean {
  const next = steps[index + 1]
  return !!next && next.stepType === 'OBSERVATION' && !/^错误/.test(next.content)
}

function countLines(text: string): number {
  return text ? text.split('\n').length : 0
}

/** 本轮 HTML 写入候选中行数最多的一份；无 HTML 写入返回 null */
export function pickHtmlWrite(steps: AgentStep[]): HtmlWriteCandidate | null {
  let best: HtmlWriteCandidate | null = null
  steps.forEach((step, i) => {
    if (step.stepType !== 'ACTION' || !succeeded(steps, i)) return
    if (step.toolName !== 'write' && step.toolName !== 'edit') return
    const path = stepFilePath(step)
    if (!path || !HTML_EXT.test(path)) return
    const args = (step.toolArgs ?? {}) as Record<string, unknown>
    const isWrite = step.toolName === 'write'
    const text = isWrite ? args.content : args.new_text
    if (typeof text !== 'string' || !text) return
    const cand: HtmlWriteCandidate = {
      path,
      lines: countLines(text),
      content: isWrite ? text : null,
    }
    if (!best || cand.lines > best.lines) best = cand
  })
  return best
}
