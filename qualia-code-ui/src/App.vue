<script setup lang="ts">
import { onMounted, ref } from 'vue'
import SidebarArea from '@/components/SidebarArea.vue'
import ChatArea from '@/components/ChatArea.vue'
import ChatTerminal from '@/components/ChatTerminal.vue'
import WorkspacePanel from '@/components/WorkspacePanel.vue'
import MaterialsView from '@/components/materials/MaterialsView.vue'
import SchedulesView from '@/components/schedules/SchedulesView.vue'
import ScheduleDialog from '@/components/schedules/ScheduleDialog.vue'
import SkillsView from '@/components/skills/SkillsView.vue'
import WorkspaceSwitcher from '@/components/WorkspaceSwitcher.vue'
import Lightbox from '@/components/Lightbox.vue'
import SettingsDialog from '@/components/SettingsDialog.vue'
import { initTheme } from '@/composables/theme'
import { initChatView, useChatView } from '@/composables/chatView'
import { useWorkspaceStore } from '@/stores/workspace'
import { useConfigStore } from '@/stores/config'
import { useSessionStore } from '@/stores/session'
import { useSchedulesStore } from '@/stores/schedules'

/** 设置弹窗开合 */
const settingsOpen = ref(false)
/** 聊天区视图模式（气泡/终端，localStorage 持久化） */
const { view: chatView } = useChatView()

const wsStore = useWorkspaceStore()
const schedStore = useSchedulesStore()

onMounted(async () => {
  initTheme()
  initChatView()

  const config = useConfigStore()
  const sessionStore = useSessionStore()

  // 并行加载配置与会话列表与任务列表（失败不阻塞主界面；任务列表驱动会话列表时钟标识与 30s 轮询）
  await Promise.allSettled([config.loadAll(), sessionStore.loadSessions(), schedStore.load()])

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
        <!-- 资料整栏视图：覆盖聊天区 + 工作区面板（内部随 materials store 开合） -->
        <MaterialsView />
        <!-- 定时任务中心：同款整栏覆盖视图（内部随 schedules store 开合） -->
        <SchedulesView />
        <!-- 技能管理中心：同款整栏覆盖视图（内部随 skills store 开合） -->
        <SkillsView />
      </div>
    </div>

    <!-- 图片灯箱：点击消息内图片全屏预览 -->
    <Lightbox />
    <!-- 设置弹窗：v-if 控制挂载，每次打开重新拉取 -->
    <SettingsDialog v-if="settingsOpen" @close="settingsOpen = false" />
    <!-- 绑定选择弹窗：由输入区 chip / 引导条唤起，为当前会话绑定工作区（可关闭） -->
    <WorkspaceSwitcher v-if="wsStore.pickerOpen" @close="wsStore.closePicker()" />
    <!-- 定时任务新建/修改弹窗：任务中心与输入区管理浮层共用，由 schedules store 开合 -->
    <ScheduleDialog v-if="schedStore.dialog" @close="schedStore.closeDialog()" />
  </div>
</template>
