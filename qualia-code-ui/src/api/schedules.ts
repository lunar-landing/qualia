import { http } from './http'
import type { ScheduleDef } from '@/types'

/** 全部定时任务（含每任务最近 20 条执行记录） */
export function listSchedules(): Promise<ScheduleDef[]> {
  return http.get('/api/schedules')
}

/** 创建任务（cron 非法 400，ApiError.message 透传原因；运行会话由调度器每轮新建） */
export function createSchedule(
  title: string,
  prompt: string,
  cron: string,
  cronText: string,
): Promise<ScheduleDef> {
  return http.post('/api/schedules', { title, prompt, cron, cronText })
}

/** 标题 / 启停 / 改周期（均可选；改周期以 now 重算下次运行） */
export function updateSchedule(
  id: string,
  patch: { enabled?: boolean; cron?: string; cronText?: string; title?: string },
): Promise<ScheduleDef> {
  return http.put(`/api/schedules/${encodeURIComponent(id)}`, patch)
}

export function deleteSchedule(id: string): Promise<{ success: boolean }> {
  return http.delete(`/api/schedules/${encodeURIComponent(id)}`)
}

/** 手动立即执行一轮（不打乱周期；对话进行中后端 409） */
export function runScheduleNow(id: string): Promise<{ success: boolean }> {
  return http.post(`/api/schedules/${encodeURIComponent(id)}/run`)
}
