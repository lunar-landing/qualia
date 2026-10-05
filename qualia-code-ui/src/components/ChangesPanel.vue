<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { baseName, changeKeyOf, normPath } from '@/utils/steps'
import type { AgentStep } from '@/types'

/**
 * 工作区「审查」面板（平移旧 changes-panel.js）：
 * 以「对话轮」为组聚合 edit/write/delete 变更，组头显示提问摘要，按时间倒序排列。
 * 数据从当前会话消息派生（chat store 单一数据源）——切会话/后台流自动跟随，
 * 消除旧版 beginGroup/record/rebuild 手动喂数据三件套。
 */
const { t } = useI18n()
const chat = useChatStore()
const sessionStore = useSessionStore()
const ws = useWorkspaceStore()

const TEXT_LIMIT = 4000
const LABEL_LIMIT = 40
// 徽标/写入模式文案表：computed 包裹保证切语言后已挂载组件同步刷新
const MODE_LABEL = computed<Record<string, string>>(() => ({
  overwrite: t('changes.modeOverwrite'),
  append: t('changes.modeAppend'),
  insert: t('changes.modeInsert'),
}))
const BADGE_LABEL = computed<Record<string, string>>(() => ({
  edit: t('changes.edit'),
  write: t('changes.write'),
  delete: t('changes.delete'),
}))

interface ChangeFile {
  key: string
  steps: AgentStep[]
}
interface ChangeGroup {
  /** 组 id 用消息 id（稳定：hist-{sid}-{i} / msg-*），展开状态以此记忆 */
  id: string
  seq: number
  label: string | null
  files: ChangeFile[]
}

const sid = computed(() => sessionStore.currentSessionId ?? '')

/** 从消息列表派生分组：user 消息记为最近提问，assistant 消息按变更工具聚合；无变更轮次不占 seq（对齐旧懒建组） */
const groups = computed<ChangeGroup[]>(() => {
  const out: ChangeGroup[] = []
  let lastQuestion: string | null = null
  let seq = 0
  for (const msg of chat.messagesOf(sid.value)) {
    if (msg.role === 'user') {
      lastQuestion = msg.content
      continue
    }
    if (!msg.steps?.length) continue
    const byPath = new Map<string, AgentStep[]>()
    for (const s of msg.steps) {
      const key = changeKeyOf(s)
      if (!key) continue
      const list = byPath.get(key)
      if (list) list.push(s)
      else byPath.set(key, [s])
    }
    if (byPath.size === 0) continue
    seq++
    out.push({
      id: msg.id,
      seq,
      label: lastQuestion,
      files: [...byPath].map(([key, steps]) => ({ key, steps })),
    })
  }
  return out
})

/** 最新一轮在最上 */
const displayGroups = computed(() => [...groups.value].reverse())

// 展开状态：key 为 `${组id}|${路径}`；会话切换时清空（对齐旧 rebuild 行为）
const expanded = ref(new Set<string>())
watch(sid, () => {
  expanded.value = new Set()
})

const listEl = ref<HTMLElement | null>(null)

// 文件树徽标点击跳转：宽松路径匹配 → 展开 + 滚动定位（对齐旧 ChangesPanel.openFile）
watch(
  () => ws.pendingFileFocus,
  (pf) => {
    if (!pf) return
    ws.pendingFileFocus = null
    const norm = normPath(pf.path)
    if (!norm) return
    let hit: string | null = null
    for (let i = groups.value.length - 1; i >= 0 && !hit; i--) {
      const g = groups.value[i]
      if (!g) break
      for (const f of g.files) {
        if (f.key === norm || f.key.endsWith('/' + norm) || norm.endsWith('/' + f.key)) {
          hit = g.id + '|' + f.key
          break
        }
      }
    }
    if (!hit) return
    expanded.value.add(hit)
    const target = hit
    void nextTick(() => {
      listEl.value
        ?.querySelector(`[data-key="${CSS.escape(target)}"]`)
        ?.scrollIntoView({ block: 'start', behavior: 'smooth' })
    })
  },
)

