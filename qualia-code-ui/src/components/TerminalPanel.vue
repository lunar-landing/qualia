<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { extractBlocks } from '@/utils/steps'

/**
 * 工作区「终端」面板（平移旧 terminal-panel.js）：
 * bash ACTION 与紧随 OBSERVATION 配对为连续终端屏幕，`$` 提示符 + 命令绿字 + 输出灰字；
 * 执行中命令行尾闪烁光标，空闲时保留待命提示符；内容变化自动滚底（模拟真实终端跟随）。
 */
const OUTPUT_LIMIT = 4000

const { t } = useI18n()
const chat = useChatStore()
const sessionStore = useSessionStore()
const ws = useWorkspaceStore()

const sid = computed(() => sessionStore.currentSessionId ?? '')

/** 当前会话全部消息的步骤合并（等价旧 renderMessages 的 allSteps + 流式期间全量重渲） */
const steps = computed(() => chat.messagesOf(sid.value).flatMap((m) => m.steps ?? []))

const blocks = computed(() => extractBlocks(steps.value))
const running = computed(() => {
  const last = blocks.value[blocks.value.length - 1]
  return blocks.value.length > 0 && last?.output === null
})

const screenEl = ref<HTMLElement | null>(null)

function scrollBottom() {
  void nextTick(() => {
    const el = screenEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// 内容变化（块数或末块输出长度）自动滚底；隐藏的 pane 滚动无效但无害
const sig = computed(() => {
  const last = blocks.value[blocks.value.length - 1]
  return `${blocks.value.length}|${last?.output?.length ?? 0}`
})
watch(sig, scrollBottom)

// 切到终端 Tab 时补一次滚底（display:none 期间滚动位置不可靠）
watch(
  () => ws.activeTab,
  (t) => {
    if (t === 'wsTerm') scrollBottom()
  },
)

function truncate(s: unknown, n: number): string {
  const t = String(s ?? '')
  return t.length > n ? t.slice(0, n) + '…' : t
}
</script>

<template>
  <div class="tp-host">
    <div class="tp-shell">
      <div ref="screenEl" class="tp-screen">
        <div v-for="(b, i) in blocks" :key="i" class="tp-entry">
          <div class="tp-cmd">
            <span class="tp-prompt">$</span> {{ b.cmd }}<span v-if="b.output === null" class="tp-cursor"></span>
          </div>
          <div v-if="b.output" class="tp-out">{{ truncate(b.output, OUTPUT_LIMIT) }}</div>
        </div>
        <!-- 空闲时保留待命提示符；执行中光标已挂在命令行尾 -->
        <div v-if="!running" class="tp-idle"><span class="tp-prompt">$</span> <span class="tp-cursor"></span></div>
        <div v-if="blocks.length === 0" class="tp-hint">{{ t('terminal.waiting') }}</div>
      </div>
    </div>
  </div>
</template>

<style>
/* 平移旧 terminal-panel.js 自注入样式（tp- 前缀全局，配色随页面 CSS 变量适配） */
/* 面板宿主：让终端壳撑满整个面板 */
.tp-host {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 11px;
  min-height: 0;
}
.tp-shell {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  overflow: hidden;
  background: var(--terminal-bg);
}

/* 终端屏幕 */
.tp-screen {
  flex: 1;
  overflow-y: auto;
  padding: 11px 13px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  line-height: 1.7;
}
.tp-screen::-webkit-scrollbar { width: 4px; }
.tp-screen::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.14); border-radius: 2px; }
.tp-entry { margin-bottom: 9px; }
.tp-prompt { color: var(--terminal-prompt); user-select: none; }
.tp-cmd {
  color: var(--terminal-success);
  word-break: break-all;
  white-space: pre-wrap;
}
.tp-out {
  color: var(--terminal-idle);
  white-space: pre-wrap;
  word-break: break-word;
  margin-top: 2px;
}
.tp-idle { color: var(--terminal-prompt); }
.tp-hint {
  margin-top: 5px;
  font-size: 10.5px;
  color: var(--text-muted);
}

/* 闪烁光标 */
.tp-cursor {
  display: inline-block;
  width: 6px;
  height: 12px;
  margin-left: 3px;
  vertical-align: -2px;
  background: var(--terminal-success);
  animation: tpBlink 1.1s steps(1) infinite;
}
.tp-idle .tp-cursor { background: var(--terminal-prompt); }
@keyframes tpBlink {
  50% { opacity: 0; }
}

/* 浅色主题：浅底深字，沿用页面浅色终端配色 */
body.light-theme .tp-shell { background: var(--terminal-bg); }
body.light-theme .tp-screen::-webkit-scrollbar-thumb { background: rgba(0, 0, 0, 0.16); }
body.light-theme .tp-prompt { color: var(--terminal-prompt); }
body.light-theme .tp-cmd { color: var(--terminal-success); }
body.light-theme .tp-out { color: var(--terminal-prompt); }
body.light-theme .tp-idle { color: var(--terminal-prompt); }
body.light-theme .tp-hint { color: var(--text-muted); }
body.light-theme .tp-cursor { background: var(--terminal-success); }
body.light-theme .tp-idle .tp-cursor { background: var(--terminal-prompt); }
</style>
