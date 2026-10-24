<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@/utils/markdown'
import { formatWordCount } from '@/utils/format'

/**
 * 资料编辑器（B3 源码编辑）：格式工具条（12 项常用 Markdown 操作）+ 行号栏 + 三档语法着色 + 状态栏 + 快捷键。
 * 纯 textarea 增强不引依赖；插入/包裹走 document.execCommand('insertText') 保留原生 undo 栈。
 * 行号与着色都靠「与 textarea 完全同度量的镜像层」：镜像每行一个块级元素，
 * 其 offsetHeight 即该源行软换行后的实际高度，行号按此高度对齐，故长段换行不会让行号错位；
 * textarea 自身文字透明只留光标，颜色由镜像透出（输入法合成期间临时反色，避免候选文字不可见）。
 */
const props = defineProps<{ modelValue: string; original: string }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'save'): void
  (e: 'cancel'): void
}>()

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

// ===== 镜像层：行高测量 + 语法着色 =====
const hlInner = ref<HTMLElement | null>(null)
const lineHeights = ref<number[]>([])
const scrollTop = ref(0)
const caretLine = ref(0)
const composing = ref(false)
/** 单行基准高度（未测量到之前的兜底），与 --med-line 保持一致 */
const BASE_LINE = 24.375

const lines = computed(() => props.modelValue.split('\n'))
const lineCount = computed(() => lines.value.length)

