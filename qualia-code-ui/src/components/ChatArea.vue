<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { emptyStreamState, useChatStore } from '@/stores/chat'
import type { StreamState } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { useThrottledRef } from '@/composables/useThrottledRef'
import MessageItem from '@/components/MessageItem.vue'
import WelcomeHero from '@/components/WelcomeHero.vue'
import InputArea from '@/components/InputArea.vue'
import FilePreview from '@/components/FilePreview.vue'

const EMPTY_STREAM: StreamState = emptyStreamState()

const { t } = useI18n()
const chat = useChatStore()
const sessionStore = useSessionStore()
const ws = useWorkspaceStore()

const sid = computed(() => sessionStore.currentSessionId)
const messages = computed(() => (sid.value ? chat.messagesOf(sid.value) : []))
const stream = computed<StreamState>(() => (sid.value ? chat.streamOf(sid.value) : EMPTY_STREAM))

/** typing indicator 窗口 = 发送后到首个 step/answer 前（对齐旧版时序） */
const typingVisible = computed(() => stream.value.status === 'connecting')
/** 空会话（或未选会话）显示欢迎 Hero，输入框随 Teleport 移入 */
const welcome = computed(() => messages.value.length === 0)

// ===== 滚动跟随：消息数/流内容变化时跟随到底，用户上滚（距底 >80px）即暂停 =====
const messagesEl = ref<HTMLElement | null>(null)
const stick = ref(true)

function onScroll() {
  const el = messagesEl.value
  if (!el) return
  stick.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function scrollToBottom() {
  const el = messagesEl.value
  if (el) el.scrollTop = el.scrollHeight
}

// 流式内容签名（16ms 节流）：消息数 + 步骤数 + 末条内容长度
const contentSig = useThrottledRef(
  computed(() => {
    const last = messages.value[messages.value.length - 1]
    return `${messages.value.length}|${stream.value.steps.length}|${last?.content.length ?? 0}`
  }),
)

watch([() => messages.value.length, contentSig], () => {
  if (stick.value) void nextTick(scrollToBottom)
})

// 切会话：懒加载历史 + 重置跟随并滚底（活跃流消息常驻 store，无需 restoreLiveMessage）
watch(
  sid,
  (s) => {
    ws.closeFilePreview() // 切会话收起文件预览层，露出新会话内容
    stick.value = true
    if (s) void chat.ensureHistory(s)
    void nextTick(scrollToBottom)
  },
  { immediate: true },
)

// ===== 欢迎态输入框移入 Hero（对齐旧 showWelcome 的 DOM 移动：Teleport 动态挂载点） =====
const heroAnchor = ref<HTMLElement | null>(null)
watch(
  welcome,
  async () => {
    await nextTick()
    heroAnchor.value =
      welcome.value && messagesEl.value
        ? (messagesEl.value.querySelector('.hero-input-anchor') as HTMLElement | null)
        : null
  },
  { immediate: true },
)
</script>

<template>
  <section class="chat-area">
    <!-- 聊天区右上角悬浮操作舱：工作区面板开关（仅面板收起时展示，收起入口由面板自身头部承担） -->
    <div v-if="ws.panelCollapsed" class="chat-float-actions">
      <button class="chat-float-btn" :title="t('chat.workspacePanel')" @click="ws.togglePanel()">
        <i class="fas fa-layer-group"></i>
      </button>
    </div>

    <!-- ===== MESSAGES ===== -->
    <div ref="messagesEl" class="messages" @scroll="onScroll">
      <WelcomeHero v-if="welcome" />
      <MessageItem v-for="m in messages" :key="m.id" :sid="sid ?? ''" :msg="m" />
      <div v-show="typingVisible" class="typing-indicator">
        <div class="typing-dots"><span></span><span></span><span></span></div>
      </div>
    </div>

    <!-- 输入区：欢迎态 Teleport 进 Hero，常态就地渲染于聊天区底部 -->
    <Teleport :to="heroAnchor ?? 'body'" :disabled="!heroAnchor">
      <div class="input-area">
        <InputArea />
      </div>
    </Teleport>

    <!-- 文件预览层：侧栏文件树点击后在聊天区整栏展示（自带头部与关闭，盖过消息与输入区） -->
    <div v-if="ws.previewFile" class="chat-file-preview">
      <FilePreview :path="ws.previewFile.path" @close="ws.closeFilePreview()" />
    </div>
  </section>
</template>