function toggleFile(key: string) {
  const next = new Set(expanded.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expanded.value = next
}

function truncate(s: unknown, n: number): string {
  const t = String(s ?? '')
  return t.length > n ? t.slice(0, n) + '…' : t
}

/** 文件终态徽标：末步为删除则标删除，否则按是否含 edit 标修改/写入 */
function fileState(steps: AgentStep[]): 'edit' | 'write' | 'delete' {
  const last = steps[steps.length - 1]
  if (last?.toolName === 'delete') return 'delete'
  return steps.some((s) => s.toolName === 'edit') ? 'edit' : 'write'
}
</script>

<template>
  <div ref="listEl" class="cp-list">
    <div v-if="displayGroups.length === 0" class="empty-steps">
      <i class="fas fa-code-commit"></i>
      <span>{{ t('changes.empty') }}</span>
    </div>
    <template v-else>
      <div v-for="g in displayGroups" :key="g.id" class="cp-group">
        <div class="cp-group-head" :title="g.label ?? ''">
          <span class="cp-group-seq">#{{ g.seq }}</span>
          <span class="cp-group-label">{{ g.label ? truncate(g.label, LABEL_LIMIT) : t('changes.round', { n: g.seq }) }}</span>
          <span class="cp-group-count">{{ t('changes.fileCount', { n: g.files.length }) }}</span>
        </div>
        <div
          v-for="f in g.files"
          :key="f.key"
          class="cp-file"
          :class="{ open: expanded.has(g.id + '|' + f.key) }"
          :data-key="g.id + '|' + f.key"
        >
          <div class="cp-head" :title="f.key" @click="toggleFile(g.id + '|' + f.key)">
            <i class="fas fa-chevron-right cp-caret"></i>
            <span class="cp-badge" :class="fileState(f.steps)">{{ BADGE_LABEL[fileState(f.steps)] }}</span>
            <span class="cp-name">{{ baseName(f.key) }}</span>
            <span class="cp-count">{{ t('changes.times', { n: f.steps.length }) }}</span>
          </div>
          <div class="cp-diffs">
            <div v-for="(s, i) in f.steps" :key="i" class="cp-step">
              <!-- delete：红色删除标记 -->
              <template v-if="s.toolName === 'delete'">
                <div class="cp-step-head">#{{ i + 1 }} {{ t('changes.delete') }}</div>
                <div class="cp-diff">
                  <div class="cp-del">{{ t('changes.fileDeleted') }}</div>
                </div>
              </template>
              <!-- edit：旧文本(红) / 新文本(绿) 对照 -->
              <template v-else-if="s.toolName === 'edit'">
                <div class="cp-step-head">#{{ i + 1 }} {{ t('changes.edit') }}{{ (s.toolArgs as Record<string, unknown>)?.replace_all ? t('changes.replaceAllSuffix') : '' }}</div>
                <div class="cp-diff">
                  <div class="cp-del">{{ truncate((s.toolArgs as Record<string, unknown>)?.old_text, TEXT_LIMIT) }}</div>
                  <div class="cp-ins">{{ truncate((s.toolArgs as Record<string, unknown>)?.new_text, TEXT_LIMIT) }}</div>
                </div>
              </template>
              <!-- write：全绿 + 写入模式标注 -->
              <template v-else>
                <div class="cp-step-head">#{{ i + 1 }} {{ t('changes.write') }} · {{ MODE_LABEL[(s.toolArgs as Record<string, unknown>)?.mode as string] ?? t('changes.modeOverwrite') }}</div>
                <div class="cp-diff">
                  <div class="cp-ins">{{ truncate((s.toolArgs as Record<string, unknown>)?.content, TEXT_LIMIT) }}</div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style>
/* 平移旧 changes-panel.js 自注入样式（cp- 前缀全局，依赖页面 CSS 变量与 .empty-steps 空态类） */
.cp-list {
  flex: 1;
  overflow-y: auto;
  padding: 11px;
  display: flex;
  flex-direction: column;
  gap: 13px;
}
.cp-list::-webkit-scrollbar { width: 3px; }
.cp-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }

/* 对话轮分组 */
.cp-group {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.cp-group-head {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 2px;
  font-size: 10.5px;
  color: var(--text-muted);
  min-width: 0;
}
.cp-group-seq {
  flex-shrink: 0;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 700;
}
.cp-group-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cp-group-count { margin-left: auto; flex-shrink: 0; }

/* 文件行 */
.cp-file {
  flex-shrink: 0;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  overflow: hidden;
}
.cp-head {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 11px;
  font-size: 11px;
  cursor: pointer;
  user-select: none;
  min-width: 0;
  /* 柔和中性底，与工作区面板底色拉开层次（同 skill-chip 双主题模式） */
  background: rgba(255, 255, 255, 0.08);
}
body.light-theme .cp-head { background: #e4e9ee; }
.cp-head:hover { background: rgba(255, 255, 255, 0.13); }
body.light-theme .cp-head:hover { background: #dde3e9; }
.cp-caret {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--text-muted);
  transition: transform 0.15s;
}
.cp-file.open .cp-caret { transform: rotate(90deg); }
.cp-badge {
  flex-shrink: 0;
  font-size: 9.5px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 100px;
}
.cp-badge.edit { color: #f59e0b; background: rgba(245, 158, 11, 0.13); }
.cp-badge.write { color: #34d399; background: rgba(52, 211, 153, 0.13); }
.cp-badge.delete { color: #f87171; background: rgba(248, 113, 113, 0.13); }
body.light-theme .cp-badge.edit { color: #b45309; background: rgba(180, 83, 9, 0.10); }
body.light-theme .cp-badge.write { color: #059669; background: rgba(5, 150, 105, 0.10); }
body.light-theme .cp-badge.delete { color: #b91c1c; background: rgba(185, 28, 28, 0.10); }
.cp-name {
  font-family: 'JetBrains Mono', monospace;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cp-count {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 10.5px;
  color: var(--text-muted);
}

/* diff 展开层 */
.cp-diffs {
  display: none;
  flex-direction: column;
  gap: 9px;
  padding: 9px 11px;
  border-top: 1px solid var(--border-color);
}
.cp-file.open .cp-diffs { display: flex; }
.cp-step { min-width: 0; }
.cp-step-head {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 5px;
}
.cp-diff {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  overflow: hidden;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  line-height: 1.65;
}
.cp-del, .cp-ins {
  position: relative;
  padding: 7px 11px 7px 23px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 432px;
  overflow-y: auto;
}
.cp-del::before, .cp-ins::before {
  position: absolute;
  left: 10px;
  font-weight: 700;
}
.cp-del { background: rgba(248, 113, 113, 0.08); color: #e89b9b; }
.cp-del::before { content: '-'; color: #f87171; }
.cp-ins { background: rgba(52, 211, 153, 0.08); color: #8fd9bb; }
.cp-ins::before { content: '+'; color: #34d399; }
.cp-del + .cp-ins { border-top: 1px solid var(--border-color); }
/* 浅色主题下提高 diff 文字对比度 */
body.light-theme .cp-del { color: #b91c1c; }
body.light-theme .cp-ins { color: #047857; }
</style>
