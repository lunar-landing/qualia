<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useConfigStore } from '@/stores/config'
import { useChatStream, tryConsumeQueue } from '@/composables/useChatStream'
import { uploadAttachment } from '@/api/attachment'
import { rewriteSkillMessage, joinSkillNames } from '@/utils/skillSlash'
import { docIconMeta, formatBytes } from '@/utils/format'
import ModelSelector from '@/components/ModelSelector.vue'
import ModeSelector from '@/components/ModeSelector.vue'
import QueuedPanel from '@/components/QueuedPanel.vue'
import type { PendingAttachment, SkillInfo } from '@/types'

const MAX_ATTACHMENTS = 4
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const MAX_DOC_BYTES = 20 * 1024 * 1024

const { t, locale } = useI18n()

const sessionStore = useSessionStore()
const chat = useChatStore()
const { send, stop } = useChatStream()

const sid = computed(() => sessionStore.currentSessionId)
const processing = computed(() => (sid.value ? chat.isProcessing(sid.value) : false))
const placeholder = computed(() =>
  processing.value
    ? t('input.queuePlaceholder')
    : configStore.readOnly
      ? t('input.placeholderAsk')
      : t('input.placeholder'),
)

const input = ref('')
const textareaEl = ref<HTMLTextAreaElement | null>(null)
const fileEl = ref<HTMLInputElement | null>(null)
/** 上传回执（发送只带 ID，对齐旧 pendingAttachments） */
const pending = ref<PendingAttachment[]>([])

/** 对齐旧 autoResize：空值回基准高（单行 52px），非空压回基准再按 scrollHeight 伸缩（52~200） */
function autoResize() {
  const el = textareaEl.value
  if (!el) return
  if (!el.value) {
    el.style.height = '52px'
    return
  }
  el.style.height = '52px'
  el.style.height = `${Math.max(52, Math.min(el.scrollHeight, 200))}px`
}

const configStore = useConfigStore()

/** 已选技能（菜单采纳后以 chip 列表展示，可多选；发送时组装改写指令） */
const selectedSkills = ref<SkillInfo[]>([])

/** /技能 菜单：输入整体为单个 /token 时弹出（采纳后清空输入转 chip；Esc 仅关闭当前 token） */
const slashDismissed = ref(false)
const slashIdx = ref(0)
const slashOpen = computed(() => /^\/[^\s]*$/.test(input.value) && !slashDismissed.value)
const slashQuery = computed(() => (slashOpen.value ? input.value.slice(1).toLowerCase() : ''))
const slashHits = computed(() => {
  const q = slashQuery.value
  return configStore.skills
    .filter((s) => `${s.name} ${s.dir ?? ''} ${s.description}`.toLowerCase().includes(q))
    .slice(0, 8)
})
// 每次候选集变化重置：重新键入即解除 Esc 关闭，高亮回首个
watch(slashHits, () => {
  slashDismissed.value = false
  slashIdx.value = 0
})

function acceptSlash(s: SkillInfo) {
  // 追加去重：同一技能不重复入列表
  const key = s.dir || s.name
  if (!selectedSkills.value.some((x) => (x.dir || x.name) === key)) {
    selectedSkills.value.push(s)
  }
  input.value = ''
  autoResize()
  textareaEl.value?.focus()
}

function onKeydown(e: KeyboardEvent) {
  // /技能 菜单优先接管导航键（输入法选词中的回车不接管）
  if (slashOpen.value && !e.isComposing && e.keyCode !== 229) {
    const n = slashHits.value.length
    if (e.key === 'ArrowDown' && n) {
      e.preventDefault()
      slashIdx.value = (slashIdx.value + 1) % n
      return
    }
    if (e.key === 'ArrowUp' && n) {
      e.preventDefault()
      slashIdx.value = (slashIdx.value - 1 + n) % n
      return
    }
    if ((e.key === 'Enter' || e.key === 'Tab') && n) {
      e.preventDefault()
      const hit = slashHits.value[slashIdx.value]
      if (hit) acceptSlash(hit)
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      slashDismissed.value = true
      return
    }
  }
  // 回车发送（Shift+Enter 换行）；输入法选词中的回车（isComposing / keyCode 229）不触发
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && e.keyCode !== 229) {
    e.preventDefault()
    void doSend()
  }
}

function doSend() {
  const raw = input.value.trim()
  const attachments = pending.value.slice()
  if (!raw && !selectedSkills.value.length && attachments.length === 0) return
  // 已选技能 → 组装改写指令（发送/回显/历史的唯一事实源）；手敲 /token 由 send 内兑底解析
  let text = raw
  if (selectedSkills.value.length) {
    const names = selectedSkills.value.map((s) => s.name)
    const label =
      names.length > 1
        ? joinSkillNames(names, locale.value.startsWith('zh'))
        : (names[0] ?? '')
    const tpl =
      names.length > 1
        ? t('input.skillRewriteMulti', { name: label })
        : t('input.skillRewrite', { name: label })
    text = rewriteSkillMessage(raw, tpl)
  }
  // 流进行中 → 入队待发送队列（回复完成后自动接发）；上限满则提示
  if (processing.value) {
    if (sid.value && chat.enqueue(sid.value, text, attachments)) {
      releaseObjectUrls(attachments)
      clearDraft()
    } else alert(t('input.queueFull'))
    return
  }
  // 空闲但队列非空（刷新恢复/编辑暂停挂起）→ 同样入队后立即消费，保证 FIFO 顺序
  if (sid.value && chat.queueOf(sid.value).length) {
    if (chat.enqueue(sid.value, text, attachments)) {
      releaseObjectUrls(attachments)
      clearDraft()
      tryConsumeQueue(sid.value)
    } else {
      alert(t('input.queueFull'))
    }
    return
  }
  clearDraft()
  void send(text, attachments).then(() => textareaEl.value?.focus())
}

