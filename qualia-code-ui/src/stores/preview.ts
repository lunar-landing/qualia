import { defineStore } from 'pinia'

/**
 * 浏览器 Tab 预览状态（旧 window.openHtmlPreview/openUrlPreview 的 store 化）。
 * WorkspacePanel 监听本 store 展开面板并切换到浏览器 Tab
 */
export const usePreviewStore = defineStore('preview', {
  state: () => ({
    /** none=空态；html=srcdoc 自建页；url=外部网页 */
    mode: 'none' as 'none' | 'html' | 'url',
    html: '',
    url: '',
    /** 自建页标题（技能详情/搜索结果等），用于浏览器 Tab 徽标展示 */
    title: '',
    /** 文件语义路径：html 模式下非空 = 来自工作区文件（地址栏面包屑 + 解锁「在文件夹中打开」等文件动作） */
    path: '',
    /** 打开次数计数：mode 不变时重复 open 也能触发面板展开/切 Tab 联动 */
    seq: 0,
  }),
  actions: {
    /** path 非空时为工作区文件预览（BrowserPane 地址栏显示面包屑并解锁文件动作） */
    openHtml(html: string, title = '', path = '') {
      this.mode = 'html'
      this.html = html
      this.title = title
      this.path = path
      this.seq++
    },
    openUrl(url: string) {
      this.mode = 'url'
      this.url = url
      this.title = ''
      this.path = ''
      this.seq++
    },
    close() {
      this.mode = 'none'
      this.html = ''
      this.url = ''
      this.title = ''
      this.path = ''
    },
  },
})
