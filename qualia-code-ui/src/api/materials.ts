import { http } from './http'

/**
 * 资料 API：本地 Wiki 式资料库（一期：人工上传 + 查看）。
 * 文章以库内相对路径寻址（'/' 分隔，带 .md 后缀）；上传同步转换，无状态轮询。
 */

export interface MaterialInfo {
  id: string
  name: string
  description: string
  createdAt: number
  updatedAt: number
  articleCount: number
  totalChars: number
  /** 类型构成（kind → 篇数），列表页卡片底部构成条据此分段着色 */
  kindBreakdown: Partial<Record<MaterialKind, number>>
}

/** 文章展示类型：后端据 source/format 派生，前端据此选图标与默认视图 */
export type MaterialKind = 'markdown' | 'html' | 'pdf' | 'docx'

/** 文章树节点：文件夹或文章（path 为库内相对路径，文件夹以 / 结尾） */
export interface MaterialNode {
  type: 'folder' | 'file'
  name: string
  path: string
  updatedAt: number
  words: number | null
  summary: string | null
  kind: MaterialKind | null
  children: MaterialNode[] | null
}

export interface MaterialArticle {
  path: string
  title: string
  author: string
  source: string | null
  updatedAt: number
  words: number
  kind: MaterialKind
  content: string
}

/** 单文件上传回执（ok=false 时 error 为可读原因） */
export interface MaterialUploadResult {
  name: string
  ok: boolean
  path: string | null
  title: string | null
  words: number | null
  original: string | null
  error: string | null
}

const enc = encodeURIComponent

export function listMaterials(): Promise<MaterialInfo[]> {
  return http.get('/api/materials')
}

export function createMaterial(name: string, description: string): Promise<MaterialInfo> {
  return http.post('/api/materials', { name, description })
}

export function updateMaterial(id: string, patch: { name?: string; description?: string }): Promise<MaterialInfo> {
  return http.patch(`/api/materials/${enc(id)}`, patch)
}

export function deleteMaterial(id: string): Promise<{ success: boolean }> {
  return http.delete(`/api/materials/${enc(id)}`)
}

export function getMaterialTree(id: string): Promise<{ root: string; children: MaterialNode[] }> {
  return http.get(`/api/materials/${enc(id)}/tree`)
}

export function createMaterialFolder(id: string, name: string, parentPath?: string): Promise<{ path: string }> {
  return http.post(`/api/materials/${enc(id)}/folders`, { name, parentPath: parentPath || '' })
}

/** 上传文件（可多选）到指定文件夹，同步转换，返回逐文件回执 */
export function uploadMaterialFiles(
  id: string,
  files: File[],
  parentPath?: string,
): Promise<{ results: MaterialUploadResult[] }> {
  const form = new FormData()
  if (parentPath) form.append('parentPath', parentPath)
  for (const file of files) form.append('files', file)
  return http.postForm(`/api/materials/${enc(id)}/files`, form)
}

export function getMaterialArticle(id: string, path: string): Promise<MaterialArticle> {
  return http.get(`/api/materials/${enc(id)}/article?path=${enc(path)}`)
}

export function createMaterialArticle(id: string, name: string, parentPath?: string): Promise<{ path: string }> {
  return http.post(`/api/materials/${enc(id)}/article`, { name, parentPath: parentPath || '' })
}

export function saveMaterialArticle(id: string, path: string, content: string): Promise<{ success: boolean }> {
  return http.put(`/api/materials/${enc(id)}/article?path=${enc(path)}`, { content })
}

export function renameMaterialNode(id: string, path: string, name: string): Promise<{ success: boolean }> {
  return http.patch(`/api/materials/${enc(id)}/node?path=${enc(path)}`, { name })
}

export function deleteMaterialNode(id: string, path: string): Promise<{ success: boolean }> {
  return http.delete(`/api/materials/${enc(id)}/node?path=${enc(path)}`)
}

/** 上传原件访问地址（pdf 内联预览，其余下载） */
export function materialOriginalUrl(id: string, path: string): string {
  return `/api/materials/${enc(id)}/original?path=${enc(path)}`
}
