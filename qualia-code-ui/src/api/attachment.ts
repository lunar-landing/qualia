import { http } from './http'
import type { AttachmentInfo } from '@/types'

/** 上传附件换取回执 ID；失败时 status='failed'（error 字段带原因） */
export function uploadAttachment(sessionId: string, file: File): Promise<AttachmentInfo> {
  const form = new FormData()
  form.append('sessionId', sessionId)
  form.append('file', file)
  return http.postForm('/api/attachments', form)
}

/** 历史消息图片回显 URL（[图片: xxx] 占位符场景，后端按文件名查附件仓库） */
export function historyImageUrl(sessionId: string, filename: string): string {
  return `/api/attachments/${encodeURIComponent(sessionId)}/file/${encodeURIComponent(filename)}`
}

export function getAttachmentStatus(
  sessionId: string,
  id: string,
): Promise<{ id: string; name: string; type: string; status: string }> {
  return http.get(`/api/attachments/${encodeURIComponent(id)}?sessionId=${encodeURIComponent(sessionId)}`)
}
