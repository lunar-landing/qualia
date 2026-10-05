<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSessionStore } from '@/stores/session'
import { useChatStore } from '@/stores/chat'
import { sessionGroupLabel } from '@/utils/format'

const sessionStore = useSessionStore()
const chat = useChatStore()
const { t } = useI18n()

// ===== 会话搜索：入口钮在 SidebarArea header，搜索框随本列表顶部滑出（收起即清空） =====
const props = defineProps<{ searchOpen: boolean }>()
const emit = defineEmits<{ closeSearch: [] }>()
const query = ref('')
const searchInput = ref<HTMLInputElement | null>(null)

watch(
  () => props.searchOpen,
  (open) => {
    if (open) {
      void nextTick(() => searchInput.value?.focus())
    } else {
      query.value = ''
    }
  },
)

function clearQuery() {
  query.value = ''
  searchInput.value?.focus()
}

/** Esc 收起搜索（父组件置 searchOpen=false，watch 统一清空） */
function onSearchKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('closeSearch')
  }
}

type Row =
  | { kind: 'group'; key: string; label: string }
  | { kind: 'session'; key: string; id: string; title: string; active: boolean; streaming: boolean; unread: boolean }

const rows = computed<Row[]>(() => {
  const q = query.value.trim().toLowerCase()
  const out: Row[] = []
  let lastGroup: string | null = null
  for (const s of sessionStore.sessions) {
    if (q && !s.title.toLowerCase().includes(q)) continue
    const group = sessionGroupLabel(s.createdAt)
    if (group !== lastGroup) {
      lastGroup = group
      out.push({ kind: 'group', key: `g-${group}-${s.id}`, label: group })
    }
    out.push({
      kind: 'session',
      key: s.id,
      id: s.id,
      title: s.title,
      active: s.id === sessionStore.currentSessionId,
      streaming: chat.isProcessing(s.id),
      unread: !!chat.unreadBySession[s.id],
    })
  }
  return out
})

const hasHits = computed(() => rows.value.some((r) => r.kind === 'session'))
const showEmpty = computed(() => props.searchOpen && query.value.trim() !== '' && !hasHits.value)

// ===== 标题命中高亮（全量转义后仅包 mark，大小写不敏感） =====
function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string)
}

function highlightTitle(title: string): string {
  const q = query.value.trim()
  if (!q) return escapeHtml(title)
  const idx = title.toLowerCase().indexOf(q.toLowerCase())
  if (idx < 0) return escapeHtml(title)
  return `${escapeHtml(title.slice(0, idx))}<mark>${escapeHtml(title.slice(idx, idx + q.length))}</mark>${escapeHtml(title.slice(idx + q.length))}`
}

// ===== 右键菜单（重命名 / 删除） =====
const menu = ref({ open: false, x: 0, y: 0, sid: '' })

function openMenu(e: MouseEvent, sid: string) {
  e.preventDefault()
  const mw = 140, mh = 84
  let x = e.clientX
  let y = e.clientY
  if (x + mw > window.innerWidth) x = window.innerWidth - mw - 8
  if (y + mh > window.innerHeight) y = window.innerHeight - mh - 8
  menu.value = { open: true, x, y, sid }
}

function closeMenu() {
  menu.value.open = false
  menu.value.sid = ''
}

function onMenuAction(act: string) {
  const sid = menu.value.sid
  closeMenu()
  if (!sid) return
  if (act === 'rename') startRename(sid)
  if (act === 'delete') removeSession(sid)
}

function removeSession(sid: string) {
  if (!window.confirm(t('conversation.deleteConfirm'))) return
  void sessionStore.removeSession(sid)
}

// ===== 行内重命名（Enter/失焦提交，Esc 取消，最长 50 字符） =====
const renaming = ref('')
const renameValue = ref('')
const renameInput = ref<HTMLInputElement | null>(null)

function startRename(sid: string) {
  const session = sessionStore.sessions.find((s) => s.id === sid)
  if (!session) return
  renaming.value = sid
  renameValue.value = session.title
  void nextTick(() => {
    renameInput.value?.focus()
    renameInput.value?.select()
  })
}

function cancelRename() {
  renaming.value = ''
}

async function commitRename() {
  const sid = renaming.value
  if (!sid) return
  const session = sessionStore.sessions.find((s) => s.id === sid)
  const current = session?.title ?? ''
  const title = renameValue.value.trim()
  renaming.value = ''
  if (!title || title === current) return
  await sessionStore.renameSession(sid, title).catch(() => undefined)
}

function onRenameKeydown(e: KeyboardEvent) {
  e.stopPropagation()
  if (e.key === 'Enter') renameInput.value?.blur()
  if (e.key === 'Escape') cancelRename()
}

function onGlobalClick() {
  closeMenu()
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') closeMenu()
}

onMounted(() => {
  document.addEventListener('click', onGlobalClick)
  document.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onGlobalClick)
  document.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<template>
  <div class="conv-wrap">
    <!-- 搜索框：grid-rows 滑出，固定于滚动区上方（长列表滚动时保持可见），Esc 收起 -->
    <div class="ss-wrap" :class="{ open: searchOpen }">
      <div class="ss-clip">
        <div class="ss-bar" :class="{ 'has-q': query.trim() }">
          <i class="fas fa-magnifying-glass"></i>
          <input
            ref="searchInput"
            v-model="query"
            type="text"
            :placeholder="t('sidebar.searchSessions')"
            @keydown="onSearchKeydown"
          />
          <button v-show="query.trim()" class="ss-clear" :title="t('sidebar.searchClear')" @click="clearQuery">
            <i class="fas fa-xmark"></i>
          </button>
        </div>
      </div>
    </div>

    <ul v-show="!showEmpty" class="conversation-list">
      <template v-for="row in rows" :key="row.key">
        <li v-if="row.kind === 'group'" class="conv-group">{{ row.label }}</li>
        <li
          v-else
          :data-id="row.id"
          :class="{ active: row.active, streaming: row.streaming }"
          @click="sessionStore.switchTo(row.id)"
          @contextmenu="openMenu($event, row.id)"
        >
          <span v-if="row.streaming" class="streaming-dot" :title="t('conversation.streaming')"></span>
          <span v-else-if="row.unread" class="unread-dot" :title="t('conversation.completed')"></span>
          <i v-else class="far fa-comment"></i>
          <input
            v-if="renaming === row.id"
            ref="renameInput"
            v-model="renameValue"
            type="text"
            class="session-rename"
            maxlength="50"
            @keydown="onRenameKeydown"
            @click.stop
            @blur="commitRename"
          />
          <span v-else class="session-title" v-html="highlightTitle(row.title)"></span>
        </li>
      </template>
    </ul>

    <!-- 搜索无命中空态 -->
    <div v-if="showEmpty" class="ss-empty">
      <i class="fas fa-magnifying-glass"></i>
      <span>{{ t('sidebar.searchEmpty') }}</span>
    </div>
  </div>

  <Teleport to="body">
    <div v-show="menu.open" class="session-menu open" :style="{ left: menu.x + 'px', top: menu.y + 'px' }">
      <button @click.stop="onMenuAction('rename')"><i class="fas fa-pen"></i>{{ t('common.rename') }}</button>
      <button class="danger" @click.stop="onMenuAction('delete')"><i class="fas fa-trash-can"></i>{{ t('common.delete') }}</button>
    </div>
  </Teleport>
</template>
