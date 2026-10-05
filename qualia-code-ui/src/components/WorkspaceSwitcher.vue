<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { browse } from '@/api/workspace'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type { BrowseDir, BrowseResult } from '@/types'

/**
 * 工作区切换弹窗（平移旧 project-panel.js）：IDE「打开项目」风格双栏。
 * 左栏品牌 + 「打开文件夹」入口；右栏最近打开/目录浏览双视图。
 * 流式期间切换动作拦截；强制模式（启动未绑定工作区）不可关闭，选定后整页重载。
 */
const props = defineProps<{ forced: boolean }>()
const emit = defineEmits<{ close: []; switched: [] }>()

// 品牌四角星（与聊天区 AI 头像同款）
const STAR_SVG =
  '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2c1 6 4 9 10 10-6 1-9 4-10 10-1-6-4-9-10-10 6-1 9-4 10-10z"/></svg>'

const { t } = useI18n()
const chat = useChatStore()
const sessionStore = useSessionStore()
const ws = useWorkspaceStore()

// ===== 视图状态（最近数据复用 ws store：弹窗打开时 refresh，响应式天然最新） =====
const view = ref<'recent' | 'browse'>('recent')
const loadingRecent = ref(true)
const recentError = ref('')
const searchKw = ref('')
const hint = ref<{ msg: string; error: boolean } | null>(null)
const switching = ref(false)

const recentList = computed(() => ws.recent)
const currentPathLower = computed(() => (ws.current?.path ?? '').toLowerCase())
const filteredRecent = computed(() => {
  const kw = searchKw.value.trim().toLowerCase()
  if (!kw) return recentList.value
  return recentList.value.filter((it) => (it.name + ' ' + it.path).toLowerCase().includes(kw))
})

// ===== 目录浏览状态 =====
const rootsCache = ref<BrowseResult | null>(null)
const browsePath = ref('')
const browseParent = ref<string | null>(null)
const brDirs = ref<BrowseDir[]>([])
const brState = ref<'loading' | 'ok' | 'error'>('ok')
const brError = ref('')
const brErrorPath = ref('')
const addr = ref('')

const drives = computed(() => rootsCache.value?.dirs ?? [])

function showError(msg: string) {
  hint.value = { msg, error: true }
}

// 相对时间：今天 HH:mm / 昨天 / N 天前 / yyyy-MM-dd
function relTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  const now = new Date()
  const dayStart = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime()
  const diffDays = Math.round((dayStart(now) - dayStart(d)) / 86400000)
  if (diffDays <= 0) return t('switcher.relToday', { time: `${pad(d.getHours())}:${pad(d.getMinutes())}` })
  if (diffDays === 1) return t('switcher.relYesterday')
  if (diffDays < 7) return t('switcher.daysAgo', { n: diffDays })
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// Windows 绝对路径的父目录（仅用于确定浏览起点，失败时回退主目录）
function parentOf(p: string): string {
  const s = String(p || '').replace(/[\\/]+$/, '')
  const i = Math.max(s.lastIndexOf('\\'), s.lastIndexOf('/'))
  if (i <= 2) return s.slice(0, 1) + ':\\'
  return s.slice(0, i)
}

// 路径像合法绝对路径时给「创建并打开」（对齐旧 canCreate）
function canCreate(path: string): boolean {
  return /^[a-zA-Z]:[\\/]/.test(path)
}

// ===== 最近打开视图 =====
async function loadRecent() {
  loadingRecent.value = true
  recentError.value = ''
  try {
    await ws.refresh()
  } catch (e) {
    recentError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingRecent.value = false
  }
}

// ===== 目录浏览视图 =====
// 磁盘快捷 chips（首次进入浏览视图拉取一次盘符列表；拉不到时 chips 区留空，不影响浏览）
async function loadDrives() {
  if (!rootsCache.value) {
    try {
      rootsCache.value = await browse()
    } catch {
      /* 忽略 */
    }
  }
}

// 进入浏览视图：起点为当前工作区的上级目录（项目通常是兄弟目录，一步可达）
async function showBrowse() {
  view.value = 'browse'
  hint.value = null
  await loadDrives()
  const curPath = ws.current?.path ?? ''
  const start = curPath ? parentOf(curPath) : (rootsCache.value?.home ?? '')
  if (start) void browseTo(start)
}

