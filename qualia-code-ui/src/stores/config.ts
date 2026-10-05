import { defineStore } from 'pinia'
import { getConfig, getCurrentModel, listSkills, listTools, updateConfig, deleteSkill as apiDeleteSkill } from '@/api/config'
import type { AppConfigPatch, AppConfig, CurrentModel, SkillInfo, ToolInfo } from '@/types'

/** 设置与模型信息。主题切换独立在 composables/theme.ts（全局单例，非业务配置） */
export const useConfigStore = defineStore('config', {
  state: () => ({
    config: null as AppConfig | null,
    skills: [] as SkillInfo[],
    tools: [] as ToolInfo[],
    currentModel: null as CurrentModel | null,
    /** 前端选中的模型名（发消息随请求携带，不落后端配置；空=后端默认） */
    selectedModel: '',
    /** 会话模式偏好：agent=智能体（默认，可本地操作）| ask=只读问答（仅查询类工具）；localStorage 持久化，不入后端配置 */
    readOnly: localStorage.getItem('qualia.chatMode') === 'ask',
    loading: false,
    saving: false,
  }),

  getters: {
    models: (state) => state.config?.models ?? [],
    defaultModel: (state) => state.config?.defaultModel ?? '',
    workspacePath: (state) => state.config?.workspace ?? null,
    modelReady: (state) => !!state.currentModel?.configured,
  },

  actions: {
    /** 启动时全量加载（配置+技能+工具+当前模型） */
    async loadAll() {
      this.loading = true
      try {
        const [cfg, skills, tools, model] = await Promise.all([
          getConfig(),
          listSkills(),
          listTools(),
          getCurrentModel(),
        ])
        this.config = cfg
        this.skills = skills
        this.tools = tools
        this.currentModel = model
        if (!this.selectedModel && cfg.defaultModel) this.selectedModel = cfg.defaultModel
      } finally {
        this.loading = false
      }
    },

    /** 保存部分字段（apiKey 掩码规则由后端合并处理）；成功后回读并重置模型选择（对齐旧版 refreshModelSelector） */
    async save(patch: AppConfigPatch): Promise<{ success: boolean; message: string }> {
      this.saving = true
      try {
        const res = await updateConfig(patch)
        this.config = await getConfig()
        this.currentModel = await getCurrentModel()
        this.selectedModel = this.config?.defaultModel ?? ''
        return res
      } finally {
        this.saving = false
      }
    },

    async refreshSkills() {
      this.skills = await listSkills()
    },

    async deleteSkill(name: string) {
      await apiDeleteSkill(name)
      await this.refreshSkills()
    },

    selectModel(name: string) {
      this.selectedModel = name
    },

    /** 切换会话模式并持久化（模式不入会话存储，全局偏好跟随下一次发送） */
    setMode(mode: 'agent' | 'ask') {
      this.readOnly = mode === 'ask'
      localStorage.setItem('qualia.chatMode', mode)
    },
  },
})
