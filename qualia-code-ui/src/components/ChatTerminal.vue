<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { emptyStreamState, useChatStore } from '@/stores/chat'
import type { StreamState } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { useConfigStore } from '@/stores/config'
import { useChatStream, tryConsumeQueue } from '@/composables/useChatStream'
import { useThrottledRef } from '@/composables/useThrottledRef'
import { useLightbox } from '@/composables/useLightbox'
import { extractThought, stepFilePath } from '@/utils/steps'
import { argStr, domainOf, shortScriptName, type ToolArgs } from '@/utils/chip'
import { splitSkillMessage } from '@/utils/skillSlash'
import { renderMarkdown } from '@/utils/markdown'
import hljs from 'highlight.js/lib/common'
import { nowTimeStr } from '@/utils/format'
import { historyImageUrl, uploadAttachment } from '@/api/attachment'
import WelcomeHero from '@/components/WelcomeHero.vue'
import ChipDetail from '@/components/ChipDetail.vue'
import FilePreview from '@/components/FilePreview.vue'
import QueuedPanel from '@/components/QueuedPanel.vue'
import type { AgentStep, ChatMessage, HistoryAttachment, PendingAttachment } from '@/types'

/**
 * 终端形态聊天区（ChatArea 的 REPL/CLI 隐喻变体，对齐 terminal-chat-theme-mockup）：
 * 消息 → ❯ 日志行、思考 → :: 暗色行、工具 → $ 命令行 + [ok]/[err]、回答 → 终端排版、输入区 → REPL prompt。
 * 与气泡版共用 chat/session/workspace store 与 useChatStream，仅渲染层不同（用户要求双组件不共存样式）。
 * 样式使用非 scoped 块 + .term-chat 前缀隔离（对齐 ToolChip 先例；v-html 内容与明暗变量覆盖需要全局作用域）。
 */

const EMPTY_STREAM: StreamState = emptyStreamState()
const MAX_ATTACHMENTS = 4
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const MAX_DOC_BYTES = 20 * 1024 * 1024

const { t } = useI18n()
const chat = useChatStore()
const sessionStore = useSessionStore()
const ws = useWorkspaceStore()
const config = useConfigStore()
const { send, stop } = useChatStream()
const { open: openLightbox } = useLightbox()

const sid = computed(() => sessionStore.currentSessionId)
const messages = computed(() => (sid.value ? chat.messagesOf(sid.value) : []))
const stream = computed<StreamState>(() => (sid.value ? chat.streamOf(sid.value) : EMPTY_STREAM))
const sessionTitle = computed(() => sessionStore.currentSession?.title ?? '')
const welcome = computed(() => messages.value.length === 0)
const typingVisible = computed(() => stream.value.status === 'connecting')

/** 用户行拆分：技能指定改写头标签化（支持多技能，保留“请使用技能”前缀与指令文案），普通消息原样 */
function userParts(content: string): { prefix: string; chips: string[]; text: string } {
  const s = splitSkillMessage(content)
  return s ? { prefix: s.prefix, chips: s.names, text: s.rest } : { prefix: '', chips: [], text: content }
}

// ===== 终端行模型：steps 线性展开为思考行 / 命令行（OBSERVATION/ERROR 归属前一 ACTION） =====
interface ThoughtRow {
  kind: 'thought'
  key: string
  text: string
}
interface CmdRow {
  kind: 'cmd'
  key: string
  step: AgentStep
  /** null = 执行中（尚无 OBSERVATION/ERROR） */
  result: { ok: boolean; content: string } | null
}
type TermRow = ThoughtRow | CmdRow

const rowsOf = (msg: ChatMessage): TermRow[] => {
  const out: TermRow[] = []
  let pending: CmdRow | null = null
  ;(msg.steps ?? []).forEach((step, i) => {
    if (step.stepType === 'THOUGHT') {
      const text = extractThought(step)
      if (text) {
        pending = null
        out.push({ kind: 'thought', key: `t${i}`, text })
      }
    } else if (step.stepType === 'ACTION') {
      pending = { kind: 'cmd', key: `s${i}`, step, result: null }
      out.push(pending)
    } else if (pending && (step.stepType === 'OBSERVATION' || step.stepType === 'ERROR')) {
      pending.result = { ok: step.stepType === 'OBSERVATION', content: step.content || '' }
      pending = null
    }
  })
  return out
}

