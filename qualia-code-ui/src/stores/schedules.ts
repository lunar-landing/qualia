import { defineStore } from 'pinia'
import {
  listSchedules,
  createSchedule,
  updateSchedule,
  deleteSchedule,
  runScheduleNow,
} from '@/api/schedules'
import { useMaterialsStore } from '@/stores/materials'
import type { ScheduleDef, ScheduleRun } from '@/types'

/**
 * 定时任务：全局任务列表 + 整栏任务中心视图开关 + 新建/修改弹窗开关 + 会话流标注索引。
 * 创建/启停/删除就地同步；有任务时 30s 轮询刷新运行状态与新执行结果。
 * 任务与会话解耦：运行会话由后端每轮新建，仅执行记录里保存 sessionId。
 */
export const useSchedulesStore = defineStore('schedules', {
  state: () => ({
    /** 整栏任务中心视图开关（侧栏「定时任务」入口） */
    open: false,
    list: [] as ScheduleDef[],
    loading: false,

    /** 新建/修改任务弹窗：create / edit（绑定任务 id）；App 层挂 ScheduleDialog */
    dialog: null as { mode: 'create' } | { mode: 'edit'; defId: string } | null,
  }),

  getters: {
    /** 消息 id → 执行标注：user 消息虚线气泡与 assistant 尾部摘要行的唯一数据源 */
    markByMsgId: (s): Record<string, { run: ScheduleRun; def: ScheduleDef; kind: 'user' | 'assistant' }> => {
      const out: Record<string, { run: ScheduleRun; def: ScheduleDef; kind: 'user' | 'assistant' }> = {}
      for (const def of s.list) {
        for (const run of def.runs) {
          if (run.userMsgId) out[run.userMsgId] = { run, def, kind: 'user' }
          if (run.assistantMsgId) out[run.assistantMsgId] = { run, def, kind: 'assistant' }
        }
      }
      return out
    },

    /** 会话 id → 运行来源：任务运行会话在会话列表的时钟标识数据源（与 markByMsgId 同模式） */
    runSessionById: (s): Record<string, { def: ScheduleDef; run: ScheduleRun }> => {
      const out: Record<string, { def: ScheduleDef; run: ScheduleRun }> = {}
      for (const def of s.list) {
        for (const run of def.runs) {
          if (run.sessionId) out[run.sessionId] = { def, run }
        }
      }
      return out
    },
  },

  actions: {
    // ===== 视图开关 =====
    toggleView() {
      if (this.open) this.closeView()
      else void this.openView()
    },
    async openView() {
      useMaterialsStore().closeView() // 覆盖视图互斥：任务与资料同时只开一个
      this.open = true
      await this.load()
    },
    closeView() {
      this.open = false
    },

    async load() {
      this.loading = true
      try {
        this.list = await listSchedules()
        syncPolling(this.list.length)
      } finally {
        this.loading = false
      }
    },

    /** 创建任务：本地置顶（错误透传给调用方提示） */
    async create(prompt: string, cron: string, cronText: string): Promise<ScheduleDef> {
      const def = await createSchedule(prompt, cron, cronText)
      this.list = [def, ...this.list.filter((d) => d.id !== def.id)]
      syncPolling(this.list.length)
      return def
    },

    /** 响应就地替换（启停/改周期后回写） */
    applyUpdated(def: ScheduleDef) {
      const idx = this.list.findIndex((d) => d.id === def.id)
      if (idx >= 0) this.list[idx] = def
    },

    async setEnabled(def: ScheduleDef, enabled: boolean) {
      this.applyUpdated(await updateSchedule(def.id, { enabled }))
    },

    async remove(id: string) {
      await deleteSchedule(id)
      this.list = this.list.filter((d) => d.id !== id)
      syncPolling(this.list.length)
    },

    /** 手动立即执行一轮：忙时抛 ApiError（409），成功后重拉标记 running */
    async runNow(id: string) {
      await runScheduleNow(id)
      await this.load()
    },

    // ===== 新建/修改弹窗 =====
    /** 打开新建弹窗 */
    openDialog() {
      this.dialog = { mode: 'create' }
    },
    /** 打开修改周期弹窗 */
    openEditDialog(defId: string) {
      this.dialog = { mode: 'edit', defId }
    },
    closeDialog() {
      this.dialog = null
    },
  },
})

/** 有任务时 30s 轮询（模块级定时器；清空任务即摘除） */
let pollTimer: number | null = null
function syncPolling(count: number) {
  if (count > 0 && pollTimer == null) {
    pollTimer = window.setInterval(() => {
      void useSchedulesStore().load()
    }, 30000)
  } else if (count === 0 && pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}
