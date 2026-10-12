<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@/utils/markdown'
import { formatWordCount } from '@/utils/format'

/**
 * 资料编辑器：格式工具条（12 项常用 Markdown 操作）+ 编辑/预览切换 + 状态栏（未保存点/字数）+ 快捷键。
 * 纯 textarea 增强不引依赖；插入/包裹走 document.execCommand('insertText') 保留原生 undo 栈。
 * 排版规格对齐设置弹窗 app-md-editor（JetBrains Mono / bg-input 分区线语言）。
 */
const props = defineProps<{ modelValue: string; original: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void; (e: 'save'): void }>()

const { t } = useI18n()
const ta = ref<HTMLTextAreaElement | null>(null)
const view = ref<'write' | 'preview'>('write')

const modelProxy = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v),
})
const dirty = computed(() => props.modelValue !== props.original)
const charCount = computed(() => props.modelValue.replace(/\s/g, '').length)
const previewHtml = computed(() => (view.value === 'preview' ? renderMarkdown(props.modelValue) : ''))

/** 光标处插入（替换选区），可选收尾选中占位区间；保留原生 undo 栈 */
function insertText(text: string, selStart?: number, selEnd?: number) {
  const el = ta.value
  if (!el) return
  el.focus()
  document.execCommand('insertText', false, text)
  if (selStart != null) el.setSelectionRange(selStart, selEnd ?? selStart)
}

/** 包裹选区：选区/其外侧已含完整标记对则解开，无选区时插入占位并选中占位词 */
function wrap(mark: string, placeholder: string) {
  const el = ta.value
  if (!el) return
  const s = el.selectionStart
  const e = el.selectionEnd
  const value = el.value
  const inner = value.slice(s, e)
  if (e > s && inner.startsWith(mark) && inner.endsWith(mark) && inner.length >= mark.length * 2) {
    el.setSelectionRange(s + mark.length, e - mark.length)
    return // 已包裹：收选区到内侧，用户直接重打或再操作
  }
  if (e > s && value.slice(s - mark.length, s) === mark && value.slice(e, e + mark.length) === mark) {
    el.setSelectionRange(s - mark.length, e + mark.length)
    insertText(inner)
    el.setSelectionRange(s - mark.length, e - mark.length)
    return
  }
  if (inner) {
    insertText(mark + inner + mark)
    el.setSelectionRange(s + mark.length, e + mark.length)
  } else {
    insertText(mark + placeholder + mark, s + mark.length, s + mark.length + placeholder.length)
  }
}

/** 行前缀 toggle（标题/引用/列表）：对选区覆盖到的每一行整体加/去前缀 */
function togglePrefix(prefix: string | ((i: number) => string), stripRe: RegExp) {
  const el = ta.value
  if (!el) return
  const value = el.value
  const s = el.selectionStart
  const e = el.selectionEnd
  const lineStart = value.lastIndexOf('\n', s - 1) + 1
  let lineEnd = value.indexOf('\n', e)
  if (lineEnd < 0) lineEnd = value.length
  const lines = value.slice(lineStart, lineEnd).split('\n')
  const gen = typeof prefix === 'function' ? prefix : () => prefix
  const all = lines.every((l) => stripRe.test(l))
  const next = lines.map((l, i) => (all ? l.replace(stripRe, '') : gen(i) + l)).join('\n')
  el.focus()
  el.setSelectionRange(lineStart, lineEnd)
  document.execCommand('insertText', false, next)
  el.setSelectionRange(lineStart, lineStart + next.length)
}

/** 块级插入：光标处前后留空行 */
function insertBlock(text: string) {
  const el = ta.value
  if (!el) return
  const s = el.selectionStart
  const before = el.value.slice(0, s)
  const lead = before && !before.endsWith('\n\n') ? (before.endsWith('\n') ? '\n' : '\n\n') : ''
  insertText(lead + text + '\n')
}