/** ACTION → $ 命令行文本（bash 展示完整命令，其余「工具名 核心参数」） */
const cmdOf = (step: AgentStep): string => {
  const args = (step.toolArgs ?? {}) as ToolArgs
  const name = step.toolName ?? 'tool'
  if (name === 'bash') return String(args.command ?? '')
  if (name === 'read' || name === 'write' || name === 'edit' || name === 'delete') {
    return `${name} ${stepFilePath(step)}`
  }
  if (name === 'grep') return `grep "${argStr(args, 'pattern', 'regex')}"`
  if (name === 'glob') return `glob "${argStr(args, 'pattern')}"`
  if (name === 'web_fetch') return `fetch ${domainOf(args.url)}`
  if (name === 'skill-loader') return `skill load ${argStr(args, 'skill_name')}`
  if (name === 'skill-script-runner') return `script ${shortScriptName(args.script_name, args.skill_name)}`
  if (name === 'skill-reference-reader') return `doc ${argStr(args, 'file_name')}`
  const arg = argStr(args, 'path', 'file_path', 'query', 'pattern', 'command', 'url')
  return arg ? `${name} ${arg}` : name
}

const isLive = (msg: ChatMessage): boolean =>
  !!sid.value && chat.isProcessing(sid.value) && stream.value.msgId === msg.id

// ===== markdown 渲染缓存（v-memo 不可用于 v-for 内，改为按 msg.id 缓存：流式时仅变化的末条重渲） =====
const mdCache = new Map<string, { content: string; html: string }>()

const mdOf = (m: ChatMessage): string => {
  const hit = mdCache.get(m.id)
  if (hit && hit.content === m.content) return hit.html
  const html = renderMarkdown(m.content)
  if (mdCache.size > 500) mdCache.clear() // 跨会话累积防护
  mdCache.set(m.id, { content: m.content, html })
  return html
}

const stateOf = (row: CmdRow, live: boolean): 'ok' | 'err' | 'run' => {
  if (row.result) return row.result.ok ? 'ok' : 'err'
  return live ? 'run' : 'ok'
}

