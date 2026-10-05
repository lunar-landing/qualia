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

/** 会话列表日期分组（今天/昨天/近7天/近30天/更早，文案随界面语言切换） */
export function sessionGroupLabel(ts: number | undefined): string {
  const t = i18n.global.t
  if (!ts) return t('conversation.groupEarlier')
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const dayMs = 24 * 60 * 60 * 1000
  if (ts >= startOfToday) return t('conversation.groupToday')
  if (ts >= startOfToday - dayMs) return t('conversation.groupYesterday')
  if (ts >= startOfToday - 7 * dayMs) return t('conversation.groupLast7')
  if (ts >= startOfToday - 30 * dayMs) return t('conversation.groupLast30')
  return t('conversation.groupEarlier')
}

/** 当前时刻 HH:MM（消息时间戳展示） */
export function nowTimeStr(): string {
  const now = new Date()
  return (
    now.getHours().toString().padStart(2, '0') + ':' + now.getMinutes().toString().padStart(2, '0')
  )
}