async function browseTo(path: string) {
  if (!path) {
    browseHome()
    return
  }
  brState.value = 'loading'
  hint.value = null
  try {
    const data = await browse(path)
    browsePath.value = data.path || ''
    browseParent.value = data.parent
    brDirs.value = data.dirs
    brState.value = 'ok'
    addr.value = browsePath.value
  } catch (e) {
    showBrowseError(path, e instanceof Error ? e.message : t('switcher.readFailed'))
  }
}

// 浏览失败：路径像合法绝对路径时给「创建并打开」
function showBrowseError(path: string, msg: string) {
  brState.value = 'error'
  brError.value = msg
  brErrorPath.value = path
  browsePath.value = ''
  addr.value = path
}

function browseUp() {
  if (browseParent.value) void browseTo(browseParent.value)
}

function browseHome() {
  const home = rootsCache.value?.home
  if (home) void browseTo(home)
}

function backToMain() {
  view.value = 'recent'
  hint.value = null
}

function pickBrowsed() {
  if (browsePath.value) void switchTo(browsePath.value)
}

// ===== 切换工作区 =====
async function switchTo(path: string, create = false) {
  if (switching.value) return
  // 流式期间拦截（对齐旧 isChatStreaming 检查；入口置灰外的兜底）
  const sid = sessionStore.currentSessionId
  if (ws.streaming || (sid ? chat.isProcessing(sid) : false)) {
    showError(t('switcher.streamingBusy'))
    return
  }
  switching.value = true
  hint.value = null
  try {
    const result = await ws.switch(path, create)
    if (result.success) {
      if (props.forced) {
        // 首次选择：整页重载让全部模块按新工作区初始化，也顺带解除强制态
        location.replace('/')
        return
      }
      emit('close')
      // 路径未变化时无需刷新页面状态
      if (result.changed) emit('switched')
    } else if (result.code === 'NOT_FOUND') {
      // 目录不存在（最近列表的兜底场景）：进浏览视图给创建选项
      view.value = 'browse'
      void loadDrives()
      showBrowseError(path, t('switcher.dirNotFound'))
    } else {
      showError(result.message || t('switcher.switchFailed'))
    }
  } catch (e) {
    // BUSY（409）等 HTTP 错误：ApiError.message 为后端原因
    showError(t('switcher.switchFailedWith', { msg: e instanceof Error ? e.message : String(e) }))
  } finally {
    switching.value = false
  }
}

