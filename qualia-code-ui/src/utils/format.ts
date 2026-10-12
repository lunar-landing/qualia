import { i18n } from '@/i18n'

/** 字节数人性化（平移旧 formatBytes） */
export function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + 'MB'
  if (bytes >= 1024) return Math.round(bytes / 1024) + 'KB'
  return bytes + 'B'
}

export interface DocIconMeta {
  icon: string
  cls: string
  ext: string
}

/** 按扩展名返回文档类型图标与配色（预览条、气泡 chip、历史标签共用） */
export function docIconMeta(name: string): DocIconMeta {
  const ext = (name && name.indexOf('.') >= 0 ? name.split('.').pop()! : '').toLowerCase()
  if (ext === 'pdf') return { icon: 'fa-file-pdf', cls: 'icon-pdf', ext: 'PDF' }
  if (ext === 'docx') return { icon: 'fa-file-word', cls: 'icon-word', ext: 'WORD' }
  if (ext === 'csv' || ext === 'xlsx') return { icon: 'fa-file-excel', cls: 'icon-excel', ext: ext.toUpperCase() }
  return { icon: 'fa-file-lines', cls: 'icon-text', ext: ext ? ext.toUpperCase() : 'TXT' }
}

/** 会话行尾时间（版本 A 规格）：今天 HH:mm / 昨天 / 周X（Intl 短星期随语言）/ 更早 M/D */
export function sessionRowTime(ts: number | undefined): string {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  const now = new Date()
  const dayStart = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime()
  const diffDays = Math.round((dayStart(now) - dayStart(d)) / 86400000)
  if (diffDays <= 0) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  if (diffDays === 1) return i18n.global.t('conversation.timeYesterday')
  if (diffDays < 7) return new Intl.DateTimeFormat(i18n.global.locale.value, { weekday: 'short' }).format(d)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** 路径末段名称（绑定 chip / 面板标题从 workspace 绝对路径派生显示名，兼容 \ 与 /） */
export function pathBaseName(path: string): string {
  const s = String(path || '').replace(/[\\/]+$/, '')
  const i = Math.max(s.lastIndexOf('\\'), s.lastIndexOf('/'))
  return i >= 0 ? s.slice(i + 1) : s
}

/** 相对时间：今天 HH:mm / 昨天 / N 天前（<7）/ yyyy-MM-dd（绑定弹窗与 chip 最近列表共用） */
export function relTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  const now = new Date()
  const dayStart = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime()
  const diffDays = Math.round((dayStart(now) - dayStart(d)) / 86400000)
  if (diffDays <= 0)
    return i18n.global.t('switcher.relToday', { time: `${pad(d.getHours())}:${pad(d.getMinutes())}` })
  if (diffDays === 1) return i18n.global.t('switcher.relYesterday')
  if (diffDays < 7) return i18n.global.t('switcher.daysAgo', { n: diffDays })
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** token 数缩写：980 → 980，1234 → 1.2k，123456 → 123k，1234567 → 1.2M */
export function fmtTokens(n: number | undefined | null): string {
  if (!n || n <= 0) return '0'
  if (n < 1000) return String(n)
  if (n < 1000000) return `${(n / 1000).toFixed(n < 10000 ? 1 : 0)}k`
  return `${(n / 1000000).toFixed(1)}M`
}

/** 当前时刻 HH:MM（消息时间戳展示） */
export function nowTimeStr(): string {
  const now = new Date()
  return (
    now.getHours().toString().padStart(2, '0') + ':' + now.getMinutes().toString().padStart(2, '0')
  )
}
