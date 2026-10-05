<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import hljs from 'highlight.js/lib/common'
import { readFileContent, rawFileUrl } from '@/api/config'
import { LANG_MAP, extOf, fileIcon, fmtSize } from '@/utils/fileMeta'
import { baseName } from '@/utils/steps'
import type { WorkspaceFileContent } from '@/types'

/**
 * 工作区文件预览（自 FileViewer 拆出的预览半边）：侧栏文件树点击后由工作区面板以整面板层展示。
 * 预览按类型渲染：text 行号 + hljs 高亮 / image 走 raw 字节 / binary 提示不支持；过期响应丢弃。
 */
const props = defineProps<{ path: string }>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()

const info = ref<WorkspaceFileContent | null>(null)
const loadError = ref('')
const loading = ref(false)

const headName = computed(() => info.value?.name || baseName(props.path))
const headMeta = computed(() => (info.value ? fmtSize(info.value.size) : ''))

const codeHtml = computed(() => {
  const inf = info.value
  if (!inf || inf.type !== 'text') return ''
  const content = String(inf.content ?? '').replace(/\r\n?/g, '\n')
  const lang = LANG_MAP[extOf(inf.name)]
  if (lang && hljs.getLanguage(lang)) {
    try {
      return hljs.highlight(content, { language: lang }).value
    } catch {
      /* 高亮失败按纯文本 */
    }
  }
  return escapeHtml(content)
})

const gutterText = computed(() => {
  const inf = info.value
  if (!inf || inf.type !== 'text') return ''
  const lines = String(inf.content ?? '').replace(/\r\n?/g, '\n').split('\n').length
  let nums = ''
  for (let i = 1; i <= lines; i++) nums += i + '\n'
  return nums
})

function escapeHtml(s: unknown): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 加载文件内容：过期响应丢弃（对齐旧 open 的竞态防护） */
async function load(path: string) {
  info.value = null
  loadError.value = ''
  loading.value = true
  try {
    const res = await readFileContent(path)
    if (props.path !== path) return
    // 后端读取失败以 200 + {error} 返回
    const err = (res as WorkspaceFileContent & { error?: string }).error
    if (err) loadError.value = err
    else info.value = res
  } catch (e) {
    if (props.path === path) loadError.value = e instanceof Error ? e.message : t('filePreview.loadFailed')
  } finally {
    if (props.path === path) loading.value = false
  }
}

watch(
  () => props.path,
  (path) => {
    void load(path)
  },
  { immediate: true },
)
</script>

<template>
  <div class="fp-root">
    <div class="fp-head">
      <button class="fp-back" :title="t('filePreview.back')" @click="emit('close')">
        <i class="fas fa-arrow-left"></i>
      </button>
      <i class="fas" :class="fileIcon(headName)"></i>
      <span class="fp-name" :title="path">{{ headName }}</span>
      <span class="fp-meta">{{ headMeta }}</span>
    </div>
    <div class="fp-body">
      <div v-if="loading" class="fp-notice"><i class="fas fa-circle-notch fa-spin"></i><span>{{ t('common.loading') }}</span></div>
      <div v-else-if="loadError" class="fp-notice">
        <i class="fas fa-triangle-exclamation"></i><span>{{ loadError }}</span>
      </div>
      <template v-else-if="info">
        <!-- text：行号列 + hljs 高亮 -->
        <template v-if="info.type === 'text'">
          <div class="fp-code-wrap">
            <div class="fp-gutter">{{ gutterText }}</div>
            <pre class="fp-code"><code class="hljs" v-html="codeHtml"></code></pre>
          </div>
          <div v-if="info.truncated" class="fp-truncated">
            <i class="fas fa-scissors"></i> {{ t('filePreview.truncated') }}
          </div>
        </template>
        <!-- image：raw 字节直出 -->
        <div v-else-if="info.type === 'image'" class="fp-imgwrap">
          <img :src="rawFileUrl(info.path)" :alt="info.name" />
        </div>
        <!-- binary：不支持预览 -->
        <div v-else class="fp-notice"><i class="fas fa-file-circle-question"></i><span>{{ t('filePreview.binary') }}</span></div>
      </template>
    </div>
  </div>
</template>

<style>
/* 平移旧 FileViewer 预览半边样式（fp- 前缀全局，依赖页面 CSS 变量） */
.fp-root { flex: 1; min-height: 0; min-width: 0; display: flex; flex-direction: column; }
.fp-head { display: flex; align-items: center; gap: 7px; padding: 7px 11px; border-bottom: 1px solid var(--border-color); flex-shrink: 0; min-width: 0; }
.fp-back { flex-shrink: 0; background: transparent; border: none; color: var(--text-secondary); font-size: 12px; padding: 4px 7px; margin-left: -5px; border-radius: 6px; cursor: pointer; transition: all 0.2s; }
.fp-back:hover { background: var(--bg-hover); color: var(--text-primary); }
.fp-head > i { color: var(--text-muted); font-size: 11.5px; flex-shrink: 0; }
.fp-name { font-size: 11.5px; font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-meta { font-size: 10.5px; color: var(--text-muted); flex-shrink: 0; }

.fp-body { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
/* 拉伸对齐：短内容时代码区/行号列仍铺满预览层高度（align-items 默认 stretch） */
.fp-code-wrap { flex: 1; overflow: auto; display: flex; font-family: 'JetBrains Mono', monospace; font-size: 11px; line-height: 1.6; }
.fp-gutter { position: sticky; left: 0; flex-shrink: 0; padding: 9px 9px 9px 13px; text-align: right; color: var(--text-muted); opacity: 0.65; white-space: pre; user-select: none; background: var(--bg-sidebar); border-right: 1px solid var(--border-color); }
.fp-code { margin: 0; padding: 9px 14px; }
.fp-code code { display: block; font-family: inherit; font-size: inherit; line-height: inherit; white-space: pre; background: transparent !important; padding: 0; }
.fp-truncated { flex-shrink: 0; padding: 5px 11px; border-top: 1px solid var(--border-color); color: var(--text-muted); font-size: 10.5px; }
.fp-truncated i { margin-right: 4px; }

.fp-notice { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: var(--text-muted); font-size: 11.5px; }
.fp-notice i { font-size: 20px; opacity: 0.6; }

.fp-imgwrap { flex: 1; overflow: auto; display: flex; align-items: center; justify-content: center; padding: 14px; }
.fp-imgwrap img { max-width: 100%; max-height: 100%; border-radius: 7px; }
</style>