// ===== 思考折叠：完成态默认折叠，live 强制展开；手动开合记录于 openSet =====
const openSet = ref(new Set<string>())
const thinkOpen = (msg: ChatMessage): boolean => openSet.value.has(msg.id) || isLive(msg)
function toggleThink(id: string) {
  const next = new Set(openSet.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  openSet.value = next
}

// ===== 命令行详情展开（复用 ChipDetail，对齐气泡版每行独立 toggle） =====
const openCmds = ref(new Set<string>())
function toggleCmd(key: string) {
  const next = new Set(openCmds.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  openCmds.value = next
}

// ===== 滚动跟随（同 ChatArea：距底 >80px 暂停，内容签名 16ms 节流） =====
const messagesEl = ref<HTMLElement | null>(null)
const stick = ref(true)

const onScroll = () => {
  const el = messagesEl.value
  if (!el) return
  stick.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

const scrollToBottom = () => {
  const el = messagesEl.value
  if (el) el.scrollTop = el.scrollHeight
}

const contentSig = useThrottledRef(
  computed(() => {
    const last = messages.value[messages.value.length - 1]
    return `${messages.value.length}|${stream.value.steps.length}|${last?.content.length ?? 0}`
  }),
)

// ===== 代码高亮：渲染后对未标记的 pre code 执行 hljs，并包装为命令前缀式代码块
// （CL3「$ lang」对齐 term-code-lang-mockup：头部语言标识 + 复制按钮，pre 内部滚动、头部常驻；
// 不带气泡版 code-wrap 包装保持终端盒式；类名用 t-code 前缀避免与全局 .code-head 冲突） =====
const highlightCode = () => {
  const root = messagesEl.value
  if (!root) return
  root.querySelectorAll<HTMLElement>('pre code:not([data-hl])').forEach((code) => {
    code.dataset.hl = '1'
    try {
      hljs.highlightElement(code)
    } catch {
      /* 高亮失败保持原样 */
    }
    /* 包装外壳 + 头部：语言名经 textContent 注入（fenced code info 串任意字符都安全） */
    const pre = code.parentElement
    if (pre?.tagName === 'PRE' && !pre.parentElement?.classList.contains('t-code')) {
      const lang = (code.className.match(/language-([\w#+-]+)/) || [])[1] || 'code'
      const box = document.createElement('div')
      box.className = 't-code'
      const head = document.createElement('div')
      head.className = 't-code-head'
      const cmd = document.createElement('span')
      cmd.className = 'cmd'
      const p = document.createElement('span')
      p.className = 'p'
      p.textContent = '$'
      cmd.append(p, lang)
      const btn = document.createElement('button')
      btn.type = 'button'
      btn.className = 't-code-copy'
      btn.title = t('md.copyCode')
      btn.innerHTML = '<i class="far fa-copy" aria-hidden="true"></i>'
      btn.addEventListener('click', () => {
        void navigator.clipboard.writeText(code.textContent ?? '').then(() => {
          btn.classList.add('copied')
          btn.innerHTML = '<i class="fas fa-check" aria-hidden="true"></i>'
          window.setTimeout(() => {
            btn.classList.remove('copied')
            btn.innerHTML = '<i class="far fa-copy" aria-hidden="true"></i>'
          }, 1500)
        })
      })
      head.append(cmd, btn)
      pre.before(box)
      box.append(head, pre)
    }
  })
}

watch([messages, contentSig], () => {
  void nextTick(() => {
    // 流式期间跳过高亮包装：裸 pre 骨架与 .t-code 对齐（见样式），防每帧重建抖动；流结束后由 processing watch 补一次
    if (!processing.value) highlightCode()
    if (stick.value) scrollToBottom()
  })
})

watch(
  sid,
  (s) => {
    ws.closeFilePreview()
    stick.value = true
    if (s) void chat.ensureHistory(s)
    void nextTick(() => {
      highlightCode()
      scrollToBottom()
    })
  },
  { immediate: true },
)

// ===== braille spinner（connecting / 运行中命令行共用） =====
const SPIN_FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏']
const spin = ref(SPIN_FRAMES[0] ?? '⠋')
let spinIdx = 0
let spinTimer = 0

// ===== REPL 输入区（能力对齐 InputArea：发送/附件/粘贴截图/停止；模型为只读 --model 展示） =====
const input = ref('')
const textareaEl = ref<HTMLTextAreaElement | null>(null)
const fileEl = ref<HTMLInputElement | null>(null)
const pending = ref<PendingAttachment[]>([])
const processing = computed(() => (sid.value ? chat.isProcessing(sid.value) : false))

// 流结束补一次高亮包装（流式期间跳过，见上方 messages watch）
watch(processing, (p) => {
  if (!p) {
    void nextTick(() => {
      highlightCode()
      if (stick.value) scrollToBottom()
    })
  }
})
const modelLabel = computed(() => config.selectedModel || config.defaultModel || '')

// ===== V3 模型 pill：点击弹出菜单切换（config.selectModel 仅前端选中，随请求携带，不落后端配置） =====
const modelOpen = ref(false)
const modelWrapEl = ref<HTMLElement | null>(null)
function pickModel(name: string) {
  config.selectModel(name)
  modelOpen.value = false
}

const placeholder = computed(() =>
  processing.value ? t('terminal.queuePlaceholder') : t('terminal.placeholder'),
)
const timeStr = nowTimeStr()

const autoResize = () => {
  const el = textareaEl.value
  if (!el) return
  el.style.height = '22px'
  if (el.value) el.style.height = `${Math.max(22, Math.min(el.scrollHeight, 160))}px`
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && e.keyCode !== 229) {
    e.preventDefault()
    void doSend()
  }
}

function doSend() {
  const text = input.value.trim()
  const attachments = pending.value.slice()
  if (!text && attachments.length === 0) return
  const queued = !!sid.value && (processing.value || chat.queueOf(sid.value).length > 0)
  if (queued && sid.value) {
    // 流进行中入队（回复完自动接发）；空闲但队列非空（恢复/编辑挂起）→ 入队后立即消费保 FIFO
    if (!chat.enqueue(sid.value, text, attachments)) {
      alert(t('input.queueFull'))
      return
    }
    // 队列条目只留上传回执元数据，释放不再被引用的 blob 预览
    for (const a of attachments) if (a.objectUrl) URL.revokeObjectURL(a.objectUrl)
    tryConsumeQueue(sid.value)
  } else {
    void send(text, attachments).then(() => textareaEl.value?.focus())
  }
  input.value = ''
  pending.value = []
  autoResize()
}

/** esc 停止（终端语义键位；窗口级监听，仅处理中生效） */
const onWindowKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && processing.value) stop()
}

// document 级关闭：模型菜单外部点击 / esc 关闭（对齐 flyout 委托模式）
function onDocClick(e: MouseEvent) {
  if (modelWrapEl.value?.contains(e.target as Node)) return
  modelOpen.value = false
}
function onDocKeyMenu(e: KeyboardEvent) {
  if (e.key === 'Escape') modelOpen.value = false
}

onMounted(() => {
  window.addEventListener('keydown', onWindowKey)
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onDocKeyMenu)
  spinTimer = window.setInterval(() => {
    spinIdx = (spinIdx + 1) % SPIN_FRAMES.length
    spin.value = SPIN_FRAMES[spinIdx] ?? '⠋'
  }, 90)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKey)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onDocKeyMenu)
  window.clearInterval(spinTimer)
})

// ===== 附件（上传换回执逻辑对齐 InputArea，错误文案复用 input.* 段） =====
async function addFiles(files: FileList | File[]) {
  for (const file of Array.from(files)) {
    if (pending.value.length >= MAX_ATTACHMENTS) {
      alert(t('input.maxAttachments'))
      break
    }
    const isImage = !!file.type && file.type.startsWith('image/')
    if (isImage && file.size > MAX_IMAGE_BYTES) {
      alert(t('input.imageTooLarge', { name: file.name }))
      continue
    }
    if (!isImage && file.size > MAX_DOC_BYTES) {
      alert(t('input.docTooLarge', { name: file.name }))
      continue
    }
    try {
      let target = sid.value
      if (!target) target = (await sessionStore.createSession()).id
      const receipt = await uploadAttachment(target, file)
      if (receipt.status !== 'ready' || !receipt.id) {
        alert(
          t('input.parseFailed', {
            name: file.name,
            reason: receipt.error || t('input.unknownReason'),
          }),
        )
        continue
      }
      const att: PendingAttachment = {
        id: receipt.id,
        name: receipt.name,
        type: receipt.type ?? 'document',
        size: file.size,
      }
      if (att.type === 'image') att.objectUrl = URL.createObjectURL(file)
      pending.value.push(att)
    } catch (err) {
      alert(t('input.uploadFailed', { name: file.name, reason: err instanceof Error ? err.message : String(err) }))
    }
  }
}

function removeAttachment(idx: number) {
  const att = pending.value[idx]
  if (att?.objectUrl) URL.revokeObjectURL(att.objectUrl)
  pending.value.splice(idx, 1)
}

const onPaste = (e: ClipboardEvent) => {
  const items = e.clipboardData?.items
  if (!items) return
  const files: File[] = []
  for (const item of Array.from(items)) {
    if (item.type && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) files.push(file)
    }
  }
  if (files.length) {
    e.preventDefault()
    void addFiles(files)
  }
}

