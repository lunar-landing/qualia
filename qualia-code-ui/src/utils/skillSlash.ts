import type { SkillInfo } from '@/types'

/** "/name" 首 token 的技能匹配结果 */
export interface SkillSlashMatch {
  /** 磁盘目录名（菜单 token） */
  dir: string
  /** frontmatter 显示名（改写文案与 skill-loader 标识） */
  name: string
  /** token 之后的剩余文本 */
  rest: string
}

const norm = (s: string) => s.toLowerCase().replace(/[\s_-]+/g, '-')

/**
 * 解析消息首 token "/name"：按目录名或显示名归一化匹配技能。
 * 命中返回改写所需信息；未命中返回 null（原样发送，模型当普通文本）。
 */
export function parseSkillSlash(text: string, skills: SkillInfo[]): SkillSlashMatch | null {
  const m = /^\/([^\s]+)(?:\s+([\s\S]*))?$/.exec(text.trim())
  if (!m) return null
  const tok = norm(m[1] ?? '')
  const hit = skills.find((s) => norm(s.dir || s.name) === tok || norm(s.name) === tok)
  if (!hit) return null
  return { dir: hit.dir || hit.name, name: hit.name, rest: (m[2] ?? '').trim() }
}

/**
 * 组装改写文本：指令头（locale 模板，技能名序列由 joinSkillNames 生成）+ 空行 + 剩余请求；
 * 改写结果是发送/回显/历史的唯一事实源
 */
export function rewriteSkillMessage(rest: string, template: string): string {
  return rest ? `${template}\n\n${rest}` : template
}

/** 改写头识别规则（与 i18n 模板一一对应；历史消息语言可能不同于当前界面语言，两套都试） */
const SKILL_HEADS: Array<{ open: string; close: string; next: string }> = [
  { open: '请使用技能「', close: '」', next: '「' },
  { open: 'Use the skill "', close: '"', next: '"' },
]

/** 回显标签化：识别改写头，返回头前缀 + 技能名列表（支持多个）+ 其后文案；未命中返回 null */
export function splitSkillMessage(text: string): { prefix: string; names: string[]; rest: string } | null {
  const t = text.trim()
  for (const { open, close, next } of SKILL_HEADS) {
    if (!t.startsWith(open)) continue
    const names: string[] = []
    let pos = open.length
    for (;;) {
      const end = t.indexOf(close, pos)
      if (end < 0 || end === pos) break
      names.push(t.slice(pos, end))
      pos = end + close.length
      // 紧邻的下一个开引号意味着还有更多技能名，否则停在文案处
      if (!t.startsWith(next, pos)) break
      pos += next.length
    }
    if (!names.length) continue
    return { prefix: open.slice(0, open.length - 1), names, rest: t.slice(pos).trim() }
  }
  return null
}

/** 改写头技能名序列拼接（与 i18n 模板配套：zh 闭合并列，en 空格并列以保持回显标签可识别） */
export function joinSkillNames(names: string[], zhLike: boolean): string {
  return zhLike ? names.map((n) => `「${n}」`).join('') : names.map((n) => `"${n}"`).join(' ')
}
