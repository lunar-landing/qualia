import { defineStore } from 'pinia'
import { deleteSkill, getConfig, listSkills, updateConfig } from '@/api/config'
import type { SkillInfo } from '@/types'
import { useMaterialsStore } from './materials'
import { useSchedulesStore } from './schedules'

/**
 * 技能管理视图状态与数据：侧栏入口整栏覆盖主区，与定时任务/资料中心三视图互斥。
 * 数据独立于 config store 的启动快照（openView 时重拉，手工创建的 skill 目录随开随见）；
 * 启停落全局配置 disabledSkills（与设置页工具开关同字段），卸载走 DELETE 端点。
 */
export const useSkillsStore = defineStore('skills', {
  state: () => ({
    open: false,
    list: [] as SkillInfo[],
    /** 配置里被停用的技能名（enabled 判定 = 不在其中且后端未禁） */
    disabled: [] as string[],
    loading: false,
  }),

  getters: {
    enabledCount: (state) => state.list.filter((s) => s.enabled !== false && !state.disabled.includes(s.name)).length,
  },

  actions: {
    // ===== 视图开关（覆盖视图互斥：任务/资料/技能同时只开一个） =====
    toggleView() {
      if (this.open) this.closeView()
      else void this.openView()
    },
    async openView() {
      useMaterialsStore().closeView()
      useSchedulesStore().closeView()
      this.open = true
      await this.load()
    },
    closeView() {
      this.open = false
    },

    async load() {
      this.loading = true
      try {
        const [cfg, skills] = await Promise.all([getConfig(), listSkills()])
        this.disabled = [...(cfg.disabledSkills ?? [])]
        this.list = skills
      } finally {
        this.loading = false
      }
    },

    /** 手工在 ~/.qualia/skills/ 创建目录后的显式重扫（与开页共用加载，语义在按钮上） */
    rescan() {
      return this.load()
    },

    isEnabled(s: SkillInfo): boolean {
      return s.enabled !== false && !this.disabled.includes(s.name)
    },

    async setEnabled(name: string, enabled: boolean) {
      const next = enabled ? this.disabled.filter((n) => n !== name) : [...new Set([...this.disabled, name])]
      this.disabled = next
      await updateConfig({ disabledSkills: [...next] })
    },

    /** 卸载按目录名删除（dir 与显示名可能不同），并从 disabledSkills 清除残项 */
    async remove(s: SkillInfo) {
      await deleteSkill(s.dir || s.name)
      this.list = await listSkills()
      if (this.disabled.includes(s.name)) {
        this.disabled = this.disabled.filter((n) => n !== s.name)
        await updateConfig({ disabledSkills: [...this.disabled] })
      }
    },
  },
})