// ===== 关闭路径（强制模式拦截一切） =====
function requestClose() {
  if (props.forced) return
  emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || props.forced) return
  // 浏览视图下 Esc 先返回最近视图，再次 Esc 才关闭
  if (view.value === 'browse') backToMain()
  else requestClose()
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  void loadRecent()
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="ws-sw-overlay open" :class="{ forced }" @click.self="requestClose">
    <div class="ws-sw-dialog">
      <aside class="ws-sw-side">
        <div class="ws-sw-side-top">
          <div class="ws-sw-brand">
            <div class="ws-sw-logo" v-html="STAR_SVG"></div>
            <div>
              <div class="ws-sw-brand-name">Qualia Code</div>
              <div class="ws-sw-brand-ver">{{ t('switcher.brandSlogan') }}</div>
            </div>
          </div>
          <button class="ws-sw-close" :title="t('common.close')" @click="requestClose"><i class="fas fa-times"></i></button>
        </div>
        <h2 class="ws-sw-side-title">{{ t('switcher.openProject') }}</h2>
        <div class="ws-sw-side-sub">
          {{
            forced
              ? t('switcher.firstTimeSub')
              : t('switcher.sub')
          }}
        </div>
        <button class="ws-sw-open-btn" @click="showBrowse">
          <i class="fas fa-folder"></i><span>{{ t('switcher.openFolder') }}</span>
        </button>
        <div class="ws-sw-side-foot">{{ t('switcher.sideFootLine1') }}<br />{{ t('switcher.sideFootLine2') }}</div>
      </aside>

      <main class="ws-sw-pane">
        <!-- 最近打开视图 -->
        <section class="ws-sw-view" :class="{ on: view === 'recent' }">
          <div class="ws-sw-rp-head">
            <h3>{{ t('switcher.recent') }}<span class="ws-sw-rp-count">{{ recentList.length ? ' · ' + recentList.length : '' }}</span></h3>
            <label v-if="recentList.length" class="ws-sw-search">
              <i class="fas fa-magnifying-glass"></i>
              <input v-model="searchKw" type="text" :placeholder="t('switcher.searchProject')" />
            </label>
          </div>
          <div class="ws-sw-rp-list">
            <div v-if="loadingRecent" class="ws-sw-empty"><i class="fas fa-spinner fa-spin"></i><span>{{ t('common.loading') }}</span></div>
            <div v-else-if="recentError" class="ws-sw-empty">
              <i class="fas fa-circle-exclamation"></i><span>{{ t('switcher.loadFailed', { msg: recentError }) }}</span>
            </div>
            <template v-else-if="filteredRecent.length">
              <div
                v-for="item in filteredRecent"
                :key="item.path"
                class="ws-sw-rp"
                :class="{ missing: item.exists === false, current: item.path.toLowerCase() === currentPathLower }"
                @click="item.exists !== false && switchTo(item.path)"
              >
                <span class="ico">
                  <i class="fas" :class="item.path.toLowerCase() === currentPathLower ? 'fa-folder-open' : 'fa-folder'"></i>
                </span>
                <div class="meta">
                  <div class="nm">{{ item.name }}</div>
                  <div class="pt">{{ item.path }}</div>
                </div>
                <span v-if="item.path.toLowerCase() === currentPathLower" class="cur-tag">{{ t('switcher.inUse') }}</span>
                <span class="tm">{{ item.exists === false ? t('switcher.notExist') : relTime(item.lastOpened) }}</span>
                <i class="fas fa-arrow-right go"></i>
              </div>
            </template>
            <div v-else class="ws-sw-empty">
              <i class="far fa-folder-open"></i>
              <span>{{ forced ? t('switcher.emptyForced') : t('switcher.empty') }}</span>
            </div>
          </div>
          <div v-if="hint" class="ws-sw-hint show" :class="{ error: hint.error }">
            <i class="fas fa-circle-exclamation"></i><span>{{ hint.msg }}</span>
          </div>
          <div class="ws-sw-rp-foot">
            <i class="fas fa-circle-info"></i>
            <span>{{ forced ? t('switcher.footForced') : t('switcher.foot') }}</span>
            <button class="foot-close" @click="requestClose">{{ t('common.close') }}</button>
          </div>
        </section>

        <!-- 目录浏览视图 -->
        <section class="ws-sw-view" :class="{ on: view === 'browse' }">
          <div class="ws-sw-br-head">
            <div>
              <h3>{{ t('switcher.pickFolder') }}</h3>
              <div class="sub">{{ t('switcher.pickSub') }}</div>
            </div>
          </div>
          <div class="ws-sw-addr-row">
            <button class="nav-btn" :title="t('switcher.parentDir')" :disabled="!browseParent" @click="browseUp">
              <i class="fas fa-arrow-up"></i>
            </button>
            <button class="nav-btn" :title="t('switcher.homeDir')" @click="browseHome"><i class="fas fa-house"></i></button>
            <input
              v-model="addr"
              type="text"
              :placeholder="t('switcher.addrPlaceholder')"
              spellcheck="false"
              @keydown.enter="browseTo(addr.trim())"
            />
          </div>
          <div v-if="drives.length" class="ws-sw-drives">
            <button
              v-for="d in drives"
              :key="d.path"
              class="drive-chip"
              :class="{ on: browsePath.toLowerCase().startsWith(d.path.toLowerCase()) }"
              @click="browseTo(d.path)"
            >
              <i class="fas fa-hard-drive"></i>{{ d.name }}
            </button>
          </div>
          <div class="ws-sw-br-list">
            <div v-if="brState === 'loading'" class="ws-sw-br-empty"><i class="fas fa-spinner fa-spin"></i>{{ t('common.loading') }}</div>
            <div v-else-if="brState === 'error'" class="ws-sw-br-empty error">
              <i class="fas fa-circle-exclamation"></i>
              <span>{{ brError }}</span>
              <button v-if="canCreate(brErrorPath)" class="create-btn" @click="switchTo(brErrorPath, true)">
                <i class="fas fa-folder-plus"></i> {{ t('switcher.createOpen') }}
              </button>
            </div>
            <template v-else-if="brDirs.length">
              <div v-for="d in brDirs" :key="d.path" class="ws-sw-br-item" @click="browseTo(d.path)">
                <i class="fas fa-folder"></i>
                <span>{{ d.name }}</span>
                <span class="enter"><i class="fas fa-chevron-right"></i></span>
              </div>
            </template>
            <div v-else class="ws-sw-br-empty">
              <i class="far fa-folder-open"></i>
              <span>{{ t('switcher.noSubdirs') }}</span>
              <span>{{ t('switcher.openBottomRight') }}</span>
            </div>
          </div>
          <div v-if="hint" class="ws-sw-hint show" :class="{ error: hint.error }">
            <i class="fas fa-circle-exclamation"></i><span>{{ hint.msg }}</span>
          </div>
          <div class="ws-sw-br-foot">
            <div class="sel"><i class="fas fa-folder-open"></i><span class="p">{{ browsePath }}</span></div>
            <button class="ghost-btn" @click="backToMain">{{ t('common.cancel') }}</button>
            <button class="pick-btn" :disabled="!browsePath || switching" @click="pickBrowsed">{{ t('switcher.open') }}</button>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<style>