function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 三档着色：标题（墨·粗）、行内标记与代码围栏（强调）、引用（灰）；其余不着色，避免像 IDE 那样花 */
function colorizeLine(line: string): string {
  const text = esc(line)
  if (!text) return '&#8203;' // 空行留零宽字符占一个行高，行号才能对齐
  if (/^#{1,6}\s/.test(line)) return `<span class="tk-h">${text}</span>`
  if (/^&gt;\s?/.test(text)) return `<span class="tk-q">${text}</span>`
  if (/^\s*(```|~~~)/.test(line)) return `<span class="tk-m">${text}</span>`
  return text.replace(/(`[^`]+`|\*\*[^*]+\*\*|__[^_]+__)/g, (m) => `<span class="tk-m">${m}</span>`)
}

const lineHtml = computed(() => lines.value.map(colorizeLine))

function paneHeight(i: number): string {
  return `${lineHeights.value[i] ?? BASE_LINE}px`
}

/** 逐行取镜像块高度：只读不写，一轮布局即可，rAF 合并连续输入触发的测量 */
function measure() {
  const host = hlInner.value
  if (!host || host.offsetParent === null) return // 预览态隐藏时高度全 0，跳过
  const kids = host.children
  const hs = new Array<number>(kids.length)
  for (let i = 0; i < kids.length; i++) hs[i] = (kids[i] as HTMLElement).offsetHeight
  lineHeights.value = hs
}
let measureRaf = 0
function scheduleMeasure() {
  if (measureRaf) return
  measureRaf = requestAnimationFrame(() => {
    measureRaf = 0
    measure()
  })
}

let ro: ResizeObserver | null = null
onMounted(() => {
  void nextTick(measure)
  // 画布宽度变化会改变软换行位置，行高需重新测量
  ro = new ResizeObserver(scheduleMeasure)
  if (hlInner.value) ro.observe(hlInner.value)
})
onBeforeUnmount(() => ro?.disconnect())

watch([lineHtml, view], () => void nextTick(scheduleMeasure))

function onScroll() {
  scrollTop.value = ta.value?.scrollTop ?? 0
}

function syncCaretLine() {
  const el = ta.value
  if (!el) return
  caretLine.value = el.value.slice(0, el.selectionStart).split('\n').length - 1
}

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
  const rowLines = value.slice(lineStart, lineEnd).split('\n')
  const gen = typeof prefix === 'function' ? prefix : () => prefix
  const all = rowLines.every((l) => stripRe.test(l))
  const next = rowLines.map((l, i) => (all ? l.replace(stripRe, '') : gen(i) + l)).join('\n')
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
  syncCaretLine()
  if (e.key === 'Escape') {
    e.preventDefault()
    emit('cancel')
    return
  }
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

/** 光标移动与选区变化也要更新当前行号（click/方向键/拖选均覆盖） */
function onCaretMove() {
  syncCaretLine()
}
</script>

<template>
  <div class="med">
    <!-- 工具条：白底 + 发丝分组，视图切换右靠不与格式按钮混排 -->
    <div class="med-bar">
      <button class="med-btn mono" :title="t('materials.edH2')" @click="actions.h2()">H2</button>
      <button class="med-btn mono" :title="t('materials.edH3')" @click="actions.h3()">H3</button>
      <span class="med-vsep"></span>
      <button class="med-btn" :title="t('materials.edBold')" @click="actions.bold()"><i class="fas fa-bold"></i></button>
      <button class="med-btn" :title="t('materials.edItalic')" @click="actions.italic()"><i class="fas fa-italic"></i></button>
      <button class="med-btn" :title="t('materials.edInlineCode')" @click="actions.code()"><i class="fas fa-code"></i></button>
      <button class="med-btn" :title="t('materials.edCodeBlock')" @click="actions.codeBlock()"><i class="fas fa-file-code"></i></button>
      <span class="med-vsep"></span>
      <button class="med-btn" :title="t('materials.edQuote')" @click="actions.quote()"><i class="fas fa-quote-left"></i></button>
      <button class="med-btn" :title="t('materials.edUl')" @click="actions.ul()"><i class="fas fa-list-ul"></i></button>
      <button class="med-btn" :title="t('materials.edOl')" @click="actions.ol()"><i class="fas fa-list-ol"></i></button>
      <button class="med-btn" :title="t('materials.edLink')" @click="actions.link()"><i class="fas fa-link"></i></button>
      <span class="med-vsep"></span>
      <button class="med-btn" :title="t('materials.edHr')" @click="actions.hr()"><i class="fas fa-minus"></i></button>
      <button class="med-btn" :title="t('materials.edTable')" @click="actions.table()"><i class="fas fa-table"></i></button>
      <span class="spring"></span>
      <span class="med-hint">{{ t('materials.edHint') }}</span>
      <div class="med-seg">
        <button :class="{ on: view === 'write' }" @click="view = 'write'">
          <i class="fas fa-code"></i>{{ t('materials.viewSource') }}
        </button>
        <button :class="{ on: view === 'preview' }" @click="view = 'preview'">
          <i class="fas fa-eye"></i>{{ t('materials.viewPreview') }}
        </button>
      </div>
    </div>

    <!-- 画布：44px 行号栏 + 编辑区（镜像着色层压在透明 textarea 下） -->
    <div v-show="view === 'write'" class="med-canvas">
      <div class="med-gutter" aria-hidden="true">
        <div class="med-gutter-in" :style="{ transform: `translateY(${-scrollTop}px)` }">
          <div
            v-for="(_, i) in lines"
            :key="i"
            class="ln-no"
            :class="{ on: i === caretLine }"
            :style="{ height: paneHeight(i) }"
          >{{ i + 1 }}</div>
        </div>
      </div>
      <div class="med-edit">
        <!-- eslint-disable-next-line vue/no-v-html —— 逐行自行转义后着色，非用户可控 HTML -->
        <div class="med-hl" aria-hidden="true">
          <div ref="hlInner" class="med-hl-in" :class="{ composing }" :style="{ transform: `translateY(${-scrollTop}px)` }">
            <span v-for="(h, i) in lineHtml" :key="i" class="hl-ln" v-html="h"></span>
          </div>
        </div>
        <textarea
          ref="ta"
          v-model="modelProxy"
          class="med-area"
          :class="{ composing }"
          spellcheck="false"
          @keydown="onKeydown"
          @scroll="onScroll"
          @click="onCaretMove"
          @keyup="onCaretMove"
          @input="onCaretMove"
          @compositionstart="composing = true"
          @compositionend="composing = false"
        ></textarea>
      </div>
    </div>

    <div v-show="view === 'preview'" class="med-preview">
      <!-- eslint-disable-next-line vue/no-v-html —— md 渲染经 renderMarkdown 统一出口（对齐阅读态） -->
      <div class="md mat-md" v-html="previewHtml"></div>
    </div>

    <!-- 状态栏：同步圆点 + 行数/字数/编码，快捷键提示右靠 -->
    <div class="med-status">
      <span class="st" :class="{ dirty }">{{ dirty ? t('materials.edDirty') : t('materials.edSaved') }}</span>
      <span>{{ t('materials.edLines', { n: lineCount }) }}</span>
      <span>{{ formatWordCount(charCount) }} {{ t('materials.unitChars') }}</span>
      <span>UTF-8</span>
      <span class="spring"></span>
      <span>Ctrl+S {{ t('common.save') }} · Esc {{ t('common.cancel') }}</span>
    </div>
  </div>
</template>

<style scoped>
.med {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--mat-card);
  color: var(--mat-ink-2);
}

/* 工具条：白底（不换成灰底，避免与编辑画布抢层次），分组用 14px 竖发丝线 */
.med-bar {
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 16px;
  border-bottom: 1px solid var(--mat-line);
  background: var(--mat-card);
}
.med-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  color: var(--mat-mut);
  border-radius: 7px;
  cursor: pointer;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.16s ease, color 0.16s ease;
}
.med-btn:hover {
  background: var(--mat-wash);
  color: var(--mat-ink);
}
.med-btn.mono {
  width: auto;
  padding: 0 7px;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
  font-size: 10.5px;
}
.med-vsep {
  width: 1px;
  height: 14px;
  background: var(--mat-line);
  margin: 0 8px;
  flex-shrink: 0;
}
.med-hint {
  font-size: 11px;
  color: var(--mat-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 10px;
}
.spring {
  flex: 1;
}
/* 源码 / 预览分段控件：浅洗凹槽，激活项白底浮起 */
.med-seg {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--mat-line);
  border-radius: 9px;
  background: var(--mat-wash);
  flex-shrink: 0;
}
.med-seg button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 10px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--mat-mut);
  font-size: 11.5px;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s ease, color 0.16s ease;
}
.med-seg button:hover {
  color: var(--mat-ink);
}
.med-seg button.on {
  background: var(--mat-card);
  color: var(--mat-ink);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(28, 33, 40, 0.06);
}
.med-seg button i {
  font-size: 9px;
}

