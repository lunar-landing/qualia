<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { tryConsumeQueue } from '@/composables/useChatStream'

/**
 * 待发送队列面板（docs/design/queued-messages-inputarea-v3.html 极简稿）：
 * 纯文字行 + 等宽序号 + 悬停浮出删除；点击文本行内编辑（Enter/失焦保存，Esc 取消）；
 * 编辑中暂停自动接发（store.queueEditing 置位，useChatStream.consumeQueue 检查）；
 * 空闲态头部切换为「立即发送 / 清空」。双视图共用：
 * bubble（气泡输入区，与输入框拼合同一卡片）/ terminal（终端控制台，等宽字体变体）。
 */
defineProps<{ variant?: 'bubble' | 'terminal' }>()

const { t } = useI18n()
const chat = useChatStore()
const sessionStore = useSessionStore()

const sid = computed(() => sessionStore.currentSessionId)
const queue = computed(() => (sid.value ? chat.queueOf(sid.value) : []))
const processing = computed(() => (sid.value ? chat.isProcessing(sid.value) : false))
const editingId = computed(() => (sid.value ? (chat.queueEditing[sid.value] ?? null) : null))
const restored = computed(() => (sid.value ? !!chat.queueRestored[sid.value] : false))

// 切会话惰性恢复队列（localStorage → 内存，非空置恢复来源标记）
watch(
  sid,
  (s) => {
    if (s) chat.ensureQueue(s)
  },
  { immediate: true },
)

// ===== 行内编辑 =====
const rootEl = ref<HTMLElement | null>(null)
const editText = ref('')
/** Esc 取消后紧随的 blur 不再保存 */
let escCanceled = false

function startEdit(id: string, text: string) {
  if (!sid.value || editingId.value) return
  escCanceled = false
  chat.startQueuedEdit(sid.value, id)
  editText.value = text
  void nextTick(() => rootEl.value?.querySelector<HTMLTextAreaElement>('.q-edit textarea')?.focus())
}

function onEditKeydown(e: KeyboardEvent) {
  if (e.isComposing || e.keyCode === 229) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    saveEdit()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    escCanceled = true
    if (sid.value && editingId.value) {
      chat.cancelQueuedEdit(sid.value, editingId.value)
      resumeAfterEdit()
    }
  }
}

function saveEdit() {
  if (escCanceled) {
    escCanceled = false
    return
  }
  if (sid.value && editingId.value) {
    chat.saveQueuedEdit(sid.value, editingId.value, editText.value)
    resumeAfterEdit()
  }
}

function removeItem(id: string) {
  if (!sid.value) return
  const wasEditing = editingId.value === id
  chat.removeQueued(sid.value, id)
  if (wasEditing) resumeAfterEdit()
}

/** 编辑态解除后恢复接发：入队过的挂起自动继续；恢复来源（restored）队列仍不自动发 */
function resumeAfterEdit() {
  if (sid.value && !chat.queueRestored[sid.value]) tryConsumeQueue(sid.value)
}

function sendNow() {
  if (sid.value) tryConsumeQueue(sid.value)
}

function clearAll() {
  if (sid.value) chat.clearQueue(sid.value)
}
</script>

<template>
  <div v-if="queue.length" ref="rootEl" class="q-zone" :class="`q-${variant ?? 'bubble'}`">
    <!-- 头部：计数 + 三态说明/操作（生成中自动接发 / 编辑暂停 / 空闲操作） -->
    <div class="q-head">
      <span>{{ t('queue.title') }} · <b>{{ queue.length }}</b></span>
      <span v-if="!processing && restored" class="q-src">· {{ t('queue.restored') }}</span>
      <span class="q-spring"></span>
      <template v-if="!processing">
        <button class="q-lnk q-lnk-main" @click="sendNow">{{ t('queue.sendNow') }}</button>
        <button class="q-lnk" @click="clearAll">{{ t('queue.clear') }}</button>
      </template>
      <span v-else-if="editingId" class="q-hint">{{ t('queue.pausedEditing') }}</span>
      <span v-else class="q-hint">{{ t('queue.autoSend') }}</span>
    </div>
    <!-- 条目：纯文字行 + 等宽序号；超 4 条内滚动；TransitionGroup 轻淡入淡出 -->
    <TransitionGroup tag="div" name="q" class="q-list" :class="{ scroll: queue.length > 4 }">
      <div
        v-for="(item, i) in queue"
        :key="item.id"
        class="q-row"
        :class="{ editing: editingId === item.id }"
      >
        <template v-if="editingId === item.id">
          <span class="q-no">#{{ i + 1 }}</span>
          <div class="q-edit">
            <textarea
              v-model="editText"
              rows="1"
              @keydown="onEditKeydown"
              @blur="saveEdit"
            ></textarea>
            <div class="q-edit-keys"><b>Enter</b> {{ t('queue.save') }} · <b>Esc</b> {{ t('queue.cancel') }}</div>
          </div>
        </template>
        <template v-else>
          <span class="q-no">#{{ i + 1 }}</span>
          <span class="q-text" :title="item.text" @click="startEdit(item.id, item.text)">{{ item.text }}</span>
          <span v-for="att in item.attachments ?? []" :key="att.id" class="q-att">
            <svg width="11" height="11" viewBox="0 0 16 16" fill="none"><path d="M11.5 6.5 8.2 9.8a2.6 2.6 0 0 1-3.7-3.7l4.2-4.2a1.8 1.8 0 0 1 2.5 2.5L7 8.6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
            <span>{{ att.name }}</span>
          </span>
          <button class="q-del" :title="t('queue.remove')" @click="removeItem(item.id)">✕</button>
        </template>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.q-zone {
  padding: 2px 0 5px;
}
/* 气泡输入区：面板置于 .input-wrapper 内部首段——同背景/边框/阴影/hover 光效，
   自身只留底部分隔线与顶部圆角（16px 外框内收 1px；≤640px 13px 外框 → 12px） */