const onFileChange = () => {
  if (fileEl.value?.files?.length) void addFiles(fileEl.value.files)
  if (fileEl.value) fileEl.value.value = ''
}

// ===== 附件图片 src（实时 blob 优先，历史走会话回显接口） =====
const imageSrc = (att: HistoryAttachment): string => att.dataUrl || historyImageUrl(sid.value ?? '', att.name)
</script>

<template>
  <section class="term-chat">
    <!-- 右上角悬浮操作舱：工作区面板开关（仅面板收起时展示；视图切换已移至设置悬浮菜单） -->
    <div v-if="ws.panelCollapsed" class="chat-float-actions">
      <button class="chat-float-btn" :title="t('chat.workspacePanel')" @click="ws.togglePanel()">
        <i class="fas fa-layer-group"></i>
      </button>
    </div>

    <!-- ===== 会话流 ===== -->
    <div ref="messagesEl" class="term-scroll" @scroll="onScroll">
      <!-- 欢迎态：banner + 可点建议（输入区常驻底部，对齐 mockup） -->
      <WelcomeHero v-if="welcome" />

      <template v-else>
        <div class="t-meta"># {{ sessionTitle }}</div>

        <template v-for="m in messages" :key="m.id">
          <!-- 用户消息 → ❯ 日志行（技能指定渲染为高亮标签 + 正文） -->
          <div v-if="m.role === 'user'" class="t-user">
            <div class="row">
              <span class="pfx">❯</span>
              <span v-if="userParts(m.content).chips.length" class="txt">{{ userParts(m.content).prefix }}<span v-for="n in userParts(m.content).chips" :key="n" class="t-skill"><i class="fas fa-bolt"></i>{{ n }}</span>{{ userParts(m.content).text }}</span>
              <span v-else class="txt">{{ userParts(m.content).text }}</span>
              <span class="tm">{{ timeStr }}</span>
            </div>
            <div v-if="m.attachments?.length" class="t-attach">
              +{{ m.attachments.length }} {{ t('terminal.attachments') }}:
              <template v-for="(att, ai) in m.attachments" :key="att.name">
                <span v-if="ai > 0"> · </span>
                <a
                  v-if="att.type === 'IMAGE'"
                  class="lk"
                  :title="att.name"
                  @click="openLightbox(imageSrc(att))"
                >{{ att.name }}</a>
                <span v-else>{{ att.name }}</span>
              </template>
            </div>
          </div>

          <!-- 助手消息（无步骤）→ 终端排版正文 -->
          <div v-else-if="!(m.steps?.length)" class="t-answer">
            <div class="md" v-html="mdOf(m)"></div>
          </div>

          <!-- 助手消息（有步骤）→ 思考折叠 + 命令行 + 回答 -->
          <div v-else class="t-block">
            <div class="t-think-toggle" :class="{ open: thinkOpen(m) }" @click="toggleThink(m.id)">
              <span class="tri"></span> <span class="lbl">{{ t('chat.thoughtProcess') }}</span> ·
              {{ (m.steps ?? []).filter((s) => s.stepType === 'THOUGHT').length }}
            </div>
            <div v-show="thinkOpen(m)" class="t-flow">
              <template v-for="row in rowsOf(m)" :key="row.key">
                <div v-if="row.kind === 'thought'" class="t-think">{{ row.text }}</div>
                <template v-else>
                  <div class="t-cmd" @click="toggleCmd(row.key)">
                    <span class="dollar">$</span>
                    <span class="args">{{ cmdOf(row.step) }}</span>
                    <span class="st" :class="stateOf(row, isLive(m))">
                      <template v-if="stateOf(row, isLive(m)) === 'run'">{{ spin }}</template>
                      <template v-else-if="stateOf(row, isLive(m)) === 'err'">[err]</template>
                      <template v-else>[ok]</template>
                    </span>
                  </div>
                  <div v-show="openCmds.has(row.key)" class="term-detail">
                    <ChipDetail :step="row.step" :result="row.result" />
                  </div>
                </template>
              </template>
            </div>

            <div v-if="m.content" class="t-answer">
              <div class="md" v-html="mdOf(m)"></div>
              <span v-if="isLive(m)" class="cursor"></span>
            </div>
          </div>
        </template>

        <!-- connecting → spinner 行 -->
        <div v-show="typingVisible" class="t-typing">
          <span class="pfx">{{ spin }}</span>
          <span class="dim">{{ t('terminal.connecting') }}</span>
        </div>
      </template>
    </div>

    <!-- ===== V3 控制台面板输入区：待发队列 / 输入行 / 底栏 ===== -->
    <div class="term-console">
      <!-- 待发送队列：置于控制台顶部（v3 极简稿终端变体） -->
      <QueuedPanel variant="terminal" />
      <div class="tc-body">
        <div v-show="pending.length" class="tc-atts">
          <span v-for="(att, idx) in pending" :key="att.id" class="tc-att-chip">
            {{ att.name }}
            <button class="x" :title="t('input.remove')" @click="removeAttachment(idx)">×</button>
          </span>
        </div>
        <div class="tc-row">
          <span class="pfx">❯</span>
          <textarea
            ref="textareaEl"
            v-model="input"
            rows="1"
            :placeholder="placeholder"
            spellcheck="false"
            @input="autoResize"
            @keydown="onKeydown"
            @paste="onPaste"
          ></textarea>
        </div>
      </div>
      <div class="tc-foot">
        <template v-if="processing">
          <span class="tc-live">{{ spin }}</span>
          <span>{{ t('terminal.generating') }}</span>
          <span class="tc-spring"></span>
          <button class="tc-btn stop" @click="stop()">
            <span class="key">esc</span>{{ t('terminal.stop') }}
          </button>
        </template>
        <template v-else>
          <button class="tc-att" :title="t('input.attachTitle')" @click="fileEl?.click()">
            + {{ t('terminal.attach') }}<template v-if="pending.length"> {{ pending.length }}</template>
          </button>
          <span class="tc-hint"><span class="key">⏎</span>{{ t('terminal.send') }}</span>
          <span class="tc-spring"></span>
          <div ref="modelWrapEl" class="tc-model-wrap">
            <button class="tc-model" :title="t('terminal.modelSwitch')" @click="modelOpen = !modelOpen">
              --model {{ modelLabel || t('terminal.modelDefault') }} <span class="tc-tri">▾</span>
            </button>
            <div v-show="modelOpen" class="tc-model-menu">
              <button
                class="tc-model-item"
                :class="{ on: !config.selectedModel }"
                @click="pickModel('')"
              >{{ t('terminal.modelDefault') }}</button>
              <button
                v-for="m in config.models"
                :key="m.name"
                class="tc-model-item"
                :class="{ on: m.name === config.selectedModel }"
                @click="pickModel(m.name)"
              >{{ m.name }}</button>
            </div>
          </div>
          <button class="tc-btn" @click="doSend()">{{ t('terminal.send') }} ⏎</button>
        </template>
      </div>
      <input
        ref="fileEl"
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp,.pdf,.docx,.txt,.md,.markdown,.csv,.json,.xml,.yml,.yaml,.log,.properties,.sql,.html,.js,.ts,.java,.py,.go,.c,.cpp,.h,.sh,.bat"
        multiple
        hidden
        @change="onFileChange"
      />
    </div>

    <!-- 文件预览层：侧栏文件树点击后在聊天区整栏展示（复用全局样式） -->
    <div v-if="ws.previewFile" class="chat-file-preview">
      <FilePreview :path="ws.previewFile.path" @close="ws.closeFilePreview()" />
    </div>
  </section>
