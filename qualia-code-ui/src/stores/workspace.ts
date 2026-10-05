import { defineStore } from 'pinia'
import { getWorkspaceInfo, switchWorkspace as apiSwitch } from '@/api/workspace'
import { listWorkspaceFiles } from '@/api/config'
import type { SwitchResult, WorkspaceFileInfo, WorkspaceRef } from '@/types'

/** 文件树活动徽标（步骤事件驱动：R=读 W=写 M=改） */
export type FileBadgeKind = 'R' | 'W' | 'M'

/** 侧栏列表视图：sessions=会话历史 / files=工作区文件树 */
export type SidebarView = 'sessions' | 'files'

/** 沿用主题/语言约定持久化到 localStorage */
const SIDE_VIEW_KEY = 'codex-side-view'

function initialSideView(): SidebarView {
  return localStorage.getItem(SIDE_VIEW_KEY) === 'files' ? 'files' : 'sessions'
}

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    /** null 表示启动未绑定工作区（强制弹出选择弹窗） */
    current: null as { path: string; name: string } | null,
    recent: [] as WorkspaceRef[],
    /** 后端互斥标志：对话流进行中禁止切换 */
    streaming: false,
    /** 根级文件列表（子目录由 FileTree 组件懒加载） */
    fileTree: [] as WorkspaceFileInfo[],
    /** 路径 → 徽标（本轮对话的瞬时活动标记，新一轮发送时清空） */
    fileBadges: {} as Record<string, FileBadgeKind>,

    /** 工作区面板 UI 状态（对齐旧版 steps-panel 的 collapsed/maximized/tabs；文件 Tab 已移至侧栏） */
    panelCollapsed: false,
    panelMaximized: false,
    activeTab: 'wsChanges' as 'wsChanges' | 'wsTerm' | 'wsPreview',
    /** 侧栏列表视图：会话历史 / 文件树（顶部分段切换，持久化） */
    sidebarView: initialSideView() as SidebarView,
    /** 侧栏文件树点开的预览文件（工作区面板以整面板层展示，null=关闭） */
    previewFile: null as { path: string } | null,
    /** 终端/审查 Tab 有新内容但未激活时的提示圆点 */
    tabDots: { wsChanges: false, wsTerm: false } as Record<string, boolean>,
    /** 待定位的变更文件（文件树徽标点击 → 审查面板展开滚动定位；每次新对象保证 watch 触发） */
    pendingFileFocus: null as { path: string } | null,
  }),

  actions: {
    async refresh() {
      const info = await getWorkspaceInfo()
      this.current = info.current
      this.recent = info.recent
      this.streaming = info.streaming
      return info
    },

    /**
     * 切换工作区。NOT_FOUND（200+code）返回给调用方给「创建并打开」选项；
     * BUSY（409）抛 ApiError
     */
    async switch(path: string, create = false): Promise<SwitchResult> {
      const result = await apiSwitch(path, create)
      if (result.success) {
        this.current = result.workspace ?? { path, name: path }
        this.afterSwitch()
      }
      return result
    },

    async loadFileTree() {
      this.fileTree = await listWorkspaceFiles('')
    },

    setBadge(path: string, kind: FileBadgeKind) {
      this.fileBadges[path] = kind
    },

    /** 读操作结束清除 R 瞬时徽标 */
    removeBadge(path: string) {
      delete this.fileBadges[path]
    },

    /** 新一轮发送时清空瞬时徽标（R 移除，W 转为 M 落定；对齐旧版 clearTransientBadges） */
    clearTransientBadges() {
      const next: Record<string, FileBadgeKind> = {}
      for (const [path, kind] of Object.entries(this.fileBadges)) {
        if (kind === 'R') continue
        next[path] = 'M'
      }
      this.fileBadges = next
    },

    togglePanel() {
      this.panelCollapsed = !this.panelCollapsed
      // 关闭时立即退出放大态（对齐旧版：不播还原动画，避免与折叠动画打架）
      if (this.panelCollapsed) this.panelMaximized = false
    },

    toggleMaximize() {
      this.panelMaximized = !this.panelMaximized
    },

    switchTab(tab: 'wsChanges' | 'wsTerm' | 'wsPreview') {
      this.activeTab = tab
      delete this.tabDots[tab]
    },

    /** 侧栏顶部视图切换并持久化（即时生效，会话/文件两块互斥显示） */
    switchSidebarView(view: SidebarView) {
      this.sidebarView = view
      localStorage.setItem(SIDE_VIEW_KEY, view)
    },

    /** 侧栏文件树点击：记录预览文件（聊天区整栏展示，不联动工作区面板状态） */
    openFilePreview(path: string) {
      this.previewFile = { path }
    },

    /** 关闭文件预览层，露出聊天内容 */
    closeFilePreview() {
      this.previewFile = null
    },

    /** 徽标点击跳转审查面板并定位文件（对齐旧版 fileTree click → ChangesPanel.openFile） */
    requestFileFocus(path: string) {
      this.pendingFileFocus = { path }
    },

    /** 切换成功后的本地重置（文件树等会话域数据随工作区失效，整页重置由 App 层联动） */
    afterSwitch() {
      this.fileTree = []
      this.fileBadges = {}
      this.previewFile = null
      void this.refresh()
    },
  },
})