.med-canvas {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  overflow: hidden;
}

/* 行号栏：底色比画布深一档，行号色极淡，当前行转强调色 */
.med-gutter {
  overflow: hidden;
  background: var(--mat-surface-2);
  border-right: 1px solid var(--mat-line);
  user-select: none;
}
.med-gutter-in {
  padding: 18px 0;
  will-change: transform;
}
.ln-no {
  padding-right: 12px;
  text-align: right;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  line-height: 24.375px;
  color: var(--mat-line-2);
  overflow: hidden;
}
.ln-no.on {
  color: var(--mat-accent);
  font-weight: 600;
}

.med-edit {
  position: relative;
  min-width: 0;
}
/* 镜像着色层：与 textarea 同内距/同字号/同字距/同行高，只透出颜色不接事件 */
.med-hl {
  position: absolute;
  inset: 0;
  padding: 18px 24px;
  overflow: hidden;
  pointer-events: none;
  white-space: pre-wrap;
  overflow-wrap: break-word;
  word-break: normal;
  tab-size: 2;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  line-height: 24.375px;
  color: var(--mat-ink-2);
}
.med-hl-in {
  will-change: transform;
}
.hl-ln {
  display: block;
  min-height: 24.375px;
}
.med-hl :deep(.tk-h) {
  color: var(--mat-ink);
  font-weight: 700;
}
.med-hl :deep(.tk-m) {
  color: var(--mat-accent);
  font-weight: 600;
}
.med-hl :deep(.tk-q) {
  color: var(--mat-faint);
}
/* 输入法合成中：镜像让位、textarea 文字显形，否则候选串不可见 */
.med-hl-in.composing {
  opacity: 0;
}

.med-area {
  position: absolute;
  inset: 0;
  resize: none;
  border: none;
  outline: none;
  padding: 18px 24px;
  background: transparent;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  line-height: 24.375px;
  tab-size: 2;
  white-space: pre-wrap;
  overflow-wrap: break-word;
  word-break: normal;
  color: transparent;
  caret-color: var(--mat-accent);
  /* 隐藏滚动条以保 textarea 与镜像内容宽度严格一致（换行位置一致，行号才不错位） */
  scrollbar-width: none;
}
.med-area.composing {
  color: var(--mat-ink-2);
}
/* 选区底色压在镜像文字之上：半透明即可，不能遮蔽文字 */
.med-area::selection {
  background: color-mix(in srgb, var(--mat-accent) 18%, transparent);
}
.med-area::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.med-preview {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 26px 32px 60px;
  background: var(--mat-surface-2);
}
.med-preview .md {
  max-width: 672px;
  margin: 0 auto;
}

/* 状态栏：32px，无底色差异，只靠上边线断开 */
.med-status {
  height: 32px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 18px;
  border-top: 1px solid var(--mat-line);
  background: var(--mat-card);
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: var(--mat-faint);
}
.med-status .st {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.med-status .st::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--mat-ok);
}
.med-status .st.dirty::before {
  background: var(--mat-warn);
}
</style>
