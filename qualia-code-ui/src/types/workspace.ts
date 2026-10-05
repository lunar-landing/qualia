/** 最近打开工作区条目 */
export interface WorkspaceRef {
  path: string
  name: string
  lastOpened?: string
  exists?: boolean
}

/** GET /api/workspace 响应；current 为 null 表示启动未绑定工作区（前端强制弹出选择） */
export interface WorkspaceInfo {
  current: { path: string; name: string } | null
  recent: WorkspaceRef[]
  streaming: boolean
}

export interface BrowseDir {
  name: string
  path: string
}

/** GET /api/workspace/browse 响应；path 为空时返回盘符列表并携带 home */
export interface BrowseResult {
  path: string
  /** 盘符根目录的上级回到盘符列表（空串） */
  parent: string | null
  dirs: BrowseDir[]
  home?: string
}

/** POST /api/workspace/switch 响应；NOT_FOUND/BUSY 为业务失败分支 */
export interface SwitchResult {
  success: boolean
  changed?: boolean
  workspace?: { path: string; name: string }
  code?: 'NOT_FOUND' | 'BUSY'
  message?: string
}

/** GET /api/config/files 条目 */
export interface WorkspaceFileInfo {
  name: string
  /** 相对工作区的 '/' 分隔路径 */
  path: string
  isDirectory: boolean
  size?: number
}

/** GET /api/config/file 响应；image 类型需另走 /api/config/file/raw 取字节 */
export interface WorkspaceFileContent {
  name: string
  path: string
  size: number
  type: 'text' | 'image' | 'binary'
  content?: string
  truncated?: boolean
}