const actions = {
  h2: () => togglePrefix('## ', /^##\s/),
  h3: () => togglePrefix('### ', /^###\s/),
  bold: () => wrap('**', '加粗文本'),
  italic: () => wrap('*', '斜体文本'),
  code: () => wrap('`', '代码'),
  quote: () => togglePrefix('> ', /^>\s?/),
  ul: () => togglePrefix('- ', /^-\s?/),
  ol: () => togglePrefix((i) => `${i + 1}. `, /^\d+\.\s?/),
  link: () => {
    const el = ta.value
    if (!el) return
    const s = el.selectionStart
    const label = el.value.slice(s, el.selectionEnd) || '链接文本'
    insertText(`[${label}](url)`, s + label.length + 3, s + label.length + 6)
  },
  codeBlock: () => {
    const el = ta.value
    if (!el) return
    const sel = el.value.slice(el.selectionStart, el.selectionEnd)
    if (sel.includes('\n')) insertText('```\n' + sel + '\n```')
    else insertBlock('```\n代码\n```')
  },
  hr: () => insertBlock('---'),
  table: () => insertBlock('| 列一 | 列二 | 列三 |\n| --- | --- | --- |\n|  |  |  |'),
}

function onKeydown(e: KeyboardEvent) {
  if (!(e.ctrlKey || e.metaKey)) return
  const k = e.key.toLowerCase()
  if (k === 'b') {
    e.preventDefault()
    actions.bold()
  } else if (k === 'i') {
    e.preventDefault()
    actions.italic()
  } else if (k === 'k') {
    e.preventDefault()
    actions.link()
  } else if (k === 's') {
    e.preventDefault()
    emit('save')
  }
}
</script>

<template>
  <div class="med">
    <div class="med-bar">
      <div class="med-tabs">
        <button :class="{ on: view === 'write' }" @click="view = 'write'">{{ t('materials.edEdit') }}</button>
        <button :class="{ on: view === 'preview' }" @click="view = 'preview'">{{ t('materials.edPreview') }}</button>
      </div>
      <span class="med-sep"></span>
      <template v-if="view === 'write'">
        <button class="med-btn mono" :title="t('materials.edH2')" @click="actions.h2()">H2</button>
        <button class="med-btn mono" :title="t('materials.edH3')" @click="actions.h3()">H3</button>
        <span class="med-sep"></span>
        <button class="med-btn" :title="t('materials.edBold')" @click="actions.bold()"><i class="fas fa-bold"></i></button>
        <button class="med-btn" :title="t('materials.edItalic')" @click="actions.italic()"><i class="fas fa-italic"></i></button>
        <button class="med-btn" :title="t('materials.edInlineCode')" @click="actions.code()"><i class="fas fa-code"></i></button>
        <button class="med-btn" :title="t('materials.edCodeBlock')" @click="actions.codeBlock()"><i class="fas fa-file-code"></i></button>
        <span class="med-sep"></span>
        <button class="med-btn" :title="t('materials.edQuote')" @click="actions.quote()"><i class="fas fa-quote-left"></i></button>
        <button class="med-btn" :title="t('materials.edUl')" @click="actions.ul()"><i class="fas fa-list-ul"></i></button>
        <button class="med-btn" :title="t('materials.edOl')" @click="actions.ol()"><i class="fas fa-list-ol"></i></button>
        <button class="med-btn" :title="t('materials.edLink')" @click="actions.link()"><i class="fas fa-link"></i></button>
        <span class="med-sep"></span>
        <button class="med-btn" :title="t('materials.edHr')" @click="actions.hr()"><i class="fas fa-minus"></i></button>
        <button class="med-btn" :title="t('materials.edTable')" @click="actions.table()"><i class="fas fa-table"></i></button>
      </template>
      <span class="spring"></span>
      <span class="med-hint">{{ t('materials.edHint') }}</span>
    </div>

    <textarea
      v-show="view === 'write'"
      ref="ta"
      v-model="modelProxy"
      class="med-area"
      spellcheck="false"
      @keydown="onKeydown"
    ></textarea>
    <div v-show="view === 'preview'" class="med-preview">
      <!-- eslint-disable-next-line vue/no-v-html —— md 渲染经 renderMarkdown 统一出口（对齐阅读态） -->
      <div class="md" v-html="previewHtml"></div>
    </div>

    <div class="med-status">
      <span class="lang">Markdown</span>
      <span v-if="dirty" class="dot" :title="t('materials.edUnsaved')"></span>
      <span class="spring"></span>
      <span class="count">{{ formatWordCount(charCount) }} {{ t('materials.charsShort') }}</span>
    </div>
  </div>
</template>

<style scoped>
.med {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.med-bar {
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 14px;
  border-bottom: 1px solid var(--border-color);
}
.med-tabs {
  display: flex;
  gap: 2px;
  margin-right: 6px;
}
.med-tabs button {
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  padding: 5px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.med-tabs button.on {
  background: var(--bg-hover);
  color: var(--text-primary);
  font-weight: 600;
}
.med-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  border-radius: 6px;
  cursor: pointer;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;
}
.med-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.med-btn.mono {
  width: auto;
  padding: 0 7px;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
  font-size: 10.5px;
}
.med-sep {
  width: 1px;
  height: 14px;
  background: var(--border-color);
  margin: 0 5px;
  flex-shrink: 0;
}
.med-hint {
  font-size: 11px;
  color: var(--text-secondary);
  opacity: 0.75;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.spring {
  flex: 1;
}
.med-area {
  flex: 1;
  min-height: 0;
  resize: none;
  border: none;
  outline: none;
  padding: 22px 28px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-primary);
  background: var(--bg-primary);
  caret-color: var(--accent);
}
.med-preview {
  flex: 1;
  overflow-y: auto;
  padding: 26px 32px 60px;
}
.med-preview .md {
  max-width: 720px;
  margin: 0 auto;
}
.med-status {
  height: 28px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border-top: 1px solid var(--border-color);
  font-size: 11px;
  color: var(--text-secondary);
}
.med-status .lang {
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.5px;
}
.med-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
}
.med-status .count {
  font-variant-numeric: tabular-nums;
}
</style>
