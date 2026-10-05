import { http } from './http'
import type { BrowseResult, SwitchResult, WorkspaceInfo } from '@/types'

export function getWorkspaceInfo(): Promise<WorkspaceInfo> {
  return http.get('/api/workspace')
}

/** path 为空返回盘符列表；400 时抛 ApiError（message 为后端原因） */
export function browse(path?: string): Promise<BrowseResult> {
  const q = path ? `?path=${encodeURIComponent(path)}` : ''
  return http.get(`/api/workspace/browse${q}`)
}

/**
 * 切换工作区。成功时 changed 表示路径是否真的变化；
 * 目录不存在返回 { success:false, code:'NOT_FOUND' }（前端给「创建并打开」选项）；
 * 有对话流进行中返回 409（ApiError.code='BUSY'）。
 */
export function switchWorkspace(path: string, create = false): Promise<SwitchResult> {
  return http.post('/api/workspace/switch', { path, create })
}
