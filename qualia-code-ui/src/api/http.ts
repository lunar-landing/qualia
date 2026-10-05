/** API 错误：携带 HTTP 状态码与后端返回的 message/code */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 从错误响应体提取 message（后端统一用 message 或 error 字段） */
async function toApiError(res: Response): Promise<ApiError> {
  let message = `请求失败（${res.status}）`
  let code: string | undefined
  try {
    const data = await res.json()
    if (typeof data?.message === 'string') message = data.message
    else if (typeof data?.error === 'string') message = data.error
    if (typeof data?.code === 'string') code = data.code
  } catch {
    // 非 JSON 响应体，保留默认消息
  }
  return new ApiError(res.status, message, code)
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

function jsonInit(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }
}

export const http = {
  get: <T>(url: string) => request<T>(url),
  post: <T>(url: string, body?: unknown) => request<T>(url, jsonInit('POST', body)),
  postForm: <T>(url: string, form: FormData) => request<T>(url, { method: 'POST', body: form }),
  put: <T>(url: string, body?: unknown) => request<T>(url, jsonInit('PUT', body)),
  delete: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
}
