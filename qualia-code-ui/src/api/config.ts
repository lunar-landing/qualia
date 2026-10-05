import { http } from './http'
import type {
  AppConfig,
  AppConfigPatch,
  CurrentModel,
  MarketSkillInfo,
  McpServerConfig,
  SkillInfo,
  ToolInfo,
  WorkspaceFileContent,
  WorkspaceFileInfo,
} from '@/types'

export function getConfig(): Promise<AppConfig> {
  return http.get('/api/config')
}

/** 保存成功后新对话使用新配置（后端热生效） */
export function updateConfig(patch: AppConfigPatch): Promise<{ success: boolean; message: string }> {
  return http.put('/api/config', patch)
}

export function getCurrentModel(): Promise<CurrentModel> {
  return http.get('/api/config/model')
}

export function listMcpServers(): Promise<McpServerConfig[]> {
  return http.get('/api/config/mcp')
}

export function listTools(): Promise<ToolInfo[]> {
  return http.get('/api/config/tools')
}

export function listSkills(): Promise<SkillInfo[]> {
  return http.get('/api/config/skills')
}

export function deleteSkill(name: string): Promise<{ success: boolean; message: string }> {
  return http.delete(`/api/config/skills/${encodeURIComponent(name)}`)
}

/** 搜索技能市场（代理 skills.sh，条目含本地已安装标记） */
export function searchMarketSkills(query: string): Promise<MarketSkillInfo[]> {
  return http.get(`/api/config/skills/market/search?q=${encodeURIComponent(query)}`)
}

/** 从技能市场安装技能（GitHub 技能包 → ~/.qualia/code/skills/） */
export function installMarketSkill(id: string): Promise<{ success: boolean; name: string; message: string }> {
  return http.post('/api/config/skills/market/install', { id })
}

/** 在系统文件管理器中打开技能目录 */
export function revealSkillFolder(name: string): Promise<{ success: boolean }> {
  return http.post(`/api/config/skills/${encodeURIComponent(name)}/reveal`)
}

/** 读取全局系统提示词（~/.qualia/code/AGENT.md）；exists=false 时 content 为空 */
export function getAgentMd(): Promise<{ exists: boolean; content: string; defaultPrompt: string }> {
  return http.get('/api/config/agent-md')
}

/** 保存全局系统提示词并对当前 Agent 热生效；空内容 = 删除文件回退默认 */
export function saveAgentMd(content: string): Promise<{ success: boolean; message?: string }> {
  return http.put('/api/config/agent-md', { content })
}

/** 工作区文件列表；path 为空列根目录 */
export function listWorkspaceFiles(path = ''): Promise<WorkspaceFileInfo[]> {
  return http.get(`/api/config/files?path=${encodeURIComponent(path)}`)
}

/** 文件内容预览（text 含 content；image 需另走 rawFileUrl；binary 不可预览） */
export function readFileContent(path: string): Promise<WorkspaceFileContent> {
  return http.get(`/api/config/file?path=${encodeURIComponent(path)}`)
}

/** 图片预览原始字节 URL（直接作为 <img src>） */
export function rawFileUrl(path: string): string {
  return `/api/config/file/raw?path=${encodeURIComponent(path)}`
}

/** 在系统文件管理器中定位工作区文件（Windows 资源管理器 /select 选中，mac Finder reveal） */
export function revealWorkspaceFile(path: string): Promise<{ success: boolean }> {
  return http.post(`/api/config/file/reveal?path=${encodeURIComponent(path)}`)
}