</template>

<style>
/* ===== 终端聊天区（非 scoped：v-html 内容与明暗变量覆盖需全局作用域；.term-chat 前缀隔离） ===== */
/* 基础变量派生自现有 --terminal-*（暗色默认），亮色由 body.light-theme 覆盖（对齐 index.css 两套主题） */
.term-chat {
  --t-line: #21262d;
  --t-faint: #4f566b;
  --t-bg-soft: #10161d;
  --t-code-bg: #0b0e14;
  --t-red: #ff7b72;
  --t-amber: #e3b341;
  --t-blue: #79c0ff;
  --t-sel: rgba(126, 231, 135, 0.1);
  background: var(--terminal-bg);
  color: var(--terminal-text);
  font-family: 'JetBrains Mono', 'Cascadia Code', 'SF Mono', Consolas, 'Sarasa Mono SC',
    'Noto Sans Mono CJK SC', 'Microsoft YaHei', monospace;
  font-size: 13px;
  line-height: 1.7;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
  transition: background 0.25s, color 0.25s;
}

body.light-theme .term-chat {
  --t-line: #d0d7de;
  --t-faint: #8c959f;
  --t-bg-soft: #eef1f4;
  --t-code-bg: #eef1f4;
  --t-red: #cf222e;
  --t-amber: #9a6700;
  --t-blue: #0969da;
  --t-sel: rgba(15, 123, 79, 0.08);
}