/* 平移旧 project-panel.js 自注入样式（ws-sw- 前缀全局，依赖页面 CSS 变量） */
/* ===== 工作区切换弹窗：IDE「打开项目」风格双栏 ===== */
.ws-sw-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: wsSwFade 0.18s ease;
}
@keyframes wsSwFade {
  from { opacity: 0; }
  to { opacity: 1; }
}
.ws-sw-dialog {
  display: flex;
  width: min(860px, calc(100vw - 48px));
  height: min(540px, calc(100vh - 64px));
  min-height: 380px;
  background: var(--bg-surface);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.ws-sw-overlay.open .ws-sw-dialog {
  animation: wsSwPop 0.26s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes wsSwPop {
  from { opacity: 0; transform: translateY(12px) scale(0.985); }
  to { opacity: 1; transform: none; }
}

/* ----- 左栏：品牌 + 操作入口 ----- */
.ws-sw-side {
  width: 248px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 22px 18px;
  border-right: 1px solid var(--border-color);
}
.ws-sw-side-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.ws-sw-brand { display: flex; align-items: center; gap: 11px; }
.ws-sw-logo {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--accent-gradient);
  color: var(--accent-ink);
  box-shadow: var(--shadow-input), inset 0 1px 0 rgba(255, 255, 255, 0.22);
}
.ws-sw-logo svg { width: 19px; height: 19px; }
.ws-sw-brand-name {
  font-size: 14px;
  font-weight: 650;
  color: var(--text-primary);
  letter-spacing: 0.1px;
}
.ws-sw-brand-ver {
  margin-top: 2px;
  font-size: 10px;
  color: var(--text-muted);
}
.ws-sw-close {
  flex-shrink: 0;
  width: 27px;
  height: 27px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-sw-close:hover { color: var(--text-primary); background: var(--bg-hover); }
.ws-sw-side-title {
  margin-top: 30px;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}
.ws-sw-side-sub {
  margin-top: 8px;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--text-muted);
}
.ws-sw-open-btn {
  margin-top: 20px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: none;
  border-radius: 9px;
  background: var(--accent-gradient);
  color: var(--accent-ink);
  font-size: 12.5px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  box-shadow: var(--shadow-input);
  transition: filter 0.15s;
}
.ws-sw-open-btn:hover { filter: brightness(1.12); }
.ws-sw-open-btn i { font-size: 12.5px; }
.ws-sw-side-foot {
  margin-top: auto;
  padding-top: 16px;
  font-size: 10px;
  line-height: 1.7;
  color: var(--text-muted);
}

/* ----- 右侧内容区 ----- */
.ws-sw-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.ws-sw-view { display: none; flex-direction: column; flex: 1; min-height: 0; }
.ws-sw-view.on { display: flex; animation: wsSwFade 0.2s ease; }

/* --- 最近打开视图 --- */
.ws-sw-rp-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 24px 12px;
}
.ws-sw-rp-head h3 {
  font-size: 13px;
  font-weight: 620;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
}
.ws-sw-rp-count { color: var(--text-muted); font-weight: 500; margin-left: 3px; }
.ws-sw-search {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 208px;
  height: 30px;
  padding: 0 11px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 11px;
  transition: all 0.15s;
}
.ws-sw-search:focus-within {
  border-color: var(--border-active);
  box-shadow: var(--shadow-input);
}
.ws-sw-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 11.5px;
  font-family: inherit;
}
.ws-sw-search input::placeholder { color: var(--text-muted); }