.q-bubble {
  overflow: hidden;
  border-bottom: 1px solid var(--border-color);
  border-radius: 15px 15px 0 0;
}
@media (max-width: 640px) {
  .q-bubble {
    border-radius: 12px 12px 0 0;
  }
}
/* 终端控制台：置于 console 顶部，自身透明，底边线作分隔 */
.q-terminal {
  background: transparent;
  border-bottom: 1px solid var(--border-color);
  padding: 8px 16px 5px;
}
.q-head {
  display: flex;
  align-items: baseline;
  gap: 7px;
  padding: 2px 17px 4px;
  font-size: 11.5px;
  color: var(--text-secondary);
}
.q-head b {
  color: var(--text-primary);
}
.q-src,
.q-hint {
  color: var(--text-muted);
  font-size: 11px;
}
.q-spring {
  flex: 1;
}
.q-lnk {
  padding: 0 2px;
  border: 0;
  background: none;
  font-size: 11.5px;
  color: var(--text-secondary);
  cursor: pointer;
}
.q-lnk:hover {
  color: var(--text-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.q-lnk-main {
  color: var(--text-primary);
  font-weight: 600;
}
.q-list {
  position: relative;
}
.q-list.scroll {
  max-height: 168px;
  overflow-y: auto;
}
.q-list.scroll::-webkit-scrollbar {
  width: 5px;
}
.q-list.scroll::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 3px;
}
.q-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 5px 17px;
  border-radius: 0;
  font-size: 13px;
  color: var(--text-primary);
  transition: background 0.12s ease;
}
.q-row:hover {
  background: var(--bg-active);
}
.q-row.editing {
  background: var(--bg-active);
  box-shadow: inset 2px 0 0 var(--border-active);
}
.q-no {
  flex: none;
  width: 24px;
  font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--text-muted);
}
.q-text {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: text;
}
.q-att {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 150px;
  font-size: 11px;
  color: var(--text-muted);
}
.q-att span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.q-del {
  flex: none;
  align-self: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s ease, color 0.12s ease;
}
.q-row:hover .q-del {
  opacity: 1;
}
.q-del:hover {
  color: #f85149;
}
body.light-theme .q-del:hover {
  color: #cf222e;
}
.q-edit {
  flex: 1;
  min-width: 0;
}
.q-edit textarea {
  width: 100%;
  height: 38px;
  border: 0;
  outline: 0;
  resize: none;
  background: transparent;
  font: inherit;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-primary);
}
.q-edit-keys {
  padding: 0 2px;
  text-align: right;
  font-size: 10.5px;
  color: var(--text-muted);
}
.q-edit-keys b {
  color: var(--text-secondary);
  font-weight: 600;
}
/* 入队淡入 / 删除接发淡出 + 其余行平滑上移 */
.q-move {
  transition: transform 0.15s ease;
}
.q-enter-active {
  transition: opacity 0.18s ease;
}
.q-leave-active {
  transition: opacity 0.12s ease;
  position: absolute;
  width: 100%;
}
.q-enter-from,
.q-leave-to {
  opacity: 0;
}
/* 终端变体：等宽字体；行内边距回 8px（容器已有 16px） */
.q-terminal .q-head,
.q-terminal .q-row,
.q-terminal .q-edit textarea {
  font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
  font-size: 12.5px;
}
.q-terminal .q-head {
  padding-left: 8px;
  padding-right: 8px;
}
.q-terminal .q-row {
  padding-left: 8px;
  padding-right: 8px;
  border-radius: 6px;
}
</style>