.term-chat .term-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 18px 24px 22px;
  min-width: 0;
}

.term-chat .term-scroll::-webkit-scrollbar {
  width: 8px;
}

.term-chat .term-scroll::-webkit-scrollbar-thumb {
  background: var(--t-line);
  border-radius: 4px;
}

/* 会话 meta 行 */
.term-chat .t-meta {
  color: var(--t-faint);
  font-size: 11.5px;
  margin-bottom: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 提示符 */
.term-chat .pfx {
  color: var(--terminal-success);
  flex-shrink: 0;
}

/* 用户消息 → 日志行 */
.term-chat .t-user {
  margin: 16px 0 4px;
}

.term-chat .t-user .row {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.term-chat .t-user .txt {
  color: var(--terminal-text);
  font-weight: 600;
  word-break: break-word;
  white-space: pre-wrap;
}

.term-chat .t-user .tm {
  margin-left: auto;
  color: var(--t-faint);
  font-size: 11px;
  flex-shrink: 0;
}

/* 技能指定标签（❯ 行内内联混排，随文本流换行） */
.term-chat .t-skill {
  color: var(--terminal-success);
  font-weight: 600;
  margin: 0 5px;
}

.term-chat .t-skill i {
  margin-right: 5px;
  font-size: 10px;
}

.term-chat .t-attach {
  margin: 4px 0 0 22px;
  color: var(--terminal-idle);
  font-size: 11.5px;
}

.term-chat .t-attach .lk {
  color: var(--t-blue);
  cursor: pointer;
  border-bottom: 1px dashed color-mix(in srgb, var(--t-blue) 50%, transparent);
}

/* 思考过程折叠行 + :: 暗色斜体行 */
.term-chat .t-think-toggle {
  margin: 10px 0 4px 22px;
  color: var(--t-faint);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.term-chat .t-think-toggle:hover {
  color: var(--terminal-idle);
}

.term-chat .t-think-toggle .tri {
  display: inline-block;
  width: 12px;
  color: var(--terminal-success);
}

.term-chat .t-think-toggle.open .tri::before {
  content: '▾';
}

.term-chat .t-think-toggle:not(.open) .tri::before {
  content: '▸';
}

.term-chat .t-flow {
  min-width: 0;
}

.term-chat .t-think {
  margin: 12px 0 4px 22px;
  color: var(--terminal-idle);
  font-style: italic;
  font-size: 12.5px;
  word-break: break-word;
}

.term-chat .t-think::before {
  content: ':: ';
  font-style: normal;
  color: var(--t-faint);
}

/* 工具调用 → $ 命令行 + 状态 */
.term-chat .t-cmd {
  margin: 10px 0 2px 22px;
  cursor: pointer;
  min-width: 0;
}

.term-chat .t-cmd:hover .args {
  color: var(--terminal-success);
}

.term-chat .t-cmd .dollar {
  color: var(--terminal-success);
  margin-right: 8px;
}

.term-chat .t-cmd .args {
  color: var(--terminal-text);
  word-break: break-all;
  transition: color 0.15s;
}

.term-chat .t-cmd .st {
  margin-left: 8px;
  font-size: 11.5px;
  flex-shrink: 0;
}

.term-chat .t-cmd .st.ok {
  color: var(--terminal-success);
}

.term-chat .t-cmd .st.err {
  color: var(--t-red);
}

.term-chat .t-cmd .st.run {
  color: var(--t-amber);
  display: inline-block;
  min-width: 1.2em;
}

/* 命令行详情（复用 ChipDetail，终端风容器） */
.term-chat .term-detail {
  margin: 6px 0 8px 44px;
  border: 1px solid var(--t-line);
  border-radius: 6px;
  background: var(--t-bg-soft);
  padding: 8px;
  overflow: hidden;
}

/* 回答 → 终端排版正文（md 为 v-html 注入，样式走 .term-chat 前缀全局作用域） */
.term-chat .t-answer {
  margin: 14px 0 6px 22px;
  min-width: 0;
}

.term-chat .t-answer .md p {
  margin: 8px 0;
}

.term-chat .t-answer .md ul,
.term-chat .t-answer .md ol {
  margin: 6px 0;
  padding-left: 16px;
}

.term-chat .t-answer .md li {
  padding-left: 4px;
}

.term-chat .t-answer .md h1,
.term-chat .t-answer .md h2,
.term-chat .t-answer .md h3,
.term-chat .t-answer .md h4 {
  margin: 14px 0 4px;
  font-weight: 700;
}

.term-chat .t-answer .md h1::before,
.term-chat .t-answer .md h2::before,
.term-chat .t-answer .md h3::before {
  content: '── ';
  color: var(--terminal-success);
}

.term-chat .t-answer .md h1::after,
.term-chat .t-answer .md h2::after,
.term-chat .t-answer .md h3::after {
  content: ' ──';
  color: var(--terminal-success);
}

.term-chat .t-answer .md code {
  background: color-mix(in srgb, var(--terminal-text) 9%, transparent);
  color: var(--t-blue);
  border-radius: 4px;
  padding: 0 5px;
  font-size: 12px;
}

/* 代码块外壳：CL3 命令前缀式（对齐 term-code-lang-mockup）——边框/圆角/底色移至外壳，
   头部 $ 绿提示符 + 语言名 + 复制按钮，pre 内部滚动使头部常驻；
   底色比终端背景深一档（--t-code-bg：夜间 #0b0e14 / 日间 #eef1f4），仅靠微差与描边区分轮廓 */
.term-chat .t-answer .md .t-code {
  background: var(--t-code-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  margin: 12px 0;
}

.term-chat .t-answer .md .t-code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  border-bottom: 1px solid var(--border-color);
}

.term-chat .t-answer .md .t-code-head .cmd {
  font-size: 11px;
  color: var(--terminal-idle);
}

.term-chat .t-answer .md .t-code-head .cmd .p {
  color: var(--terminal-success);
  font-weight: 600;
  margin-right: 7px;
}

.term-chat .t-answer .md .t-code-copy {
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 4px;
  font-size: 12px;
  color: var(--terminal-idle);
  opacity: 0.7;
  transition: color 0.15s, opacity 0.15s;
}

.term-chat .t-answer .md .t-code-copy:hover {
  color: var(--terminal-success);
  opacity: 1;
}

.term-chat .t-answer .md .t-code-copy.copied {
  color: var(--terminal-success);
  opacity: 1;
}

.term-chat .t-answer .md pre {
  background: var(--t-code-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 0;
  margin: 12px 0;
  max-height: 560px;
  overflow: auto;
}
/* 包装后外壳接管边框与外边距，pre 退化为纯滚动容器（裸 pre 骨架与 .t-code 对齐，防流式重建抖动） */
.term-chat .t-answer .md .t-code > pre {
  background: transparent;
  border: none;
  border-radius: 0;
  margin: 0;
}

/* 代码对齐气泡版墨线加重定稿（index.css .msg .bubble pre code）：字重 500、基础色 #e6edf3、同字体栈 */
.term-chat .t-answer .md pre code {
  display: block;
  padding: 13px 16px;
  background: transparent;
  border: none;
  overflow-x: visible;
  width: max-content;
  min-width: 100%;
  color: #e6edf3;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  font-weight: 500;
}

/* 日间基础色对齐气泡版（body.light-theme .msg .bubble pre code → --text-code） */
body.light-theme .term-chat .t-answer .md pre code {
  color: var(--text-code);
}

.term-chat .t-answer .md a {
  color: var(--t-blue);
  text-decoration: none;
  border-bottom: 1px dashed color-mix(in srgb, var(--t-blue) 50%, transparent);
}

.term-chat .t-answer .md blockquote {
  border-left: 2px solid var(--t-line);
  margin: 8px 0;
  padding: 2px 12px;
  color: var(--terminal-idle);
}

/* 表格：TE3 绿表头强调（对齐 term-table-mockup）——无竖线，表头绿字 + 半透明绿重底线，hover 行高亮 */
.term-chat .t-answer .md table {
  display: block;
  overflow-x: auto;
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 12px;
}

.term-chat .t-answer .md th,
.term-chat .t-answer .md td {
  padding: 5px 12px;
  text-align: left;
  border-bottom: 1px solid var(--t-line);
}

.term-chat .t-answer .md th {
  font-weight: 600;
  color: var(--terminal-success);
  font-size: 11.5px;
  border-bottom: 2px solid color-mix(in srgb, var(--terminal-success) 45%, transparent);
}

.term-chat .t-answer .md tr:last-child td {
  border-bottom: none;
}

.term-chat .t-answer .md tbody tr:hover td {
  background: var(--t-sel);
}

.term-chat .t-answer .md img {
  max-width: 100%;
  border-radius: 6px;
}

.term-chat .t-answer .md hr {
  border: none;
  border-top: 1px dashed var(--t-line);
  margin: 12px 0;
}

/* 流式光标 */
.term-chat .cursor {
  display: inline-block;
  width: 0.62em;
  color: var(--terminal-success);
  animation: term-blink 1.1s steps(1) infinite;
}

.term-chat .cursor::before {
  content: '▊';
}

@keyframes term-blink {
  0%,
  49% {
    opacity: 1;
  }
  50%,
  100% {
    opacity: 0;
  }
}

/* connecting spinner 行 */
.term-chat .t-typing {
  margin: 14px 0 4px 22px;
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.term-chat .t-typing .dim {
  color: var(--t-faint);
  font-size: 12px;
}

/* ===== 欢迎态 hero：复用气泡模式 WelcomeHero 组件（同一 DOM/样式），终端容器为普通块需补水平居中与满高拉伸 ===== */
.term-chat .term-scroll > .welcome-hero {
  margin-inline: auto;
  min-height: 100%;
}

/* ===== V3 控制台面板输入区：输入行 / 底栏 两段式 ===== */
.term-chat .term-console {
  border-top: 1px solid var(--t-line);
  flex-shrink: 0;
  min-width: 0;
}

.term-chat .tc-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 16px;
  background: var(--t-bg-soft);
  font-size: 11px;
  color: var(--t-faint);
}

.term-chat .tc-foot { border-top: 1px solid var(--t-line); }
.term-chat .tc-spring { flex: 1; }
.term-chat .tc-hint { white-space: nowrap; }

.term-chat .tc-foot .key {
  display: inline-block;
  border: 1px solid var(--t-line);
  border-bottom-width: 2px;
  border-radius: 5px;
  padding: 0 6px;
  color: var(--t-faint);
  font-size: 10.5px;
  margin-right: 4px;
}

/* 模型文字 + 向上生长的下拉菜单 */
.term-chat .tc-model-wrap { position: relative; flex-shrink: 0; }
.term-chat .tc-model {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  font-size: 11px;
  color: var(--terminal-success);
  cursor: pointer;
  transition: color 0.15s;
}
.term-chat .tc-model:hover { text-decoration: underline; }
.term-chat .tc-tri { font-size: 9px; opacity: 0.7; margin-left: 2px; }
.term-chat .tc-model-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 6px);
  min-width: 200px;
  max-height: 260px;
  overflow-y: auto;
  background: var(--t-bg-soft);
  border: 1px solid var(--t-line);
  border-radius: 8px;
  padding: 4px;
  z-index: 40;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.4);
}
body.light-theme .term-chat .tc-model-menu { box-shadow: 0 10px 28px rgba(31, 35, 40, 0.14); }
.term-chat .tc-model-item {
  display: block;
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  border-radius: 5px;
  padding: 4px 9px;
  font: inherit;
  font-size: 11.5px;
  color: var(--t-dim);
  cursor: pointer;
}
.term-chat .tc-model-item:hover { background: rgba(255, 255, 255, 0.05); color: var(--terminal-text); }
body.light-theme .term-chat .tc-model-item:hover { background: rgba(31, 35, 40, 0.06); }
.term-chat .tc-model-item.on { color: var(--terminal-success); }
.term-chat .tc-model-item.on::after { content: ' ✓'; }

/* 输入行 + 待发附件 chips */
.term-chat .tc-body { padding: 11px 16px; }
.term-chat .tc-atts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.term-chat .tc-att-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--t-line);
  border-radius: 6px;
  padding: 1px 8px;
  font-size: 11.5px;
  color: var(--t-dim);
  background: var(--terminal-bg);
}
.term-chat .tc-att-chip .x {
  background: none;
  border: none;
  padding: 0 2px;
  font-size: 13px;
  line-height: 1;
  color: var(--t-faint);
  cursor: pointer;
}
.term-chat .tc-att-chip .x:hover { color: var(--t-red); }

