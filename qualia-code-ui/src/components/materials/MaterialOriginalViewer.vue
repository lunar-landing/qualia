<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { renderAsync } from 'docx-preview'

/**
 * 富文档「原件」统一预览：pdf 走浏览器原生内嵌 iframe，docx 取原件字节用 docx-preview
 * 本地渲染成分页文档（页边距/样式近似 Word）。二者共用同一容器（定高 + 边框 + 加载/失败态），
 * 视觉规格严格一致。src/kind 变化重渲染；docx 失败可重试或回退打开原件。
 */
const props = defineProps<{ kind: 'pdf' | 'docx'; src: string }>()
const emit = defineEmits<{ open: [] }>()

const { t } = useI18n()
const host = ref<HTMLElement | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')

/** pdf：iframe @load 决定就绪；docx：fetch 字节后异步渲染 */
function start() {
  state.value = 'loading'
  if (props.kind === 'docx') {
    void renderDocx(props.src)
  }
}

async function renderDocx(src: string) {
  const el = host.value
  if (!el || !src) {
    state.value = 'error'
    return
  }
  el.innerHTML = ''
  try {
    const res = await fetch(src)
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    const buffer = await res.arrayBuffer()
    await renderAsync(buffer, el, undefined, { inWrapper: true, breakPages: true, ignoreLastRenderedPageBreak: false })
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

function onFrameLoad() {
  if (props.kind === 'pdf') {
    state.value = 'ready'
  }
}

onMounted(start)
watch(() => [props.src, props.kind], start)
</script>

<template>
  <div class="orig-viewer">
    <div v-if="state === 'loading'" class="ov-status">
      <i class="fas fa-circle-notch fa-spin"></i>{{ kind === 'docx' ? t('materials.docxLoading') : t('common.loading') }}
    </div>
    <div v-else-if="state === 'error'" class="ov-status">
      <i class="fas fa-triangle-exclamation"></i>{{ t('materials.docxRenderFailed') }}
      <div class="ov-acts">
        <button class="act-btn" @click="start"><i class="fas fa-rotate-right"></i>{{ t('common.retry') }}</button>
        <button class="act-btn primary" @click="emit('open')"><i class="fas fa-up-right-from-square"></i>{{ t('materials.openOriginal') }}</button>
      </div>
    </div>

    <!-- pdf：原生内嵌（iframe 自带滚动）；docx：渲染 host 常驻，就绪后显形 -->
    <iframe
      v-if="kind === 'pdf'"
      :src="src"
      class="ov-frame"
      :class="{ on: state === 'ready' }"
      title="PDF"
      @load="onFrameLoad"
    ></iframe>
    <div v-else ref="host" class="ov-docx" :class="{ on: state === 'ready' }"></div>
  </div>
</template>

<style scoped>
/* 满幅容器：填满阅读区（无卡片边框/圆角），pdf 与 docx 同足迹，内层各自滚动 */
.orig-viewer {
  position: relative;
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  background: var(--bg-app);
}
.ov-frame,
.ov-docx {
  width: 100%;
  height: 100%;
  border: none;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.ov-frame.on,
.ov-docx.on {
  opacity: 1;
}
.ov-docx {
  overflow: auto;
  background: var(--bg-codeblock);
}
/* docx 预览 = 冷雾灰桌面 + 白纸页幅：去库默认灰底与黑重投影，
   改细墨线 + 轻投影让纸浮起（两主题同一组色值，深色下影子自然隐没） */
.ov-docx :deep(.docx-wrapper) {
  background: transparent;
  gap: 16px;
  padding: 24px 16px;
}
.ov-docx :deep(.docx-wrapper > section.docx) {
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(31, 35, 40, 0.06), 0 8px 24px rgba(31, 35, 40, 0.07);
}
/* 加载 / 失败态覆盖层 */
.ov-status {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--bg-codeblock);
  color: var(--text-secondary);
  font-size: 12.5px;
}
.ov-status i {
  font-size: 20px;
  opacity: 0.7;
}
.ov-acts {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>

<style scoped>
/* 覆盖层内的按钮（与详情页 act-btn 同规格，组件自带避免外部作用域丢失） */
.orig-viewer .act-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
}
.orig-viewer .act-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.orig-viewer .act-btn.primary {
  background: var(--accent);
  color: var(--accent-ink);
  border-color: transparent;
  font-weight: 600;
}
</style>
