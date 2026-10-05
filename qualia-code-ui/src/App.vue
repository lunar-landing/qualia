<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import SidebarArea from '@/components/SidebarArea.vue'
import ChatArea from '@/components/ChatArea.vue'
import ChatTerminal from '@/components/ChatTerminal.vue'
import WorkspacePanel from '@/components/WorkspacePanel.vue'
import WorkspaceSwitcher from '@/components/WorkspaceSwitcher.vue'
import Lightbox from '@/components/Lightbox.vue'
import SettingsDialog from '@/components/SettingsDialog.vue'
import { initTheme } from '@/composables/theme'
import { initChatView, useChatView } from '@/composables/chatView'
import { useChatStream } from '@/composables/useChatStream'
import { useWorkspaceStore } from '@/stores/workspace'
import { useChatStore } from '@/stores/chat'
import { useConfigStore } from '@/stores/config'
import { useSessionStore } from '@/stores/session'

/** 设置弹窗开合 */
const settingsOpen = ref(false)
/** 聊天区视图模式（气泡/终端，localStorage 持久化） */
const { view: chatView } = useChatView()
/** 工作区切换弹窗开合（启动未绑定工作区时强制打开） */
const wsSwitcherOpen = ref(false)

const wsStore = useWorkspaceStore()
const wsCurrent = computed(() => wsStore.current)
const { closeAll: closeAllStreams } = useChatStream()

/** 工作区切换完成后的整页状态刷新（平移旧 onWorkspaceSwitched）：
 *  会话/流状态全部清空，重拉新工作区的会话列表与文件树（终端/审查面板从消息派生，自动跟随清空） */
async function onWorkspaceSwitched() {
  const chat = useChatStore()
  const sessionStore = useSessionStore()

  closeAllStreams()
  chat.$reset()
  sessionStore.currentSessionId = null
  try {
    await sessionStore.loadSessions()
    if (!sessionStore.currentSessionId) {
      const first = sessionStore.sessions[0]
      if (first) sessionStore.switchTo(first.id)
    }
  } catch {
    /* 列表加载失败降级欢迎态 */
  }
  wsStore.loadFileTree().catch(() => undefined)
}

onMounted(async () => {
  initTheme()
  initChatView()

  const config = useConfigStore()
  const sessionStore = useSessionStore()

  // 并行加载工作区与配置（失败不阻塞主界面）
  await Promise.allSettled([wsStore.refresh(), config.loadAll()])

  // 旧版 loadSessions：自动选中第一个会话；列表为空/失败落到欢迎态
  try {
    await sessionStore.loadSessions()
    if (!sessionStore.currentSessionId) {
      const first = sessionStore.sessions[0]
      if (first) sessionStore.switchTo(first.id)
    }
  } catch {
    /* 列表加载失败降级欢迎态 */
  }

  // 启动未绑定工作区：强制弹出选择弹窗（选完整页重载，设置引导顺延到重载后的初始流程）
  if (!wsStore.current) {
    wsSwitcherOpen.value = true
    return
  }

  // 旧版 checkModelConfigured：模型未配置 → 自动打开设置引导
  if (!config.modelReady) settingsOpen.value = true
})
</script>

<template>
  <div class="app">
    <!-- 侧栏（品牌 + 工作区 + 历史 + 设置） -->
    <SidebarArea @open-settings="settingsOpen = true" @open-switcher="wsSwitcherOpen = true" />

    <!-- MAIN：内容层内聊天区与工作区面板水平排列（对齐旧版 .main-content > .steps-panel） -->
    <div class="main">
      <div class="main-content">
        <!-- 聊天区双组件并存（v-show 保活：切换不卸载，输入草稿与后台流不断） -->
        <ChatArea v-show="chatView !== 'terminal'" />
        <ChatTerminal v-show="chatView === 'terminal'" />
        <WorkspacePanel />
      </div>
    </div>

    <!-- 图片灯箱：点击消息内图片全屏预览 -->
    <Lightbox />
    <!-- 设置弹窗：v-if 控制挂载，每次打开重新拉取 -->
    <SettingsDialog v-if="settingsOpen" @close="settingsOpen = false" />
    <!-- 工作区切换弹窗：forced 表示启动未绑定工作区（不可关闭，选定后整页重载） -->
    <WorkspaceSwitcher
      v-if="wsSwitcherOpen"
      :forced="!wsCurrent"
      @close="wsSwitcherOpen = false"
      @switched="onWorkspaceSwitched"
    />
  </div>
</template>
