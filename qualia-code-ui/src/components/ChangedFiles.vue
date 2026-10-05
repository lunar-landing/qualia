<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWorkspaceStore } from '@/stores/workspace'
import { usePreviewStore } from '@/stores/preview'
import { readFileContent } from '@/api/config'
import { useTheme } from '@/composables/theme'
import { buildMarkdownPage, isMarkdownFile } from '@/utils/markdownPage'
import { baseName, changeKeyOf } from '@/utils/steps'
import type { AgentStep } from '@/types'

/**
 * 消息尾部变更清单（微染底色行）：
 * 从本条消息 steps 派生 edit/write/delete 变更，按文件聚合去重，
 * 整行铺超低透明度 op 色底（与审查面板徽标同色系），hover 以 1px 内描边提示可交互。
 * 「预览」按类型路由：html 走工作区面板浏览器 Tab 渲染效果（srcdoc），
 * md 转阅读页同走浏览器 Tab；其余文件走 ws.openFilePreview 预览层；删除文件无预览按钮。
 */
const props = defineProps<{ steps: AgentStep[] }>()
const { t } = useI18n()
const ws = useWorkspaceStore()
const previewStore = usePreviewStore()
const { isLight } = useTheme()

const OP_LABEL = computed<Record<string, string>>(() => ({
  write: t('changes.write'),
  edit: t('changes.edit'),
  delete: t('changes.delete'),
}))

interface ChangedFile {
  path: string
  op: 'write' | 'edit' | 'delete'
}

/** 文件终态 op（对齐审查面板 fileState）：末步 delete > 含 edit > write */
function opOf(steps: AgentStep[]): ChangedFile['op'] {
  if (steps[steps.length - 1]?.toolName === 'delete') return 'delete'
  return steps.some((s) => s.toolName === 'edit') ? 'edit' : 'write'
}

/** 按路径聚合本条消息的变更步骤（同文件多次操作合并为一条） */
const files = computed<ChangedFile[]>(() => {
  const byPath = new Map<string, AgentStep[]>()
  for (const s of props.steps) {
    const key = changeKeyOf(s)
    if (!key) continue
    const list = byPath.get(key)
    if (list) list.push(s)
    else byPath.set(key, [s])
  }
  return [...byPath].map(([path, steps]) => ({ path, op: opOf(steps) }))
})

/** 预览路由：html/md → 读内容走浏览器 Tab（md 转阅读页）；读取失败回落预览层；其余文件直接预览层 */
async function open(f: ChangedFile) {
  const htmlLike = /\.html?$/i.test(f.path)
  const mdLike = isMarkdownFile(f.path)
  if (!htmlLike && !mdLike) {
    ws.openFilePreview(f.path)
    return
  }
  try {
    const res = await readFileContent(f.path)
    if (res.type === 'text') {
      const content = String(res.content ?? '')
      // 第三参传文件语义路径：浏览器 Tab 地址栏显示面包屑并解锁 reveal 等文件动作
      previewStore.openHtml(
        mdLike ? buildMarkdownPage(content, isLight.value) : content,
        baseName(f.path),
        f.path,
      )
      return
    }
  } catch {
    // 读取失败回落文件预览层（其内有加载错误态 UI）
  }
  ws.openFilePreview(f.path)
}
</script>

<template>
  <div v-if="files.length" class="cfd">
    <div v-for="f in files" :key="f.path" class="cfd-row" :class="f.op">
      <span class="cfd-name" :title="f.path">{{ baseName(f.path) }}</span>
      <span class="cfd-op">{{ OP_LABEL[f.op] }}</span>
      <button v-if="f.op !== 'delete'" class="cfd-pv" @click="open(f)">
        {{ t('changes.preview') }}
        <i class="fas fa-arrow-up-right-from-square"></i>
      </button>
    </div>
  </div>
</template>

<style>
/* 消息尾部变更清单（cfd- 前缀全局；染底色与审查面板徽标同色系） */
.cfd { margin-top: 2px; display: flex; flex-direction: column; gap: 6px; }
.cfd-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 11px;
  border-radius: 9px;
  min-width: 0;
}
.cfd-row:hover { box-shadow: inset 0 0 0 1px var(--border-active); }
.cfd-row.write { background: rgba(52, 211, 153, 0.07); }
.cfd-row.edit { background: rgba(245, 158, 11, 0.07); }
.cfd-row.delete { background: var(--bg-hover); }
body.light-theme .cfd-row.write { background: rgba(5, 150, 105, 0.07); }
body.light-theme .cfd-row.edit { background: rgba(180, 83, 9, 0.06); }
body.light-theme .cfd-row.delete { background: #e9edf1; }
.cfd-name {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cfd-op { flex-shrink: 0; font-size: 11px; }
.cfd-row.write .cfd-op { color: #34d399; }
.cfd-row.edit .cfd-op { color: #f59e0b; }
.cfd-row.delete .cfd-op { color: var(--text-muted); }
body.light-theme .cfd-row.write .cfd-op { color: #059669; }
body.light-theme .cfd-row.edit .cfd-op { color: #b45309; }
.cfd-pv {
  margin-left: auto;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 11px;
  padding: 3px 10px;
  transition:
    border-color 0.12s,
    color 0.12s;
}
.cfd-pv:hover { border-color: var(--border-active); color: var(--text-primary); }
.cfd-pv i { font-size: 9.5px; }
</style>
