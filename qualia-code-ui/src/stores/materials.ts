import { defineStore } from 'pinia'
import {
  listMaterials,
  createMaterial,
  updateMaterial,
  deleteMaterial,
  getMaterialTree,
  createMaterialFolder,
  uploadMaterialFiles,
  getMaterialArticle,
  createMaterialArticle,
  saveMaterialArticle,
  renameMaterialNode,
  deleteMaterialNode,
  type MaterialInfo,
  type MaterialNode,
  type MaterialArticle,
  type MaterialUploadResult,
} from '@/api/materials'
import { i18n } from '@/i18n'

/**
 * 资料视图状态：双页结构（列表页 ⇄ 详情页）+ 详情页三态（目录 / 阅读 / 编辑）。
 * open 控制整栏覆盖层显隐；上传为同步转换，uploading 仅作树内短暂过渡展示。
 */
export const useMaterialsStore = defineStore('materials', {
  state: () => ({
    /** 整栏视图开关（侧栏「资料」入口） */
    open: false,
    /** 列表页数据 */
    list: [] as MaterialInfo[],
    listLoading: false,

    /** 当前进入的库（null = 列表页） */
    currentId: null as string | null,
    tree: [] as MaterialNode[],
    treeLoading: false,

    /** 详情页阅读区三态 */
    readerMode: 'home' as 'home' | 'article' | 'edit',
    article: null as MaterialArticle | null,
    articleLoading: false,
    /** 树选中路径（文件夹或文章） */
    selectedPath: null as string | null,

    /** 上传中的文件名（树内短暂转圈行，同步转换秒级完成） */
    uploading: [] as string[],
    /** 右下角汇总 toast（上传完成 / 失败） */
    toast: null as { text: string; sub?: string; fail?: boolean } | null,

    /** 折叠的文件夹路径集合（会话内记忆，enterKb 随库重置；过滤态强制展开不受其影响） */
    collapsedPaths: new Set<string>(),
  }),

  getters: {
    current: (s): MaterialInfo | null => s.list.find((k) => k.id === s.currentId) ?? null,
  },

  actions: {
    // ===== 视图开关 =====
    toggleView() {
      if (this.open) this.closeView()
      else void this.openView()
    },
    async openView() {
      this.open = true
      if (!this.currentId) await this.loadList()
    },
    closeView() {
      this.open = false
    },

    // ===== 列表页 =====
    async loadList() {
      this.listLoading = true
      try {
        this.list = await listMaterials()
      } finally {
        this.listLoading = false
      }
    },

    async createKb(name: string, description: string) {
      const info = await createMaterial(name, description)
      await this.loadList()
      await this.enterKb(info.id)
    },

    async renameKb(id: string, name: string) {
      // 重命名入口仅在列表页，刷新列表即可
      await updateMaterial(id, { name })
      await this.loadList()
    },

    async deleteKb(id: string) {
      await deleteMaterial(id)
      if (this.currentId === id) this.backToList()
      else await this.loadList()
    },

    // ===== 详情页（双页之页 B） =====
    async enterKb(id: string) {
      this.currentId = id
      this.readerMode = 'home'
      this.article = null
      this.selectedPath = null
      this.collapsedPaths.clear()
      await this.refreshTree()
    },

    /** 文件夹折叠状态翻转（会话内记忆，切换库重置） */
    toggleCollapsed(path: string) {
      if (this.collapsedPaths.has(path)) this.collapsedPaths.delete(path)
      else this.collapsedPaths.add(path)
    },

    backToList() {
      this.currentId = null
      this.tree = []
      this.article = null
      this.readerMode = 'home'
      this.selectedPath = null
      void this.loadList()
    },

    async refreshTree() {
      if (!this.currentId) return
      this.treeLoading = true
      try {
        const data = await getMaterialTree(this.currentId)
        this.tree = data.children
      } finally {
        this.treeLoading = false
      }
    },

    // ===== 阅读区三态 =====
    openHome() {
      this.readerMode = 'home'
      this.article = null
      this.selectedPath = null
    },

    async openArticle(path: string) {
      if (!this.currentId) return
      this.articleLoading = true
      this.selectedPath = path
      try {
        this.article = await getMaterialArticle(this.currentId, path)
        this.readerMode = 'article'
      } finally {
        this.articleLoading = false
      }
    },

    startEdit() {
      if (this.article) this.readerMode = 'edit'
    },

    cancelEdit() {
      this.readerMode = this.article ? 'article' : 'home'
    },

    async saveEdit(content: string) {
      if (!this.currentId || !this.article) return
      await saveMaterialArticle(this.currentId, this.article.path, content)
      await this.openArticle(this.article.path)
      await this.refreshTree()
    },

    // ===== 文章与文件夹 =====
    async createArticle(name: string, parentPath?: string) {
      if (!this.currentId) return
      const { path } = await createMaterialArticle(this.currentId, name, parentPath)
      await this.refreshTree()
      await this.openArticle(path)
      this.startEdit()
    },

    async createFolder(name: string, parentPath?: string) {
      if (!this.currentId) return
      const { path } = await createMaterialFolder(this.currentId, name, parentPath)
      await this.refreshTree()
      this.selectedPath = path
    },

    async renameNode(path: string, name: string) {
      if (!this.currentId) return
      await renameMaterialNode(this.currentId, path, name)
      await this.refreshTree()
      // 重命名当前打开的文章后跟随新路径
      if (this.article?.path === path) await this.openArticle(renamedPath(path, name))
    },

    async deleteNode(path: string) {
      if (!this.currentId) return
      await deleteMaterialNode(this.currentId, path)
      await this.refreshTree()
      // 删除的是打开中文章（或其祖先目录）→ 回目录页
      if (this.article?.path === path || this.article?.path.startsWith(path)) this.openHome()
    },

    // ===== 上传（同步转换） =====
    async uploadFiles(files: File[], parentPath?: string) {
      if (!this.currentId || files.length === 0) return
      const names = files.map((f) => f.name)
      this.uploading.push(...names)
      try {
        const { results } = await uploadMaterialFiles(this.currentId, files, parentPath)
        await this.refreshTree()
        this.notifyUpload(results)
      } catch (e) {
        this.showToast(e instanceof Error ? e.message : String(e), undefined, true)
      } finally {
        this.uploading = this.uploading.filter((n) => !names.includes(n))
      }
    },

    notifyUpload(results: MaterialUploadResult[]) {
      const ok = results.filter((r) => r.ok)
      const failed = results.filter((r) => !r.ok)
      if (ok.length > 0) {
        this.showToast(i18n.global.t('materials.toastImported', { n: ok.length }), ok.map((r) => r.title).join(' · '))
      }
      if (failed.length > 0) {
        this.showToast(
          i18n.global.t('materials.toastFailed', { n: failed.length }),
          failed.map((r) => `${r.name}: ${r.error}`).join(' · '),
          true,
        )
      }
    },

    showToast(text: string, sub?: string, fail = false) {
      this.toast = { text, sub, fail }
      window.setTimeout(() => {
        if (this.toast?.text === text) this.toast = null
      }, 4200)
    },
  },
})

/** 重命名后的新路径：替换末段文件名为新名（保留 .md 后缀） */
function renamedPath(path: string, newName: string): string {
  const idx = path.lastIndexOf('/')
  const parent = idx >= 0 ? path.slice(0, idx + 1) : ''
  return parent + newName + '.md'
}
