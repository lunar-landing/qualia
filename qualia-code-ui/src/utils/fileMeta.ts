/**
 * 文件元信息工具（平移旧 file-viewer.js 的扩展名映射）：
 * 扩展名 → hljs 语言 / 文件图标，以及文件大小格式化
 */

/** 扩展名 → hljs 语言（未命中或 hljs 未注册时按纯文本渲染） */
export const LANG_MAP: Record<string, string> = {
  js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'javascript',
  ts: 'typescript', tsx: 'typescript',
  java: 'java', py: 'python', go: 'go', rs: 'rust', rb: 'ruby', php: 'php',
  c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', hpp: 'cpp', cs: 'csharp', kt: 'kotlin', swift: 'swift',
  html: 'xml', htm: 'xml', xml: 'xml', vue: 'xml', svg: 'xml',
  css: 'css', scss: 'scss', less: 'less',
  json: 'json', md: 'markdown', yml: 'yaml', yaml: 'yaml',
  sh: 'bash', bash: 'bash', bat: 'dos', cmd: 'dos', ps1: 'powershell',
  sql: 'sql', gradle: 'groovy', properties: 'properties', ini: 'ini', toml: 'ini',
}

const FILE_ICONS: Record<string, string> = {
  js: 'fa-file-code', ts: 'fa-file-code', jsx: 'fa-file-code', tsx: 'fa-file-code',
  html: 'fa-file-code', css: 'fa-file-code', scss: 'fa-file-code',
  json: 'fa-file-code', xml: 'fa-file-code', yml: 'fa-file-code', yaml: 'fa-file-code',
  md: 'fa-file-alt', txt: 'fa-file-alt',
  java: 'fa-file-code', py: 'fa-file-code', go: 'fa-file-code', rs: 'fa-file-code',
  gradle: 'fa-file-code', properties: 'fa-cog',
  png: 'fa-file-image', jpg: 'fa-file-image', jpeg: 'fa-file-image', gif: 'fa-file-image', svg: 'fa-file-image',
}

export function extOf(name: string): string {
  const dot = String(name || '').lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

export function fileIcon(name: string): string {
  return FILE_ICONS[extOf(name)] || 'fa-file-alt'
}

export function fmtSize(n?: number): string {
  if (n == null) return ''
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  if (n >= 1024) return (n / 1024).toFixed(1) + ' KB'
  return n + ' B'
}
