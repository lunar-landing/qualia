/** POST /api/attachments 上传回执（后端 AttachmentInfo） */
export interface AttachmentInfo {
  /** 解析成功时为回执 ID，失败为 null */
  id: string | null
  name: string
  type: string | null
  status: 'ready' | 'failed'
  error?: string
}

/** 输入区待发送附件（上传回执 + 本地预览信息） */
export interface PendingAttachment {
  id: string
  name: string
  /** 后端回执小写：'image' | 'document' */
  type: string
  size: number
  /** 图片本地预览 URL（blob:），发送时作为消息内附件的 dataUrl */
  objectUrl?: string
}
