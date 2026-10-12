<script setup lang="ts">
import { onMounted, ref } from 'vue'
import SidebarArea from '@/components/SidebarArea.vue'
import ChatArea from '@/components/ChatArea.vue'
import ChatTerminal from '@/components/ChatTerminal.vue'
import WorkspacePanel from '@/components/WorkspacePanel.vue'
import WorkspaceSwitcher from '@/components/WorkspaceSwitcher.vue'
import Lightbox from '@/components/Lightbox.vue'
import SettingsDialog from '@/components/SettingsDialog.vue'
import { initTheme } from '@/composables/theme'
import { initChatView, useChatView } from '@/composables/chatView'
import { useWorkspaceStore } from '@/stores/workspace'
import { useConfigStore } from '@/stores/config'
import { useSessionStore } from '@/stores/session'

/** 设置弹窗开合 */
const settingsOpen = ref(false)
/** 聊天区视图模式（气泡/终端，localStorage 持久化） */
const { view: chatView } = useChatView()

const wsStore = useWorkspaceStore()

onMounted(async () => {
  initTheme()
  initChatView()

  const config = useConfigStore()
  const sessionStore = useSessionStore()

  // 并行加载配置与会话列表（失败不阻塞主界面；工作区镜像由会话绑定派生，启动直达无强制弹窗）
  await Promise.allSettled([config.loadAll(), sessionStore.loadSessions()])

  // 旧版 loadSessions：自动选中第一个会话；列表为空落到欢迎态
  if (!sessionStore.currentSessionId) {
    const first = sessionStore.sessions[0]
    if (first) sessionStore.switchTo(first.id)
  }

  // 旧版 checkModelConfigured：模型未配置 → 自动打开设置引导
  if (!config.modelReady) settingsOpen.value = true
})
</script>

<template>
  <div class="app">
    <!-- 侧栏（品牌 + 会话库 + 设置） -->
    <SidebarArea @open-settings="settingsOpen = true" />

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
    <!-- 绑定选择弹窗：由输入区 chip / 引导条唤起，为当前会话绑定工作区（可关闭） -->
    <WorkspaceSwitcher v-if="wsStore.pickerOpen" @close="wsStore.closePicker()" />
  </div>
</template>
