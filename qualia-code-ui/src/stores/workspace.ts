import { defineStore } from 'pinia'
import { getWorkspaceInfo } from '@/api/workspace'
import { listWorkspaceFiles } from '@/api/config'
import { pathBaseName } from '@/utils/format'
import type { WorkspaceFileInfo, WorkspaceRef } from '@/types'

/** 文件树活动徽标（步骤事件驱动：R=读 W=写 M=改） */
export type FileBadgeKind = 'R' | 'W' | 'M'

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    /** 活跃会话绑定工作区的镜像（session store 切会话/换绑时写入；null = 派生沙箱态） */
    current: null as { path: string; name: string } | null,
    recent: [] as WorkspaceRef[],
    /** 后端互斥标志：对话流进行中禁止换绑 */
    streaming: false,
    /** 根级文件列表（子目录由 TreeNode 懒加载；绑定/会话变化后清空待重载） */
    fileTree: [] as WorkspaceFileInfo[],
    /** 路径 → 徽标（本轮对话的瞬时活动标记，新一轮发送时清空） */
    fileBadges: {} as Record<string, FileBadgeKind>,

    /** 工作区面板 UI 状态（对齐旧版 steps-panel 的 collapsed/maximized/tabs） */
    panelCollapsed: false,
    panelMaximized: false,
    activeTab: 'wsChanges' as 'wsChanges' | 'wsFiles' | 'wsTerm' | 'wsPreview',
    /** 文件 Tab 点开的预览文件（聊天区以整栏层展示，null=关闭） */
    previewFile: null as { path: string } | null,
    /** 审查/文件/终端 Tab 有新内容但未激活时的提示圆点 */
    tabDots: { wsChanges: false, wsFiles: false, wsTerm: false } as Record<string, boolean>,
    /** 待定位的变更文件（文件树徽标点击 → 审查面板展开滚动定位；每次新对象保证 watch 触发） */
    pendingFileFocus: null as { path: string } | null,
    /** 绑定选择弹窗开合（chip 菜单 / 引导条等多入口共享，App 层读取渲染） */
    pickerOpen: false,
  }),

  actions: {
    /** 最近列表与流式互斥标志（供绑定弹窗使用；current 镜像不经此更新） */
    async refresh() {
      const info = await getWorkspaceInfo()
      this.recent = info.recent
      this.streaming = info.streaming
      return info
    },

    /**
     * 会话域镜像同步：活跃会话切换 / 换绑完成时由 session store 调用。
     * current 随绑定重写，会话域数据（文件树、徽标、预览）整体失效；返回绑定是否变化。
     */
    applySessionWorkspace(workspace: string | null | undefined): boolean {
      const path = workspace || null
      const changed = (this.current?.path ?? null) !== path
      this.current = path ? { path, name: pathBaseName(path) } : null
      this.fileTree = []
      this.fileBadges = {}
      this.previewFile = null
      return changed
    },

    async loadFileTree(sessionId?: string | null) {
      this.fileTree = await listWorkspaceFiles('', sessionId)
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

    switchTab(tab: 'wsChanges' | 'wsFiles' | 'wsTerm' | 'wsPreview') {
      this.activeTab = tab
      delete this.tabDots[tab]
    },

    /** 文件 Tab 树节点点击：记录预览文件（聊天区整栏展示，不联动面板状态） */
    openFilePreview(path: string) {
      this.previewFile = { path }
    },

    /** 打开/关闭绑定选择弹窗 */
    openPicker() {
      this.pickerOpen = true
    },

    closePicker() {
      this.pickerOpen = false
    },

    /** 关闭文件预览层，露出聊天内容 */
    closeFilePreview() {
      this.previewFile = null
    },

    /** 徽标点击跳转审查面板并定位文件（对齐旧版 fileTree click → ChangesPanel.openFile） */
    requestFileFocus(path: string) {
      this.pendingFileFocus = { path }
    },
  },
})