.term-chat .tc-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.term-chat .tc-row textarea {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  resize: none;
  color: var(--terminal-text);
  font: inherit;
  line-height: inherit;
  padding: 0;
  min-width: 0;
  height: 22px;
  caret-color: var(--terminal-success);
}

.term-chat .tc-row textarea::placeholder {
  color: var(--t-faint);
}

/* 底栏：附件链接 / 键帽提示 / 行动按钮 */
.term-chat .tc-att {
  background: none;
  border: none;
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  padding: 0;
  color: var(--t-dim);
  white-space: nowrap;
}
.term-chat .tc-att:hover { color: var(--terminal-success); }

.term-chat .tc-live { color: var(--terminal-success); }

.term-chat .tc-btn {
  background: rgba(126, 231, 135, 0.14);
  border: none;
  border-radius: 6px;
  padding: 2px 13px;
  font: inherit;
  font-size: 11.5px;
  color: var(--terminal-success);
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.term-chat .tc-btn:hover { background: rgba(126, 231, 135, 0.24); }
.term-chat .tc-btn.stop { background: rgba(255, 123, 114, 0.14); color: var(--t-red); }
.term-chat .tc-btn.stop:hover { background: rgba(255, 123, 114, 0.24); }

/* 日间按钮底色（低饱和同色系） */
body.light-theme .term-chat .tc-btn { background: rgba(15, 123, 79, 0.1); }
body.light-theme .term-chat .tc-btn:hover { background: rgba(15, 123, 79, 0.18); }
body.light-theme .term-chat .tc-btn.stop { background: rgba(207, 34, 46, 0.1); }
body.light-theme .term-chat .tc-btn.stop:hover { background: rgba(207, 34, 46, 0.18); }
</style>
