<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTheme } from '@/composables/theme'
import { setLocale } from '@/i18n'
import { useSessionStore } from '@/stores/session'
import { useMaterialsStore } from '@/stores/materials'
import ConversationList from '@/components/ConversationList.vue'
import TokenHeatmap from '@/components/TokenHeatmap.vue'

const emit = defineEmits<{ openSettings: [] }>()

const sessionStore = useSessionStore()
const materials = useMaterialsStore()
const { isLight, toggleTheme } = useTheme()
const { t, locale } = useI18n()

/** 会话搜索展开态：入口钮在本组件 header，搜索框与过滤在 ConversationList 内 */
const sessionSearch = ref(false)

/** 设置悬浮菜单：点设置钮弹出右侧浮层；外观/视图/语言归入系统设置「个性」页，浮层只留常用快捷项 */
const flyoutOpen = ref(false)
const anchorRef = ref<HTMLElement | null>(null)
const flyoutPos = ref<{ left: string; bottom: string }>({ left: '0px', bottom: '0px' })

function toggleFlyout() {
  if (!flyoutOpen.value) {
    // fixed 定位坐标按按钮实时位置计算：left 右侧弹出，bottom 与按钮底部对齐（向上生长）
    const rect = anchorRef.value?.getBoundingClientRect()
    if (rect) {
      flyoutPos.value = {
        left: `${rect.right + 8}px`,
        bottom: `${window.innerHeight - rect.bottom + 2}px`,
      }
    }
  }
  flyoutOpen.value = !flyoutOpen.value
}

function openSystemSettings() {
  flyoutOpen.value = false
  emit('openSettings')
}

/** 语言行：在中英之间切换并持久化（右侧灰字展示当前语言，浮层保持打开便于预览） */
function toggleLocale() {
  setLocale(locale.value === 'zh-CN' ? 'en' : 'zh-CN')
}

// document 级关闭：按钮/浮层内部点击忽略，其余任意点击关闭（对齐 ModelSelector 委托模式）
function onDocClick(e: MouseEvent) {
  const el = e.target as Element
  if (anchorRef.value?.contains(el) || el.closest('.settings-flyout')) return
  flyoutOpen.value = false
}
function onDocKey(e: KeyboardEvent) {
  if (e.key === 'Escape') flyoutOpen.value = false
}

// ===== 宽度拖拽：null = CSS 默认宽；钳制与 .ws-resizer 同思路（不持久化） =====
const sidebarEl = ref<HTMLElement | null>(null)
const sideWidth = ref<number | null>(null)
const resizing = ref(false)
let stopResize: (() => void) | null = null

function clampSideWidth(w: number): number {
  // 上限受视口约束：给聊天区+工作区面板保留至少 690px（与面板 clampWidth 的保留量一致）
  return Math.min(Math.max(198, window.innerWidth - 690), Math.max(198, w))
}

function startResize(e: MouseEvent) {
  e.preventDefault()
  const aside = sidebarEl.value
  if (!aside) return
  resizing.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  const onMove = (ev: MouseEvent) => {
    sideWidth.value = clampSideWidth(ev.clientX - aside.getBoundingClientRect().left)
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
  stopResize = onUp // 卸载兜底：拖拽中途组件被卸也能摘掉 document 监听
}

const sideStyle = computed(() => {
  if (sideWidth.value == null) return undefined
  return { width: sideWidth.value + 'px', minWidth: sideWidth.value + 'px' }
})

// 窄屏（≤820px）切回固定 198px 布局：复位拖拽宽度，避免内联样式压过媒体查询
const narrowMq = window.matchMedia('(max-width: 820px)')
function onNarrowChange() {
  if (narrowMq.matches) sideWidth.value = null
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onDocKey)
  narrowMq.addEventListener('change', onNarrowChange)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onDocKey)
  narrowMq.removeEventListener('change', onNarrowChange)
  stopResize?.()
})
</script>

<template>
  <aside class="sidebar" ref="sidebarEl" :class="{ resizing }" :style="sideStyle">
    <!-- 右缘拖拽调宽手柄（198 ~ 视口余量，不持久化；≤820px 隐藏并复位） -->
    <div class="side-resizer" @mousedown="startResize"></div>
    <div class="sidebar-brand">
      <i class="fas fa-feather"></i>
      <span>Qualia Code</span>
    </div>

    <!-- 顶部 header：标题 + 操作钮组（搜索 / 新建；文件树在工作区面板，侧栏为纯全局会话库） -->
    <div class="sidebar-header">
      <span class="sidebar-title">{{ t('sidebar.sessionsTitle') }}</span>
      <span class="header-acts">
        <button
          :class="{ on: sessionSearch }"
          :title="t('sidebar.searchSessions')"
          @click="sessionSearch = !sessionSearch"
        >
          <i class="fas fa-magnifying-glass"></i>
        </button>
        <button :title="t('sidebar.newChat')" @click="sessionStore.createSession().catch(() => undefined)">
          <i class="fas fa-plus"></i>
        </button>
      </span>
    </div>

    <!-- 全局会话库（工作区分组）+ 近 30 日 token 用量热力矩阵 -->
    <ConversationList :search-open="sessionSearch" @close-search="sessionSearch = false" />
    <TokenHeatmap />

    <div class="sidebar-footer">
      <!-- 资料（Wiki 式资料库）入口：设置按钮上方，同款样式；点击在主区整栏展开 -->
      <button
        class="settings-btn"
        :class="{ open: materials.open }"
        :title="t('materials.title')"
        @click="materials.toggleView()"
      >
        <i class="fas fa-book"></i>
        <span>{{ t('materials.entry') }}</span>
      </button>
      <div class="settings-anchor" ref="anchorRef">
        <button class="settings-btn" :class="{ open: flyoutOpen }" :title="t('sidebar.settings')" @click="toggleFlyout">
          <i class="fas fa-sliders-h"></i>
          <span>{{ t('sidebar.settings') }}</span>
        </button>
      </div>
    </div>
  </aside>

  <!-- 设置悬浮菜单：Teleport 到 body + fixed 定位，绕开 sidebar overflow:hidden 的裁剪 -->
  <Teleport to="body">
    <div v-if="flyoutOpen" class="settings-flyout" :style="flyoutPos">
      <button class="flyout-item" @click="openSystemSettings">
        <i class="fas fa-gear"></i>
        <span>{{ t('sidebar.systemSettings') }}</span>
      </button>
      <!-- 外观行：整行点击切换黑白主题，右侧开关仅作状态指示（开=日间，关=夜间） -->
      <button class="flyout-item" @click="toggleTheme">
        <i class="fas fa-circle-half-stroke"></i>
        <span>{{ t('sidebar.appearance') }}</span>
        <span class="flyout-switch" :class="{ on: isLight }" aria-hidden="true">
          <span class="flyout-knob"></span>
        </span>
      </button>
      <!-- 语言行：整行点击在中英之间切换，右侧灰字显示当前语言 -->
      <button class="flyout-item" @click="toggleLocale">
        <i class="fas fa-language"></i>
        <span>{{ t('sidebar.language') }}</span>
        <span class="flyout-locale">{{ locale === 'zh-CN' ? '中文' : 'English' }}</span>
      </button>
    </div>
  </Teleport>
</template>
