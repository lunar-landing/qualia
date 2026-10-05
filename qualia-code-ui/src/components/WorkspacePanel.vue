<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWorkspaceStore } from '@/stores/workspace'
import { usePreviewStore } from '@/stores/preview'
import ChangesPanel from './ChangesPanel.vue'
import TerminalPanel from './TerminalPanel.vue'
import BrowserPane from './BrowserPane.vue'

/**
 * 工作区面板壳（平移旧 index.html 的 steps-panel 结构与 toggleStepsPanel/toggleWsMaximize/initWsResize 逻辑）：
 * 审查 / 终端 / 浏览器三 Tab（文件列表已移至侧栏，点击侧栏文件在聊天区整栏预览）；
 * 左缘拖拽调宽（252 ~ 视口余宽，不持久化）；
 * 放大转为覆盖层从右向左铺开盖住聊天区（占位符钉住布局避免重排）。
 * 浏览器 Tab 由 BrowserPane 渲染：地址栏工具栏（面包屑/URL + 刷新/reveal/外开/复制）
 * + srcdoc（HTML 预览）与外链网页双 iframe，沙箱策略对齐旧版。
 */
const { t } = useI18n()
const ws = useWorkspaceStore()
const preview = usePreviewStore()

// label 存字典 key，渲染时 t() 翻译，保证切语言同步
const TABS = [
  { id: 'wsChanges', icon: 'fa-code-pull-request', label: 'wsPanel.changes' },
  { id: 'wsTerm', icon: 'fa-terminal', label: 'wsPanel.terminal' },
  { id: 'wsPreview', icon: 'fa-globe', label: 'wsPanel.browser' },
] as const

const panelEl = ref<HTMLElement | null>(null)

// ===== 宽度拖拽：null = CSS 默认 420px；同一套钳制规则供程序化调宽复用 =====
const width = ref<number | null>(null)
const resizing = ref(false)

function clampWidth(w: number): number {
  // 上限受视口约束：给侧边栏+聊天区保留至少 690px
  return Math.min(Math.max(252, window.innerWidth - 690), Math.max(252, w))
}

function startResize(e: MouseEvent) {
  e.preventDefault()
  const panel = panelEl.value
  if (!panel) return
  resizing.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  const onMove = (ev: MouseEvent) => {
    width.value = clampWidth(panel.getBoundingClientRect().right - ev.clientX)
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    resizing.value = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

const panelStyle = computed(() => {
  if (width.value == null) return undefined
  return { width: width.value + 'px', minWidth: width.value + 'px' }
})

// ===== 最大化两阶段动画（对齐旧 toggleWsMaximize）=====
// 进入：以当前宽度进 overlay（右缘不动）→ 双 rAF 后置 maximized 触发宽度过渡；占位符接管原 flex 位置
// 还原：先撤 maximized 滑回原宽，动画结束后退出 overlay 并撤占位符
const overlaying = ref(false)
const heldWidth = ref(0)

function toggleMax() {
  if (!ws.panelMaximized) {
    heldWidth.value = panelEl.value?.getBoundingClientRect().width ?? 0
    overlaying.value = true
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        ws.panelMaximized = true
      }),
    )
  } else {
    ws.panelMaximized = false
    setTimeout(() => {
      overlaying.value = false
    }, 330)
  }
}

// 关闭面板时立即清理放大态（不播还原动画，避免与折叠动画打架）；同帧撤 overlay
function closePanel() {
  overlaying.value = false
  ws.togglePanel()
}

// 兜底：面板折叠时保证退出覆盖态
watch(
  () => ws.panelCollapsed,
  (collapsed) => {
    if (collapsed) overlaying.value = false
  },
)

// 预览打开联动（对齐旧 showPreviewFrame）：面板展开 + 切浏览器 Tab；seq 兼具「mode 不变的重复 open」触发
watch(
  [() => preview.mode, () => preview.seq],
  ([mode]) => {
    if (mode === 'none') return
    ws.panelCollapsed = false
    ws.switchTab('wsPreview')
  },
)
</script>

<template>
  <!-- 最大化/还原过渡期间钉住聊天区布局（等价旧 #wsPlaceholder） -->
  <div v-if="overlaying" :style="{ width: heldWidth + 'px', flexShrink: '0' }"></div>

  <aside
    ref="panelEl"
    class="steps-panel"
    :class="{ collapsed: ws.panelCollapsed, maximized: ws.panelMaximized, overlay: overlaying, resizing }"
    :style="panelStyle"
  >
    <div class="ws-resizer" @mousedown="startResize"></div>
    <div class="steps-panel-header">
      <h4><i class="fas fa-layer-group"></i> {{ t('wsPanel.title') }}</h4>
      <div class="steps-panel-actions">
        <button :title="ws.panelMaximized ? t('wsPanel.restore') : t('wsPanel.maximize')" @click="toggleMax">
          <i
            class="fas"
            :class="ws.panelMaximized ? 'fa-down-left-and-up-right-to-center' : 'fa-up-right-and-down-left-from-center'"
          ></i>
        </button>
        <button :title="t('common.close')" @click="closePanel"><i class="fas fa-times"></i></button>
      </div>
    </div>
    <div class="ws-tabs">
      <button
        v-for="tab in TABS"
        :key="tab.id"
        class="ws-tab"
        :class="{ active: ws.activeTab === tab.id, dot: ws.tabDots[tab.id] }"
        @click="ws.switchTab(tab.id)"
      >
        <i class="fas" :class="tab.icon"></i> {{ t(tab.label) }}
      </button>
    </div>

    <div class="ws-pane" :class="{ active: ws.activeTab === 'wsChanges' }" id="wsChanges">
      <ChangesPanel />
      <div class="cp-note"><i class="fas fa-circle-info"></i> {{ t('wsPanel.changesNote') }}</div>
    </div>
    <div class="ws-pane" :class="{ active: ws.activeTab === 'wsTerm' }" id="wsTerm">
      <TerminalPanel />
    </div>
    <div class="ws-pane" :class="{ active: ws.activeTab === 'wsPreview' }" id="wsPreview">
      <BrowserPane />
    </div>
  </aside>
</template>
