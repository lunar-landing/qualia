export type ModelType = 'pay-as-you-go' | 'token-plan'

export interface ModelConfig {
  name: string
  provider: string
  type: ModelType
  model: string
  baseUrl?: string
  /** GET 下发为掩码值（含 ****）；PUT 时空值或掩码值后端自动保留原配置 */
  apiKey?: string
}

export type McpTransport = 'streamable-http' | 'http-sse' | 'stdio'

export interface McpServerConfig {
  name: string
  transport?: McpTransport
  url?: string
  headers?: Record<string, string>
  enabled?: boolean
}

/** GET /api/config 响应 */
export interface AppConfig {
  /** 当前工作区绝对路径，启动未绑定时为 null */
  workspace: string | null
  defaultModel: string | null
  models: ModelConfig[]
  mcpServers: McpServerConfig[]
  disabledSkills: string[]
  disabledTools: string[]
  /** 派生工作区自定义存储位置；null = 默认 ~/.qualia/code/workspaces */
  derivedWorkspaceDir: string | null
  /** 派生工作区默认根路径（供输入框 placeholder 展示） */
  defaultDerivedDir: string
  configFile: string
}

/** PUT /api/config 请求体：仅传需要更新的字段 */
export type AppConfigPatch = Partial<
  Pick<AppConfig, 'defaultModel' | 'models' | 'mcpServers' | 'disabledSkills' | 'disabledTools' | 'derivedWorkspaceDir'>
>

/** PUT /api/config 响应：保存派生工作区存储位置时附带全量迁移统计 */
export interface SaveConfigResult {
  success: boolean
  message: string
  /** 全量迁移：已搬迁的派生工作区数（仅存储位置变更时返回） */
  migrated?: number
  /** 迁移失败数（文件占用中，下次会话解析自动重试） */
  failed?: number
}

/** GET /api/config/model 响应 */
export interface CurrentModel {
  configured: boolean
  name?: string
  provider?: string
  model?: string
  baseUrl?: string
  apiKey?: string
  message?: string
}

/** 内置工具定义（GET /api/config/tools） */
export interface ToolInfo {
  name: string
  description: string
  category: string
}

/** 全局技能（GET /api/config/skills） */
export interface SkillInfo {
  name: string
  /** 磁盘目录名（reveal/delete 按此定位；与 name 即 SKILL.md frontmatter 显示名可能不同） */
  dir?: string
  description: string
  source: string
  enabled: boolean
  scripts: string[]
  references: string[]
}

/** 技能脚本条目（GET /api/config/skills/{dir}/detail） */
export interface SkillScriptInfo {
  /** 运行期标识 script_{skill}_{base} */
  name: string
  description: string
  /** 脚本文件名（script/ 目录下） */
  file: string
}

/** 技能详情（GET /api/config/skills/{dir}/detail） */
export interface SkillDetail {
  name: string
  dir: string
  description: string
  /** SKILL.md frontmatter 键值（name/description/version/license 等） */
  meta: Record<string, string>
  /** SKILL.md 正文（frontmatter 已剥离），前端渲染 markdown */
  content: string
  scripts: SkillScriptInfo[]
  references: string[]
}

/** 技能市场条目（GET /api/config/skills/market/search） */
export interface MarketSkillInfo {
  /** owner/repo/skillId 三段式标识 */
  id: string
  name: string
  skillId: string
  /** 来源 GitHub 仓库或外部站点 */
  source: string
  installs: number
  /** source 是否为可直接安装的 GitHub 仓库 */
  installable: boolean
  installed: boolean
}
