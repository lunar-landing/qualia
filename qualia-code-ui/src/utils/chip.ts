/**
 * ToolChip 详情渲染纯函数集（对齐旧 tool-chip.js 内部工具函数）
 * 视图结构由 ChipDetail* 组件声明式渲染，这里只做数据解析
 */

/** 详情块文本上限（超出截断） */
export const OUT_LIMIT = 4000

export type ToolArgs = Record<string, unknown>

/** 截断超长文本 */
export function truncate(s: unknown, n: number): string {
  const str = String(s ?? '')
  return str.length > n ? str.slice(0, n) + '…' : str
}

/** 依次取第一个非空字符串参数值（兼容 path/file_path、pattern/regex 双命名） */
export function argStr(args: ToolArgs, ...keys: string[]): string {
  for (const k of keys) {
    const v = args[k]
    if (typeof v === 'string' && v) return v
  }
  return ''
}

/**
 * read 输出为 "%6d→内容"（每行带行号前缀），解析为编辑器行（行号列 + 代码列）；
 * 任一行不匹配（如错误消息）返回 null，由调用方回退普通代码块
 */
export function parseEditorLines(text: string): { num: string; txt: string }[] | null {
  // 后端 %n 在 Windows 下为 \r\n，先归一化换行
  const lines = truncate(text, OUT_LIMIT).replace(/\r\n?/g, '\n').split('\n')
  if (lines.length && lines[lines.length - 1] === '') lines.pop()
  const parsed: { num: string; txt: string }[] = []
  for (const line of lines) {
    const m = line.match(/^\s*(\d+)→(.*)$/)
    if (!m?.[1] || m[2] === undefined) return null
    parsed.push({ num: m[1], txt: m[2] })
  }
  return parsed.length ? parsed : null
}

/** 脚本注册名为 script_<技能名>_<文件基名>（框架内部命名），展示时剥离前缀还原短名 */
export function shortScriptName(name: unknown, skillName: unknown): string {
  if (!name) return ''
  const prefix = 'script_' + String(skillName ?? '').toLowerCase().replace(/[\s-]+/g, '_') + '_'
  const s = String(name)
  return s.startsWith(prefix) ? s.slice(prefix.length) : s
}

export interface SkillScriptRef {
  name: string
  desc: string
}

export interface SkillLoadRecord {
  doc: string
  desc: string
  meta: { icon: string; name: string }[]
  scripts: SkillScriptRef[]
  refs: string[]
}

/** skill-loader 输出为三段结构（【技能说明】/【可用脚本】/【附属文档】），解析失败返回 null 回退整段展示 */
export function parseSkillLoad(output: string, skillName: string): SkillLoadRecord | null {
  const text = String(output ?? '').replace(/\r\n?/g, '\n')
  const docM = text.match(/【技能说明】\n([\s\S]*?)(?=\n*【|$)/)
  if (!docM?.[1]) return null
  const doc = docM[1].trim()
  const scripts: SkillScriptRef[] = []
  const scriptsM = text.match(/【可用脚本】\n([\s\S]*?)(?=\n*【|$)/)
  if (scriptsM?.[1] !== undefined) {
    for (const line of scriptsM[1].split('\n')) {
      const m = line.match(/^- ([^:：]+)[:：]?\s*(.*)$/)
      if (m?.[1]) scripts.push({ name: shortScriptName(m[1].trim(), skillName), desc: (m[2] ?? '').trim() })
    }
  }
  const refs: string[] = []
  const refsM = text.match(/【附属文档】\n([\s\S]*?)(?=\n*【|$)/)
  if (refsM?.[1] !== undefined) {
    for (const line of refsM[1].split('\n')) {
      const m = line.match(/^- (.+)/)
      if (m?.[1]) refs.push(m[1].trim())
    }
  }
  const meta = scripts
    .map((s) => ({ icon: 'fa-bolt', name: s.name }))
    .concat(refs.map((r) => ({ icon: 'fa-book', name: r })))
  return { doc, desc: skillDesc(doc), meta, scripts, refs }
}