.ws-sw-rp-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 2px 18px 12px;
}
.ws-sw-rp-list::-webkit-scrollbar { width: 4px; }
.ws-sw-rp-list::-webkit-scrollbar-track { background: transparent; }
.ws-sw-rp-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }

.ws-sw-rp {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 9px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.13s, border-color 0.13s;
}
.ws-sw-rp + .ws-sw-rp { margin-top: 2px; }
.ws-sw-rp:hover { background: var(--bg-hover); }
.ws-sw-rp.current { background: var(--bg-active); border-color: var(--border-active); }
.ws-sw-rp .ico {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  border: 1px solid var(--border-color);
  background: var(--bg-hover);
  color: var(--text-muted);
  font-size: 13px;
  transition: all 0.13s;
}
.ws-sw-rp:hover .ico, .ws-sw-rp.current .ico {
  color: var(--accent-light);
  border-color: var(--border-active);
}
.ws-sw-rp .meta { flex: 1; min-width: 0; }
.ws-sw-rp .nm {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ws-sw-rp .pt {
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--text-muted);
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ws-sw-rp .cur-tag {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  color: var(--accent-light);
  border: 1px solid var(--border-active);
  border-radius: 999px;
  padding: 2px 8px;
}
.ws-sw-rp .tm { flex-shrink: 0; font-size: 10px; color: var(--text-muted); }
.ws-sw-rp .go {
  flex-shrink: 0;
  color: var(--accent-light);
  font-size: 10.5px;
  opacity: 0;
  transform: translateX(-3px);
  transition: all 0.15s;
}
.ws-sw-rp:hover .go { opacity: 1; transform: none; }
.ws-sw-rp.missing { opacity: 0.4; cursor: not-allowed; }
.ws-sw-rp.missing:hover { background: transparent; }
.ws-sw-rp.missing:hover .ico { color: var(--text-muted); border-color: var(--border-color); }
.ws-sw-rp.missing:hover .go { opacity: 0; }
.ws-sw-rp.missing .tm { color: var(--error); }
.ws-sw-rp.hidden { display: none; }
.ws-sw-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 34px 20px;
  font-size: 11.5px;
  color: var(--text-muted);
  text-align: center;
}
.ws-sw-empty i { font-size: 18px; opacity: 0.5; }
.ws-sw-rp-foot {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 24px 14px;
  border-top: 1px solid var(--border-color);
  font-size: 10.5px;
  color: var(--text-muted);
}
.ws-sw-rp-foot i { font-size: 10.5px; }
.ws-sw-rp-foot .foot-close {
  margin-left: auto;
  flex-shrink: 0;
  height: 26px;
  padding: 0 13px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-sw-rp-foot .foot-close:hover {
  color: var(--text-primary);
  border-color: rgba(255, 255, 255, 0.12);
  background: var(--bg-hover);
}

/* --- 目录浏览视图 --- */
.ws-sw-br-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px 0;
}
.ws-sw-br-head h3 {
  font-size: 14px;
  font-weight: 620;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}
.ws-sw-br-head .sub { margin-top: 3px; font-size: 10.5px; color: var(--text-muted); }

/* 地址栏：上级/主目录 + 可编辑路径输入（回车跳转） */
.ws-sw-addr-row { display: flex; gap: 8px; margin: 16px 24px 0; }
.ws-sw-addr-row .nav-btn {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-sw-addr-row .nav-btn:hover {
  color: var(--accent-light);
  border-color: var(--border-active);
  background: var(--bg-active);
}
.ws-sw-addr-row .nav-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.ws-sw-addr-row input {
  flex: 1;
  min-width: 0;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 11.5px;
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  outline: none;
  transition: all 0.15s;
}
.ws-sw-addr-row input:focus {
  border-color: var(--border-active);
  box-shadow: var(--shadow-input);
}
.ws-sw-addr-row input::placeholder { color: var(--text-muted); }

/* 磁盘快捷 chips */
.ws-sw-drives { display: flex; flex-wrap: wrap; gap: 6px; margin: 10px 24px 0; }
.ws-sw-drives:empty { display: none; }
.ws-sw-drives .drive-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 11px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 10.5px;
  font-weight: 550;
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  cursor: pointer;
  transition: all 0.15s;
}
.ws-sw-drives .drive-chip:hover, .ws-sw-drives .drive-chip.on {
  color: var(--accent-light);
  border-color: var(--border-active);
  background: var(--bg-active);
}
.ws-sw-drives .drive-chip i { font-size: 10px; }