/** 发送/入队后统一清空草稿（对齐旧版：视觉即时反馈，流状态由 useChatStream 管理） */
function clearDraft() {
  input.value = ''
  selectedSkills.value = []
  pending.value = []
  autoResize()
}

/** 入队后释放 blob 预览：队列条目只留上传回执元数据，objectUrl 不再被引用（发送走服务端附件） */
function releaseObjectUrls(list: PendingAttachment[]) {
  for (const a of list) if (a.objectUrl) URL.revokeObjectURL(a.objectUrl)
}

/** 上传换回执，失败逐个 alert（对齐旧 addAttachmentFiles 的限额与降级） */
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

/** 支持直接粘贴截图 */
function onPaste(e: ClipboardEvent) {
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

/** 文件选择：处理后重置 input 以便重复选同一文件 */
function onFileChange() {
  if (fileEl.value?.files?.length) void addFiles(fileEl.value.files)
  if (fileEl.value) fileEl.value.value = ''
}
</script>

<template>
  <div class="input-hold">
    <!-- 附件预览条：图片缩略 / 文档卡片同规格（CSS 默认 display:none，有附件时显式 flex） -->
    <div class="image-preview" :style="{ display: pending.length ? 'flex' : 'none' }">
      <div
        v-for="(att, idx) in pending"
        :key="att.id"
        class="preview-item"
        :class="{ doc: att.type !== 'image' }"
      >
        <img v-if="att.type === 'image'" :src="att.objectUrl" alt="" />
        <template v-else>
          <div class="doc-body">
            <i class="fas" :class="[docIconMeta(att.name).icon, docIconMeta(att.name).cls]"></i>
          </div>
          <div class="doc-foot">
            <div class="n" :title="att.name">{{ att.name }}</div>
            <div class="s">{{ docIconMeta(att.name).ext }} · {{ formatBytes(att.size) }}</div>
          </div>
        </template>
        <button class="preview-remove" :title="t('input.remove')" @click="removeAttachment(idx)">
          <i class="fas fa-times"></i>
        </button>
      </div>
    </div>

    <div class="input-wrapper">
      <!-- 待发送队列：卡片内部首段，与输入区同背景/边框/阴影/hover（v3 极简稿） -->
      <QueuedPanel variant="bubble" />
      <!-- 已选技能 chips：菜单可多选累积，独立于正文展示，可单独移除；发送时组装为改写指令 -->
      <div v-if="selectedSkills.length" class="skill-chips">
        <div v-for="(s, i) in selectedSkills" :key="s.dir || s.name" class="skill-chip selected">
          <i class="fas fa-bolt"></i>
          <span>{{ s.name }}</span>
          <button type="button" class="chip-x" :title="t('input.remove')" @click="selectedSkills.splice(i, 1)">
            <i class="fas fa-times"></i>
          </button>
        </div>
      </div>
      <textarea
        ref="textareaEl"
        v-model="input"
        rows="1"
        :placeholder="placeholder"
        @input="autoResize"
        @keydown="onKeydown"
        @paste="onPaste"
      ></textarea>
      <!-- /技能 菜单：输入为单个 /token 时向上弹出（浮层规格对齐 model-dropdown） -->
      <div v-if="slashOpen" class="slash-menu">
        <template v-if="slashHits.length">
          <button
            v-for="(s, i) in slashHits"
            :key="s.dir || s.name"
            type="button"
            class="slash-item"
            :class="{ active: i === slashIdx }"
            @mousedown.prevent
            @click="acceptSlash(s)"
            @mousemove="slashIdx = i"
          >
            <i class="fas fa-bolt"></i>
            <span class="si-name">/{{ s.dir || s.name }}</span>
            <span class="si-desc">{{ s.description }}</span>
          </button>
        </template>
        <div v-else class="slash-empty">{{ t('input.slashNoMatch') }}</div>
        <div class="slash-foot">{{ t('input.slashHint') }}</div>
      </div>
      <div class="input-tools">
        <button
          class="tool-btn"
          :title="t('input.attachTitle')"
          @click="fileEl?.click()"
        >
          <i class="fas fa-paperclip"></i>
        </button>
        <ModelSelector />
        <ModeSelector />
        <!-- 生成期间切换为红色停止态（仅断开前端流） -->
        <button
          class="send-btn"
          :class="{ stop: processing }"
          :title="processing ? t('input.stop') : t('input.send')"
          @click="processing ? stop() : doSend()"
        >
          <i class="fas" :class="processing ? 'fa-stop' : 'fa-arrow-up'"></i>
        </button>
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
  </div>
</template>