/** 技能说明为 skill.md 原文（可能带 YAML frontmatter），摘要优先取 frontmatter description */
export function skillDesc(doc: string): string {
  const fm = String(doc ?? '').match(/^---\s*\n([\s\S]*?)\n---/)
  if (fm?.[1]) {
    const m = fm[1].match(/^description:\s*['"]?(.+?)['"]?\s*$/m)
    if (m?.[1]) return m[1].trim()
  }
  const body = String(doc ?? '').replace(/^---\s*\n[\s\S]*?\n---\s*\n?/, '')
  for (const line of body.split('\n')) {
    const t = line.trim()
    if (t && !t.startsWith('#')) return t
  }
  return ''
}

/** 技能输出是否为错误（「错误：」开头），决定内容块错误色与折叠默认展开 */
export function isSkillError(text: unknown): boolean {
  return /^错误[:：]/.test(String(text ?? '').trim())
}

export interface SkillArgsParsed {
  pairs: { key: string; value: string }[]
  raw: string
}

/** 脚本入参为 JSON 字符串，解析为 key-value 对列表；解析失败按原文展示（raw） */
export function parseSkillArgs(argsJson: unknown): SkillArgsParsed | null {
  if (!argsJson) return null
  try {
    const obj = JSON.parse(String(argsJson)) as Record<string, unknown>
    return {
      pairs: Object.entries(obj).map(([k, v]) => ({
        key: k,
        value: typeof v === 'object' && v !== null ? JSON.stringify(v) : String(v),
      })),
      raw: '',
    }
  } catch {
    return { pairs: [], raw: truncate(String(argsJson), 200) }
  }
}

/** skill-selector 输出解析（JSON 数组）；空列表/无输出返回 null */
export function parseSkillSelectorOutput(output: string): { name: string; description?: string }[] | null {
  if (!output || output === '当前没有可用的技能。') return null
  try {
    const skills = JSON.parse(output) as { name?: string; description?: string }[]
    if (Array.isArray(skills) && skills.length > 0) {
      return skills.map((s) => ({ name: s.name ?? '', description: s.description }))
    }
  } catch {
    /* 非 JSON 回退整段展示 */
  }
  return null
}

// ===== 联网工具（web_search 族 / web_fetch，平移旧 browser.js 共享工具与解析器） =====

/** HTML 转义（textContent 语义：仅 & < >；供字符串拼装的内嵌预览页使用，模板插值由 Vue 转义） */
export function escHtml(s: unknown): string {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 去协议去尾斜杠的展示 URL */
export function displayUrl(url: unknown): string {
  return String(url ?? '').replace(/^https?:\/\//i, '').replace(/\/$/, '')
}

/** 域名提取（无协议时原样返回 host 部分） */
export function domainOf(url: unknown): string {
  const m = String(url ?? '').match(/^(?:https?:\/\/)?([^/?#]+)/i)
  return m?.[1] ?? String(url ?? '')
}

/** 补全协议（结果条目的 url 可能无 scheme） */
export function absUrl(url: unknown): string {
  const s = String(url ?? '')
  return s ? (/^https?:\/\//i.test(s) ? s : 'https://' + s) : ''
}

/** 搜索工具名 -> 来源徽章文案 */
export const WEB_SEARCH_SOURCES: Record<string, string> = {
  baidu_search: 'baidu',
  bing_search: 'bing',
  google_search: 'google',
  duckduckgo_search: 'duckduckgo',
  tavily_search: 'tavily',
}

export interface WebSearchItem {
  title: string
  url: string
  snippet: string
}

export interface WebSearchParsed {
  items: WebSearchItem[]
  /** Tavily 响应耗时（如 "1.2s"），无则不展示 */
  time?: string
}

/**
 * 搜索输出解析，兼容两种后端格式：
 * - 拼接文本（baidu/bing/google/duckduckgo）："N. 标题 / 链接: URL / 摘要: ..."（摘要支持多行续写）
 * - Tavily JSON：{items:[{title,url,content,score}], response_time}（error 或空 items 返回 null）
 */
export function parseWebSearch(output: string): WebSearchParsed | null {
  const t = String(output ?? '').trim()
  if (!t) return null
  return t.startsWith('{') ? parseTavilySearch(t) : parseTextSearch(t)
}

function parseTextSearch(t: string): WebSearchParsed | null {
  const items: WebSearchItem[] = []
  let cur: WebSearchItem | null = null
  let inSnippet = false
  for (const raw of t.split('\n')) {
    const line = raw.trim()
    let m: RegExpMatchArray | null
    if ((m = line.match(/^(\d+)\.\s+(.+)$/))) {
      cur = { title: m[2] ?? '', url: '', snippet: '' }
      items.push(cur)
      inSnippet = false
    } else if (cur && (m = line.match(/^链接[:：]\s*(.*)$/))) {
      cur.url = m[1] ?? ''
      inSnippet = false
    } else if (cur && (m = line.match(/^摘要[:：]\s*(.*)$/))) {
      cur.snippet = m[1] ?? ''
      inSnippet = true
    } else if (cur && inSnippet && line) {
      cur.snippet += ' ' + line
    }
  }
  return items.length ? { items } : null
}

function parseTavilySearch(t: string): WebSearchParsed | null {
  try {
    const o = JSON.parse(t) as {
      error?: unknown
      items?: { title?: string; url?: string; content?: string; snippet?: string }[]
      response_time?: unknown
    }
    if (o.error) return null
    const arr = Array.isArray(o.items) ? o.items : []
    const items = arr.map((it) => ({
      title: it.title || it.url || '',
      url: it.url || '',
      snippet: it.content || it.snippet || '',
    }))
    if (!items.length) return null
    const r: WebSearchParsed = { items }
    if (o.response_time != null) r.time = Number(o.response_time).toFixed(1) + 's'
    return r
  } catch {
    return null
  }
}

/** web_fetch 后端输出结构（status>=400 或 error 走错误态展示） */
export interface WebFetchParsed {
  url?: string
  status?: number
  content_type?: string
  title?: string
  content?: string
  extracted_length?: number
  error?: unknown
  message?: string
}

/** 抓取输出解析（必须为 JSON，否则返回 null 降级纯文本） */
export function parseWebFetch(output: string): WebFetchParsed | null {
  const t = String(output ?? '').trim()
  if (!t.startsWith('{')) return null
  try {
    return JSON.parse(t) as WebFetchParsed
  } catch {
    return null
  }
}

/** content-type 去掉 charset 等参数，只留主类型 */
export function shortContentType(ct: unknown): string {
  return String(ct ?? '').split(';')[0]?.trim() ?? ''
}

/** 抓取是否为错误态（error 字段或 HTTP 状态 >= 400） */
export function isFetchError(r: WebFetchParsed): boolean {
  return !!r.error || (r.status ?? 0) >= 400
}