/* 子目录列表 */
.ws-sw-br-list {
  flex: 1;
  min-height: 0;
  margin: 12px 24px 0;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  padding: 5px;
  overflow-y: auto;
}
.ws-sw-br-list::-webkit-scrollbar { width: 4px; }
.ws-sw-br-list::-webkit-scrollbar-track { background: transparent; }
.ws-sw-br-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }
.ws-sw-br-item {
  display: flex;
  align-items: center;
  gap: 11px;
  height: 38px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s;
}
.ws-sw-br-item i {
  font-size: 12.5px;
  color: var(--text-muted);
  width: 15px;
  text-align: center;
  transition: color 0.12s;
}
.ws-sw-br-item:hover { background: var(--bg-hover); }
.ws-sw-br-item:hover i { color: var(--text-secondary); }
.ws-sw-br-item .enter {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 10px;
  opacity: 0;
  transition: opacity 0.12s;
}
.ws-sw-br-item:hover .enter { opacity: 1; }
.ws-sw-br-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 150px;
  height: 100%;
  font-size: 11px;
  color: var(--text-muted);
  text-align: center;
  padding: 18px;
}
.ws-sw-br-empty i { font-size: 20px; opacity: 0.5; }
.ws-sw-br-empty .create-btn {
  padding: 5px 13px;
  border: 1px solid var(--accent-light);
  border-radius: 7px;
  background: transparent;
  color: var(--accent-light);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-sw-br-empty .create-btn:hover { background: var(--accent-light); color: var(--accent-ink); }
.ws-sw-br-empty.error { color: var(--error); }

/* 提示行：错误信息（两个视图各一条） */
.ws-sw-hint {
  display: none;
  align-items: center;
  gap: 9px;
  margin: 10px 24px 0;
  padding: 8px 12px;
  border: 1px dashed var(--border-color);
  border-radius: 9px;
  font-size: 11px;
  color: var(--text-secondary);
  animation: wsSwFade 0.18s ease;
}
.ws-sw-hint.show { display: flex; }
.ws-sw-hint.error { color: var(--error); border-color: var(--error); }

/* 底部动作栏：选中路径 + 取消 + 打开 */
.ws-sw-br-foot {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px 18px;
}
.ws-sw-br-foot .sel {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10.5px;
  color: var(--text-muted);
}
.ws-sw-br-foot .sel i { color: var(--accent-light); font-size: 11px; flex-shrink: 0; }
.ws-sw-br-foot .sel .p {
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  direction: rtl;
  text-align: left;
}
.ws-sw-br-foot .ghost-btn {
  flex-shrink: 0;
  height: 34px;
  padding: 0 17px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-sw-br-foot .ghost-btn:hover {
  color: var(--text-primary);
  border-color: rgba(255, 255, 255, 0.12);
  background: var(--bg-hover);
}
.ws-sw-br-foot .pick-btn {
  flex-shrink: 0;
  height: 34px;
  padding: 0 20px;
  border: none;
  border-radius: 8px;
  background: var(--accent-gradient);
  color: var(--accent-ink);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  box-shadow: var(--shadow-input);
  transition: filter 0.15s;
}
.ws-sw-br-foot .pick-btn:hover { filter: brightness(1.1); }
.ws-sw-br-foot .pick-btn:disabled { opacity: 0.45; cursor: not-allowed; filter: none; }

/* ----- 强制模式（启动时未绑定工作区）：拦截一切关闭路径 ----- */
.ws-sw-overlay.forced .ws-sw-close { display: none; }
.ws-sw-overlay.forced .ws-sw-rp-foot .foot-close { display: none; }
.ws-sw-overlay.forced .ws-sw-br-foot .ghost-btn { display: none; }

/* ----- 窄屏：左栏收窄为顶部条 ----- */
@media (max-width: 720px) {
  .ws-sw-dialog {
    flex-direction: column;
    width: min(520px, calc(100vw - 32px));
    height: auto;
    max-height: calc(100vh - 48px);
  }
  .ws-sw-side {
    width: auto;
    border-right: none;
    border-bottom: 1px solid var(--border-color);
    padding: 16px 18px;
  }
  .ws-sw-side-title, .ws-sw-side-sub, .ws-sw-side-foot { display: none; }
  .ws-sw-open-btn { margin-top: 12px; }
  .ws-sw-rp-list { min-height: 220px; }
  .ws-sw-br-list { min-height: 180px; }
}
</style>
