<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { argStr, domainOf, shortScriptName, truncate, WEB_SEARCH_SOURCES, type ToolArgs } from '@/utils/chip'
import type { AgentStep } from '@/types'

/**
 * 工具调用 chip（图标 + 动词 + 参数摘要 + 状态）。
 * 详情块由宿主（MessageItem）在 chips 组下方按需渲染 ChipDetail；
 * 联网搜索族 / web_fetch 的元信息见 WEB_SEARCH_TOOLS 与 webFetchMeta（详情视图 ChipDetailWeb）
 */
const props = defineProps<{
  step: AgentStep
  /** 工具执行结果（紧随 ACTION 的 OBSERVATION/ERROR 归属）；null = 执行中 */
  result: { ok: boolean; content: string } | null
  /** 流式进行中：尚未收到结果时呈 run 态 */
  live: boolean
  /** 详情展开态（宿主持有，与详情块显隐联动） */
  active: boolean
}>()

const emit = defineEmits<{ toggle: [] }>()

const { t } = useI18n()

interface ChipMeta {
  icon: string
  verb: string
  arg: (a: ToolArgs) => string
}

const baseNameOf = (a: ToolArgs): string => {
  const p = argStr(a, 'path', 'file_path')
  if (!p) return ''
  const norm = p.replace(/\\/g, '/')
  return norm.split('/').filter(Boolean).pop() || norm
}

// ===== chip 元信息（图标 / 动词 / 参数摘要，对齐旧 META 表）；computed 包裹保证切语言后已挂载组件同步刷新 =====
const META = computed<Record<string, ChipMeta>>(() => ({
  read: { icon: 'fa-magnifying-glass', verb: t('tool.read'), arg: baseNameOf },
  write: { icon: 'fa-file-circle-plus', verb: t('tool.write'), arg: baseNameOf },
  edit: { icon: 'fa-pen', verb: t('tool.edit'), arg: baseNameOf },
  delete: { icon: 'fa-trash-can', verb: t('tool.delete'), arg: baseNameOf },
  bash: { icon: 'fa-terminal', verb: t('tool.bash'), arg: (a) => truncate(a.command ?? '', 36) },
  grep: { icon: 'fa-magnifying-glass', verb: t('tool.search'), arg: (a) => argStr(a, 'pattern', 'regex') },
  glob: { icon: 'fa-magnifying-glass', verb: t('tool.search'), arg: (a) => argStr(a, 'pattern') },
  'skill-loader': { icon: 'fa-shapes', verb: t('tool.loadSkill'), arg: (a) => argStr(a, 'skill_name') },
  'skill-script-runner': {
    icon: 'fa-bolt',
    verb: t('tool.runScript'),
    arg: (a) => shortScriptName(a.script_name, a.skill_name),
  },
  'skill-reference-reader': { icon: 'fa-book-open', verb: t('tool.readDoc'), arg: (a) => argStr(a, 'file_name') },
  'skill-selector': { icon: 'fa-list', verb: t('tool.listSkills'), arg: () => '' },
}))

// 联网搜索族共元信息（地球图标 / 联网搜索 / query 摘要，对齐旧 browser.js WebSearchDetail.meta）
const webSearchMeta = computed<ChipMeta>(() => ({
  icon: 'fa-globe',
  verb: t('tool.webSearch'),
  arg: (a) => truncate(a.query ?? '', 36),
}))
const WEB_SEARCH_TOOLS = computed<Record<string, ChipMeta>>(() =>
  Object.fromEntries(Object.keys(WEB_SEARCH_SOURCES).map((name) => [name, webSearchMeta.value])),
)
// web_fetch 元信息（抓取网页 · 域名）
const webFetchMeta = computed<ChipMeta>(() => ({
  icon: 'fa-file-lines',
  verb: t('tool.webFetch'),
  arg: (a) => truncate(domainOf(a.url), 36),
}))

const toolName = computed(() => props.step.toolName ?? 'tool')
const meta = computed<ChipMeta>(
  () =>
    META.value[toolName.value] ??
    WEB_SEARCH_TOOLS.value[toolName.value] ??
    (toolName.value === 'web_fetch' ? webFetchMeta.value : undefined) ?? {
      icon: 'fa-wrench',
      verb: toolName.value || t('tool.fallback'),
      arg: () => '',
    },
)

const argText = computed(() => {
  try {
    return meta.value.arg((props.step.toolArgs ?? {}) as ToolArgs) || ''
  } catch {
    return ''
  }
})

