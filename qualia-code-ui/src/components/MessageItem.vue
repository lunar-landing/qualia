<script setup lang="ts">
import { computed, nextTick, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { usePreviewStore } from '@/stores/preview'
import { useLightbox } from '@/composables/useLightbox'
import { useThrottledRef } from '@/composables/useThrottledRef'
import { extractThought } from '@/utils/steps'
import { docIconMeta, nowTimeStr } from '@/utils/format'
import { enhanceCodeBlocks, renderMarkdown } from '@/utils/markdown'
import { splitSkillMessage } from '@/utils/skillSlash'
import { historyImageUrl } from '@/api/attachment'
import ToolChip from '@/components/ToolChip.vue'
import ChipDetail from '@/components/ChipDetail.vue'
import ChangedFiles from '@/components/ChangedFiles.vue'
import type { AgentStep, ChatMessage, HistoryAttachment } from '@/types'

const props = defineProps<{ sid: string; msg: ChatMessage }>()

const chat = useChatStore()
const preview = usePreviewStore()
const { t } = useI18n()
const { open: openLightbox } = useLightbox()

const stream = computed(() => chat.streamOf(props.sid))
/** 本条消息是否为当前活跃流的消息 */
const isLive = computed(() => chat.isProcessing(props.sid) && stream.value.msgId === props.msg.id)
/** 用户消息的技能指定改写头（命中则渲染为标签 + 正文） */
const userSkill = computed(() => (props.msg.role === 'user' ? splitSkillMessage(props.msg.content) : null))
const hasSteps = computed(() => (props.msg.steps?.length ?? 0) > 0)
const timeStr = nowTimeStr()

// ===== markdown 渲染：SSE chunk 高频更新 → 16ms 节流后重渲 =====
const contentSource = toRef(() => props.msg.content)
const throttledContent = useThrottledRef(contentSource)
const answerHtml = computed(() => renderMarkdown(throttledContent.value))

const mdContainer = ref<HTMLElement | null>(null)
function enhance() {
  if (mdContainer.value) enhanceCodeBlocks(mdContainer.value, (html) => preview.openHtml(html))
}
// 内容 + 容器 ref 一起 watch：历史消息挂载即有全文、无步骤↔有步骤分支切换时 ref 重绑，
// 仅 watch 内容会漏掉这两种首帧，pre code 不被包进 .code-wrap（渲染成无头部的裸代码块）。
// 流式期间跳过增强：裸 pre 骨架与 code-wrap 对齐（见 index.css 兕底样式），防每帧重建抖动，
// 流结束后（isLive→false）补一次高亮包装
watch([answerHtml, mdContainer], () => {
  void nextTick(() => {
    if (!isLive.value) enhance()
  })
})
watch(isLive, (live) => {
  if (!live) void nextTick(enhance)
})

// ===== 活动流分段：思考段与工具 chip 组按时间顺序交替（数据驱动等价旧 applyStepToActivity） =====
interface ThoughtSeg {
  kind: 'thought'
  key: string
  text: string
}
interface ChipItem {
  key: string
  step: AgentStep
  result: { ok: boolean; content: string } | null
}
interface ChipsSeg {
  kind: 'chips'
  key: string
  chips: ChipItem[]
}
type Seg = ThoughtSeg | ChipsSeg

const segments = computed<Seg[]>(() => {
  const steps = props.msg.steps ?? []
  const out: Seg[] = []
  let chipsSeg: ChipsSeg | null = null
  let pending: ChipItem | null = null
  steps.forEach((step, i) => {
    if (step.stepType === 'THOUGHT') {
      const text = extractThought(step)
      if (text) {
        chipsSeg = null
        out.push({ kind: 'thought', key: `t${i}`, text })
      }
    } else if (step.stepType === 'ACTION') {
      if (!chipsSeg) {
        chipsSeg = { kind: 'chips', key: `c${i}`, chips: [] }
        out.push(chipsSeg)
      }
      const item: ChipItem = { key: `s${i}`, step, result: null }
      chipsSeg.chips.push(item)
      pending = item
    } else if (step.stepType === 'OBSERVATION' || step.stepType === 'ERROR') {
      if (pending && pending.result === null) {
        pending.result = { ok: step.stepType === 'OBSERVATION', content: step.content || '' }
      }
      pending = null
    }
  })
  return out
})

const thoughtSegs = computed(() => segments.value.filter((s): s is ThoughtSeg => s.kind === 'thought'))
const lastThoughtKey = computed(() => thoughtSegs.value[thoughtSegs.value.length - 1]?.key ?? '')
const hasThought = computed(() => thoughtSegs.value.length > 0)
const chipCount = computed(() =>
  segments.value.reduce((n, s) => (s.kind === 'chips' ? n + s.chips.length : n), 0),
)
/** 摘要条角标 = 思考段数 + 工具次数 */
const summaryCount = computed(() => thoughtSegs.value.length + chipCount.value)

// ===== 终态摘要条（旧 finalizeActivity/collapseActivity） =====
const flowOpen = ref(false)
watch(isLive, (live) => {
  if (!live) flowOpen.value = false
})

/** 附件图片 src：实时消息用 dataUrl/blob 预览，历史消息走会话回显接口 */
function imageSrc(att: HistoryAttachment): string {
  return att.dataUrl || historyImageUrl(props.sid, att.name)
}

// ===== 详情展开：每 chip 独立 toggle（多开支持，对齐旧版）；展开中输出到达自动刷新（响应式） =====
const openChips = ref(new Set<string>())
function toggleChip(key: string) {
  const next = new Set(openChips.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  openChips.value = next
}

// 详情默认展开（旧版 ToolChip.open(chip)：实时与历史一致，点击 chip 可单独收起）
watch(
  () => segments.value.flatMap((s) => (s.kind === 'chips' ? s.chips.map((c) => c.key) : [])),
  (keys) => {
    if (!keys.length) return
    const next = new Set(openChips.value)
    let changed = false
    keys.forEach((k) => {
      if (!next.has(k)) {
        next.add(k)
        changed = true
      }
    })
    if (changed) openChips.value = next
  },
  { immediate: true },
)
</script>

<template>
  <!-- 用户消息：技能指定渲染为标签 + 正文，普通消息纯文本 + 附件卡片组（气泡与时间戳之间） -->
  <div v-if="msg.role === 'user'" class="msg user">
    <div>
      <div class="bubble">
        <template v-if="userSkill">
          {{ userSkill.prefix }}
          <span v-for="n in userSkill.names" :key="n" class="skill-chip"><i class="fas fa-bolt"></i>{{ n }}</span>
          {{ userSkill.rest }}
        </template>
        <template v-else>{{ msg.content }}</template>
      </div>
      <div v-if="msg.attachments?.length" class="msg-attach">
        <template v-for="att in msg.attachments" :key="att.name">
          <img
            v-if="att.type === 'IMAGE'"
            class="history-image"
            :src="imageSrc(att)"
            :alt="'🖼 ' + att.name"
            loading="lazy"
            @click="openLightbox(imageSrc(att))"
          />
          <div v-else class="attach-card doc">
            <div class="doc-body"><i class="fas" :class="[docIconMeta(att.name).icon, docIconMeta(att.name).cls]"></i></div>
            <div class="doc-foot">
              <div class="n" :title="att.name">{{ att.name }}</div>
              <div class="s">{{ docIconMeta(att.name).ext }}</div>
            </div>
          </div>
        </template>
      </div>
      <div class="time">{{ timeStr }}</div>
    </div>
  </div>

  <!-- 助手消息（无步骤）：普通气泡（流式无步骤时与 plainBubble 同构） -->
  <div v-else-if="!hasSteps" class="msg codex">
    <div>
      <div ref="mdContainer" class="bubble" v-html="answerHtml"></div>
      <div class="time">{{ timeStr }}</div>
    </div>
  </div>

  <!-- 助手消息（有步骤）：活动容器 = 摘要条 + 思考/工具流 + 思考状态条 + 回答 -->
  <div v-else class="msg codex" :data-msg-id="msg.id">
    <div class="act-col">
      <div
        v-show="!isLive && summaryCount > 0"
        class="think-toggle"
        :class="{ open: flowOpen }"
        @click="flowOpen = !flowOpen"
      >
        <i class="fas fa-chevron-right tt-chevron"></i>
        <span class="tt-text">{{ t('chat.thoughtProcess') }}</span>
      </div>

      <div class="act-flow" :class="{ collapsed: !isLive && !flowOpen }">
        <template v-for="seg in segments" :key="seg.key">
          <div
            v-if="seg.kind === 'thought'"
            class="act-thought"
            :class="{ current: isLive && seg.key === lastThoughtKey }"
          >{{ seg.text }}</div>
          <div v-else class="chips-block">
            <div class="tool-chips">
              <ToolChip
                v-for="chip in seg.chips"
                :key="chip.key"
                :step="chip.step"
                :result="chip.result"
                :live="isLive"
                :active="openChips.has(chip.key)"
                @toggle="toggleChip(chip.key)"
              />
            </div>
            <!-- 详情块挂在 chips 组下方（对齐旧 DOM 锚点插入位置）；v-show 保持挂载，输出到达自动刷新 -->
            <div
              v-for="chip in seg.chips"
              v-show="openChips.has(chip.key)"
              :key="chip.key + '-detail'"
              class="tc-detail"
            >
              <ChipDetail :step="chip.step" :result="chip.result" />
            </div>
          </div>
        </template>
      </div>

      <div v-show="isLive && hasThought" class="think-bar running">
        <i class="fas fa-circle-notch fa-spin"></i>
        <span class="tb-text">{{ t('chat.thinking') }}</span>
      </div>

      <template v-if="msg.content">
        <div ref="mdContainer" class="bubble" v-html="answerHtml"></div>
        <div class="time">{{ timeStr }}</div>
      </template>

      <!-- 消息尾部变更清单：从 steps 派生 edit/write/delete，点预览走整面板层 -->
      <ChangedFiles :steps="msg.steps ?? []" />
    </div>
  </div>
</template>