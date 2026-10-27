/**
 * 定时任务创建浮层的周期工具：内置模式（每天 / 工作日 / 每周N）折算标准 cron，
 * 并在前端预览下次运行时间；自定义 cron 直填交后端校验（无预览）。
 */

export type SchedMode = 'daily' | 'weekday' | 'weekly' | 'custom'

/** JS 周序（0=周日）→ cron 周名，周几选择器用 1-7（周一=1，周日=7） */
const WEEKDAY_NAMES = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

/** 模式 + 时间 + 周几 → 6 段 cron（秒 分 时 日 月 周，Spring CronExpression 方言） */
export function buildCron(mode: SchedMode, time: string, weekday: number): string {
  const [h, m] = parseTime(time)
  if (mode === 'daily') return `0 ${m} ${h} * * *`
  if (mode === 'weekday') return `0 ${m} ${h} * * MON-FRI`
  if (mode === 'weekly') return `0 ${m} ${h} * * ${WEEKDAY_NAMES[weekday % 7]}`
  return ''
}

/** "HH:mm" → [小时, 分钟]（非法回落 09:00） */
export function parseTime(time: string): [number, number] {
  const parts = time.split(':').map((x) => parseInt(x, 10))
  const h = Number.isFinite(parts[0]) ? (parts[0] as number) : 9
  const m = Number.isFinite(parts[1]) ? (parts[1] as number) : 0
  return [h, m]
}

/**
 * 内置模式的下次运行预览（纯前端估算，创建后以后端为准）：从现在起找第一个
 * 匹配时刻；custom 返回 null（交后端 CronExpression 计算）
 */
export function previewNextRun(mode: SchedMode, time: string, weekday: number): number | null {
  if (mode === 'custom') return null
  const [h, m] = parseTime(time)
  const at = (base: Date) => {
    const d = new Date(base)
    d.setHours(h, m, 0, 0)
    return d
  }
  const now = new Date()
  let d = at(now)
  if (d.getTime() <= now.getTime()) d = at(new Date(now.getTime() + 86400000))
  if (mode === 'weekday') {
    while (d.getDay() === 0 || d.getDay() === 6) d = at(new Date(d.getTime() + 86400000))
  } else if (mode === 'weekly') {
    const target = weekday % 7
    while (d.getDay() !== target) d = at(new Date(d.getTime() + 86400000))
  }
  return d.getTime()
}

/**
 * 解析内置三模式的 cron 为表单参数（修改周期时回填选择器）：
 * 非内置格式（自定义表达式）返回 null，调用方切到 custom 模式直填。
 */
export function parseCronParts(
  cron: string,
): { mode: Exclude<SchedMode, 'custom'>; time: string; weekday: number } | null {
  const parts = cron.trim().split(/\s+/)
  if (parts.length !== 6 || parts[0] !== '0' || parts[3] !== '*' || parts[4] !== '*') return null
  const m = parseInt(parts[1] ?? '', 10)
  const h = parseInt(parts[2] ?? '', 10)
  if (!Number.isFinite(h) || !Number.isFinite(m) || h > 23 || m > 59) return null
  const time = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
  const dow = parts[5] ?? ''
  if (dow === '*') return { mode: 'daily', time, weekday: 1 }
  if (dow === 'MON-FRI') return { mode: 'weekday', time, weekday: 1 }
  const idx = WEEKDAY_NAMES.indexOf(dow)
  if (idx > 0) return { mode: 'weekly', time, weekday: idx }
  if (idx === 0) return { mode: 'weekly', time, weekday: 7 }
  return null
}

/** 执行耗时展示：<1s 记毫秒，<60s 记秒，否则记分秒（对齐任务历史摘要行） */
export function fmtDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const s = Math.round(ms / 1000)
  if (s < 60) return `${s}s`
  return `${Math.floor(s / 60)}m ${s % 60}s`
}

/** 执行时间点展示：MM-DD HH:mm（执行记录与来源行共用） */
export function fmtRunTime(ts: number | undefined | null): string {
  if (!ts) return ''
  const d = new Date(ts)
  const p = (x: number) => String(x).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 下次触发相对倒计时（任务卡面主信息）：「X 分钟/小时后」「X 天后」，已到点记「即将触发」 */
export function fmtRelNext(ts: number, en = false): string {
  const diff = ts - Date.now()
  if (diff <= 0) return en ? 'any moment' : '即将触发'
  const m = Math.floor(diff / 60000)
  if (m < 60) return en ? `in ${m} min` : `${m} 分钟后`
  const h = Math.floor(m / 60)
  if (h < 24) return en ? `in ${h}h` : `${h} 小时后`
  return en ? `in ${Math.floor(h / 24)}d` : `${Math.floor(h / 24)} 天后`
}