const state = computed(() => {
  if (props.result) return props.result.ok ? 'ok' : 'err'
  return props.live ? 'run' : 'ok'
})
</script>

<template>
  <button class="tool-chip" :class="{ active }" :title="toolName" @click.stop="emit('toggle')">
    <i class="chip-ico fas" :class="meta.icon"></i>{{ meta.verb }}
    <span v-if="argText" class="chip-arg">{{ argText }}</span>
    <span class="chip-st" :class="state">
      <i v-if="state === 'run'" class="fas fa-circle-notch fa-spin"></i>
      <i v-else-if="state === 'err'" class="fas fa-xmark"></i>
      <i v-else class="fas fa-check"></i>
    </span>
  </button>
</template>

<style>
/* ===== chip 本体与详情块（旧 tool-chip.js 自包含样式平移；tc-/chip-/sk- 前缀全局防冲突） ===== */
.tool-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 500;
  font-family: 'JetBrains Mono', monospace;
  color: var(--text-secondary);
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  padding: 4px 10px;
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.15s;
  max-width: 100%;
  white-space: nowrap;
}
.tool-chip:hover,
.tool-chip.active {
  border-color: var(--border-active);
  color: var(--text-primary);
}
.tool-chip .chip-ico {
  font-size: 10.5px;
  color: var(--accent-light);
  flex-shrink: 0;
}
.tool-chip .chip-arg {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.tool-chip .chip-st { font-size: 10px; flex-shrink: 0; }
.tool-chip .chip-st.ok { color: #34d399; }
.tool-chip .chip-st.run { color: #60a5fa; }
.tool-chip .chip-st.err { color: #f87171; }
/* 浅色主题下状态色加深，保证白底对比度 */
body.light-theme .tool-chip .chip-st.ok { color: #059669; }
body.light-theme .tool-chip .chip-st.run { color: #2563eb; }
body.light-theme .tool-chip .chip-st.err { color: #dc2626; }

/* chips 组块：chips 行 + 各 chip 展开的详情块（详情撑满组宽，对齐旧 DOM 锚点插入位置） */
.chips-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

/* 详情容器 */
.tc-detail {
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 9px;
  padding: 11px 13px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

/* 文件头 / 搜索头 */
.tc-file-head, .tc-search-head {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}
.tc-file-head i, .tc-search-head i {
  font-size: 10.5px;
  color: var(--accent-light);
  flex-shrink: 0;
}
.tc-path {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  direction: rtl;
  text-align: left;
}
.tc-pattern {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--text-primary);
  background: var(--bg-inline-code);
  padding: 2px 6px;
  border-radius: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tc-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  color: var(--text-muted);
  border: 1px solid var(--border-color);
  padding: 1px 6px;
  border-radius: 100px;
}

/* 代码 / 输出块（跟随 --bg-codeblock：暗色深底、浅色浅底） */
.tc-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-code);
  background: var(--bg-codeblock);
  border: 1px solid var(--border-color);
  border-radius: 7px;
  padding: 9px 11px;
  max-height: 234px;
  overflow-y: auto;
}

/* read 编辑器风格块（行号列 + 代码列） */
.tc-editor {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  line-height: 1.65;
  background: var(--bg-codeblock);
  border: 1px solid var(--border-color);
  border-radius: 7px;
  padding: 7px 0;
  max-height: 234px;
  overflow: auto;
}
.tc-editor::-webkit-scrollbar-corner {
  background: var(--bg-codeblock);
}
.tc-ed-line {
  display: flex;
  min-width: max-content;
}
.tc-ed-line:hover { background: rgba(255, 255, 255, 0.04); }
.tc-ed-num {
  flex-shrink: 0;
  text-align: right;
  padding: 0 9px 0 5px;
  margin-right: 9px;
  color: var(--text-muted);
  border-right: 1px solid var(--border-color);
  user-select: none;
  position: sticky;
  left: 0;
  background: var(--bg-codeblock);
}
.tc-ed-txt {
  color: var(--text-code);
  white-space: pre;
  padding-right: 11px;
}
/* 浅色主题：hover 改黑色基调 */
body.light-theme .tc-ed-line:hover { background: rgba(0, 0, 0, 0.04); }

/* edit 对照块 */
.tc-diff {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  overflow: hidden;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  line-height: 1.65;
}
.tc-diff-del, .tc-diff-ins {
  padding: 7px 11px 7px 23px;
  white-space: pre-wrap;
  word-break: break-word;
  position: relative;
  max-height: 162px;
  overflow-y: auto;
}
.tc-diff-del::before, .tc-diff-ins::before {
  position: absolute;
  left: 10px;
  font-weight: 700;
}
.tc-diff-del {
  background: rgba(248, 113, 113, 0.08);
  color: #e89b9b;
}
.tc-diff-del::before { content: '-'; color: #f87171; }
.tc-diff-ins {
  background: rgba(52, 211, 153, 0.08);
  color: #8fd9bb;
  border-top: 1px solid var(--border-color);
}
.tc-diff-ins::before { content: '+'; color: #34d399; }
/* 浅色主题下提高 diff 文字对比度 */
body.light-theme .tc-diff-del { color: #b91c1c; }
body.light-theme .tc-diff-ins { color: #047857; }

/* bash 终端块 */
.tc-term {
  background: var(--bg-codeblock);
  border: 1px solid var(--border-color);
  border-radius: 7px;
  padding: 9px 11px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  line-height: 1.7;
}
.tc-term-cmd { color: #7ee2b8; }
.tc-term-cmd::before { content: '$ '; color: #4f566b; }
.tc-term-out {
  color: #8f97ae;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 234px;
  overflow-y: auto;
  margin-top: 4px;
}
/* 浅色主题：终端块改浅底深字 */
body.light-theme .tc-term-cmd { color: #0f7b4f; }
body.light-theme .tc-term-cmd::before { color: #8c959f; }
body.light-theme .tc-term-out { color: #57606a; }
.tc-term-wait { color: #60a5fa; }
body.light-theme .tc-term-wait { color: #2563eb; }

/* 结果行 / 执行中 */
.tc-result {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 11px;
  color: var(--text-secondary);
}
.tc-result i { color: #34d399; font-size: 10.5px; }
.tc-running {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #60a5fa;
}

/* 通用回退段 */
.tc-sec { min-width: 0; }
.tc-label {
  font-size: 10.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-muted);
  margin-bottom: 5px;
}

/* 技能详情统一结构：头部行 / 参数行 / 内容块 / 资源行 */
.sk-head { display: flex; align-items: center; gap: 7px; min-width: 0; }
.sk-head > i { font-size: 10.5px; color: var(--accent-light); flex-shrink: 0; }
.sk-name {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sk-src {
  margin-left: auto;
  flex-shrink: 0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: var(--text-muted);
}
.sk-args {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 14px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: var(--text-secondary);
}
.sk-args b { font-weight: 400; color: var(--text-muted); margin-right: 5px; }
.sk-body {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-code);
  background: var(--bg-codeblock);
  border: 1px solid var(--border-color);
  border-radius: 7px;
  padding: 9px 11px;
  max-height: 216px;
  overflow-y: auto;
}
.sk-body.err { color: #f87171; }
body.light-theme .sk-body.err { color: #dc2626; }
.sk-body::-webkit-scrollbar { width: 4px; }
.sk-body::-webkit-scrollbar-track { background: transparent; }
.sk-body::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }
.sk-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 14px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: var(--text-secondary);
}
.sk-meta i { font-size: 9.5px; color: var(--accent-light); margin-right: 6px; }
.sk-fold summary {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10.5px;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
  list-style: none;
  transition: color 0.15s;
}
.sk-fold summary::-webkit-details-marker { display: none; }
.sk-fold summary:hover { color: var(--text-secondary); }
.sk-fold summary .fa-chevron-right { font-size: 8.5px; transition: transform 0.15s; }
.sk-fold[open] summary .fa-chevron-right { transform: rotate(90deg); }
.sk-fold[open] summary { margin-bottom: 7px; }
.sk-fold-err { color: #f87171; }
body.light-theme .sk-fold-err { color: #dc2626; }
.sk-desc {
  font-size: 10.5px;
  line-height: 1.65;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sk-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  padding: 6px 0;
  border: 1px dashed var(--border-color);
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 10.5px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}
.sk-more:hover {
  color: var(--accent-light);
  border-color: var(--border-active);
  background: var(--bg-hover);
}
.sk-more .fa-angles-right { font-size: 10px; }

/* 技能列表样式 */
.sk-skills {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.sk-skill-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-codeblock);
  border: 1px solid var(--border-color);
  border-radius: 7px;
  transition: border-color 0.15s;
}
.sk-skill-item:hover {
  border-color: var(--border-active);
}
.sk-skill-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-bg);
  border-radius: 6px;
  color: var(--accent-light);
  font-size: 12px;
}
.sk-skill-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.sk-skill-name {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.sk-skill-desc {
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
