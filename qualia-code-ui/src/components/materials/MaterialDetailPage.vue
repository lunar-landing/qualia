<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMaterialsStore } from '@/stores/materials'
import { renderMarkdown } from '@/utils/markdown'
import { formatWordCount, relTime } from '@/utils/format'
import { materialOriginalUrl, type MaterialNode, type MaterialKind } from '@/api/materials'
import MaterialTreeNode from './MaterialTreeNode.vue'
import MaterialEditor from './MaterialEditor.vue'
import MaterialOriginalViewer from './MaterialOriginalViewer.vue'

/**
 * 资料详情页（页面 B）：两栏 —— 文章树 + 预览阅读区。
 * 操作单一入口：上传 = 树底 CTA（拖拽为等价手势）；新建文件夹/文章 = 树头（共用 .dlg 轻弹窗）；
 * 删除/重命名 = 树行 hover（重命名同走轻弹窗，删除走 confirm）。阅读区头部只放阅读上下文操作（返回 / 面包屑 / 原件 / 编辑）。
 * 阅读区三态：目录首页 / 整篇阅读（md 渲染 + 文内 TOC + .md 相对链接跳转）/ 源码编辑。
 * 排版走资料库专属规格（--mat-* token）：正文列 672px + 目录列 180px 整体居中，
 * 阅读态 14.5px/1.9 长文行高，层级靠字号字重与段间距而非装饰；区域靠底色分层而非边框。
 */
const { t } = useI18n()
const materials = useMaterialsStore()

// ===== 面包屑（库名 / 目录段… / 标题；回列表由头部返回按钮承担，不重复设入口） =====
interface Crumb {
  text: string
  kind: 'home' | 'folder' | 'current'
  path: string
}

const crumbs = computed<Crumb[]>(() => {
  const list: Crumb[] = []
  if (materials.current) list.push({ text: materials.current.name, kind: 'home', path: '' })
  if (materials.readerMode !== 'home' && materials.article) {
    const segs = materials.article.path.split('/')
    segs.forEach((seg, i) => {
      if (i < segs.length - 1) {
        list.push({ text: seg, kind: 'folder', path: segs.slice(0, i + 1).join('/') + '/' })
      } else {
        list.push({ text: materials.article!.title, kind: 'current', path: '' })
      }
    })
  } else {
    list.push({ text: t('materials.home'), kind: 'current', path: '' })
  }
  return list
})

function onCrumb(crumb: Crumb) {
  if (crumb.kind === 'home') materials.openHome()
  else if (crumb.kind === 'folder') materials.selectedPath = crumb.path
}

// ===== 上传（唯一入口：树底 CTA；拖拽为等价手势） =====
const fileInput = ref<HTMLInputElement | null>(null)
const dragDepth = ref(0)

/** 上传落点：选中文件夹 → 其内；选中文章 → 同级目录；否则库根 */
function currentUploadDir(): string {
  const sel = materials.selectedPath
  if (!sel) return ''
  if (sel.endsWith('/')) return sel
  const idx = sel.lastIndexOf('/')
  return idx >= 0 ? sel.slice(0, idx + 1) : ''
}

function pickFiles() {
  fileInput.value?.click()
}

function onPickFiles(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length) void materials.uploadFiles(files, currentUploadDir())
}

function onDrop(e: DragEvent) {
  dragDepth.value = 0
  const files = Array.from(e.dataTransfer?.files ?? [])
  if (files.length) void materials.uploadFiles(files, currentUploadDir())
}

// ===== 树过滤（纯前端本地：文章/路径命中 + 祖先链；文件夹名命中整组保留；过滤态强制展开由树节点处理） =====
const filterKw = ref('')
const filtering = computed(() => filterKw.value.trim().length > 0)
const filteredTree = computed<MaterialNode[]>(() => {
  const kw = filterKw.value.trim().toLowerCase()
  if (!kw) return materials.tree
  const hit = (n: MaterialNode) => n.name.toLowerCase().includes(kw)
  const walk = (list: MaterialNode[]): MaterialNode[] => {
    const out: MaterialNode[] = []
    for (const n of list) {
      if (n.type === 'file') {
        if (hit(n)) out.push(n)
        continue
      }
      if (hit(n)) {
        out.push(n)
        continue
      }
      const kids = walk(n.children ?? [])
      if (kids.length) out.push({ ...n, children: kids })
    }
    return out
  }
  return walk(materials.tree)
})

// 切库重置过滤词（折叠集合由 store.enterKb 清）
watch(
  () => materials.currentId,
  () => {
    filterKw.value = ''
  },
)

// ===== 树滚动跟随：选中/过滤变化后，选中行滚入可视区（深层文章/过滤定位均可达） =====
const treeEl = ref<HTMLElement | null>(null)
watch([() => materials.selectedPath, filterKw], () => {
  void nextTick(() => treeEl.value?.querySelector('.tree-row.on')?.scrollIntoView({ block: 'nearest' }))
})

// ===== 行右键菜单（行操作唯一入口：打开 / 重命名 / 删除；Teleport 到 body 防裁剪） =====
const ctxMenu = ref<{ x: number; y: number; node: MaterialNode } | null>(null)
const menuEl = ref<HTMLElement | null>(null)

function onCtxEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') closeCtxMenu()
}

function closeCtxMenu() {
  ctxMenu.value = null
  window.removeEventListener('keydown', onCtxEsc)
}

function onRowMenu(node: MaterialNode, e: MouseEvent) {
  closeCtxMenu()
  ctxMenu.value = { x: e.clientX, y: e.clientY, node }
  // 下一帧按菜单实测尺寸裁回视口（右/下缘翻转）
  void nextTick(() => {
    const el = menuEl.value
    if (!el || !ctxMenu.value) return
    const r = el.getBoundingClientRect()
    ctxMenu.value.x = Math.max(4, Math.min(ctxMenu.value.x, window.innerWidth - r.width - 8))
    ctxMenu.value.y = Math.max(4, Math.min(ctxMenu.value.y, window.innerHeight - r.height - 8))
  })
  // 本轮事件派发完再挂全局关闭（避免立即自我关闭）；capture 先于行 handler，右键另一行时先关旧后开新
  setTimeout(() => {
    window.addEventListener('click', closeCtxMenu, { once: true })
    window.addEventListener('contextmenu', closeCtxMenu, { once: true, capture: true })
    window.addEventListener('keydown', onCtxEsc)
  }, 0)
}

function ctxAction(action: 'open' | 'rename' | 'delete') {
  const node = ctxMenu.value?.node
  closeCtxMenu()
  if (!node) return
  if (action === 'open') void materials.openArticle(node.path)
  else if (action === 'rename') onRenameNode(node)
  else onDeleteNode(node)
}

// ===== 目录首页（树数据按分组渲染） =====
interface HomeGroup {
  name: string
  items: MaterialNode[]
}

const homeGroups = computed<HomeGroup[]>(() => {
  const groups: HomeGroup[] = []
  const rootFiles: MaterialNode[] = []
  for (const node of materials.tree) {
    if (node.type === 'folder') {
      groups.push({ name: node.name, items: node.children ?? [] })
    } else {
      rootFiles.push(node)
    }
  }
  // 根级文章置顶（高频入口无组头直接铺开），文件夹组在后
  if (rootFiles.length > 0) groups.unshift({ name: '', items: rootFiles })
  return groups
})

// ===== 整篇阅读 =====
const articleHtml = computed(() => (materials.article ? renderMarkdown(materials.article.content) : ''))
const readerBodyEl = ref<HTMLElement | null>(null)

// 阅读子视图：kind 决定可选视图与默认项（富文档看原件，文本类看预览）
const kind = computed<MaterialKind>(() => materials.article?.kind ?? 'markdown')
const view = ref<'preview' | 'source' | 'original' | 'text'>('preview')
const isRichDoc = computed(() => kind.value === 'pdf' || kind.value === 'docx')
// 版式预览（pdf/docx 原件 · html 渲染页）占满阅读区：去正文留白与外滚，交内层自滚
const fullbleed = computed(
  () =>
    materials.readerMode === 'article' &&
    (view.value === 'original' || (kind.value === 'html' && view.value === 'preview')),
)
const originalUrl = computed(() =>
  materials.current && materials.article?.source
    ? materialOriginalUrl(materials.current.id, materials.article.source)
    : '',
)
// 切换文章时按 kind 重置默认视图
watch(
  () => materials.article?.path,
  () => {
    view.value = isRichDoc.value ? 'original' : 'preview'
  },
)

/** 图标按 kind 区分字形与颜色（树已同规则，此处供首页卡片与拖拽上传行复用；.icon-* 与聊天附件共用词汇） */
function iconOf(k: MaterialKind | null): string {
  switch (k) {
    case 'pdf':
      return 'fa-file-pdf icon-pdf'
    case 'docx':
      return 'fa-file-word icon-word'
    case 'html':
      return 'fa-file-code icon-html'
    default:
      return 'fa-file-lines icon-md'
  }
}
function uploadIcon(name: string): string {
  const n = name.toLowerCase()
  if (n.endsWith('.pdf')) return 'fa-file-pdf icon-pdf'
  if (n.endsWith('.docx')) return 'fa-file-word icon-word'
  if (n.endsWith('.html') || n.endsWith('.htm')) return 'fa-file-code icon-html'
  return 'fa-file-lines icon-md'
}

/** 文内目录：h2/h3 顺序提取（渲染顺序一致，按下标定位滚动） */
const toc = computed(() => {
  const items: { level: number; text: string }[] = []
  if (materials.article) {
    for (const m of materials.article.content.matchAll(/^(#{2,3})\s+(.+)$/gm)) {
      items.push({ level: m[1]?.length ?? 2, text: m[2]?.trim() ?? '' })
    }
  }
  return items
})

function jumpToc(index: number) {
  const heads = readerBodyEl.value?.querySelectorAll('.art-body .md h2, .art-body .md h3')
  heads?.[index]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 元信息行：作者缩写（CJK 取前两字，拉丁取首字母）与阅读时长按 400 字/分估算 */
const authorShort = computed(() => {
  const a = (materials.article?.author ?? '').trim()
  if (!a) return ''
  return /[\u4e00-\u9fa5]/.test(a) ? a.slice(0, 2) : a.slice(0, 2).toUpperCase()
})
const readingMins = computed(() => Math.max(1, Math.round((materials.article?.words ?? 0) / 400)))

/** 目录跟随：滚动时按小节距阅读区顶部 96px 内判定当前项，rAF 节流避免逐像素重排 */
const activeToc = ref(0)
let spyRaf = 0
function spyToc() {
  const body = readerBodyEl.value
  if (!body) return
  const heads = body.querySelectorAll<HTMLElement>('.art-body .md h2, .art-body .md h3')
  const base = body.getBoundingClientRect().top
  let current = 0
  heads.forEach((h, i) => {
    if (h.getBoundingClientRect().top - base <= 96) current = i
  })
  activeToc.value = current
}
function onReaderScroll() {
  if (spyRaf || toc.value.length === 0) return
  spyRaf = requestAnimationFrame(() => {
    spyRaf = 0
    spyToc()
  })
}
// 换文章复位目录，渲染完成后再判定一次（首屏可能已滚过若干小节）
watch(
  () => materials.article?.path,
  () => {
    activeToc.value = 0
    void nextTick(spyToc)
  },
)

/** 文章内 .md 相对链接 → 库内文章跳转（链接即导航） */
function onArticleClick(e: MouseEvent) {
  const anchor = (e.target as Element).closest('a')
  if (!anchor || !materials.article) return
  const href = anchor.getAttribute('href') || ''
  if (!href.endsWith('.md')) return
  e.preventDefault()
  const dir = materials.article.path.includes('/')
    ? materials.article.path.slice(0, materials.article.path.lastIndexOf('/') + 1)
    : ''
  const out: string[] = []
  for (const part of (dir + href).split('/')) {
    if (!part || part === '.') continue
    if (part === '..') out.pop()
    else out.push(part)
  }
  void materials.openArticle(out.join('/'))
}

function openOriginal() {
  if (materials.current && materials.article?.source) {
    window.open(materialOriginalUrl(materials.current.id, materials.article.source))
  }
}

// ===== 源码编辑 =====
const editContent = ref('')
const saving = ref(false)

watch(
  () => materials.readerMode,
  (mode) => {
    if (mode === 'edit') editContent.value = materials.article?.content ?? ''
    if (mode === 'article') void nextTick(() => readerBodyEl.value?.scrollTo({ top: 0 }))
  },
)

async function saveEdit() {
  if (saving.value) return
  saving.value = true
  try {
    await materials.saveEdit(editContent.value)
  } catch (e) {
    materials.showToast(e instanceof Error ? e.message : String(e), undefined, true)
  } finally {
    saving.value = false
  }
}

// ===== 节点操作（新建/重命名走共享轻弹窗 .dlg-*；删除仍从树行 confirm 发起） =====
type NodeOp = 'folder' | 'article' | 'rename'
const dlgOpen = ref(false)
const dlgOp = ref<NodeOp>('folder')
const dlgName = ref('')
const dlgNode = ref<MaterialNode | null>(null)

/** 三态文案：标题/输入标签/placeholder（rename 的标题沿用 common.rename） */
function dlgCfg(op: NodeOp) {
  if (op === 'folder')
    return { title: t('materials.newFolder'), label: t('materials.folderNameLabel'), ph: t('materials.folderNamePh') }
  if (op === 'article')
    return { title: t('materials.newArticle'), label: t('materials.articleNameLabel'), ph: t('materials.articleNamePh') }
  return { title: t('common.rename'), label: t('materials.renameNodeLabel'), ph: '' }
}

function openDlg(op: NodeOp, preset = '') {
  dlgOp.value = op
  dlgName.value = preset
  dlgOpen.value = true
}

function onNewFolder() {
  openDlg('folder')
}

function onNewArticle() {
  openDlg('article')
}

function onRenameNode(node: MaterialNode) {
  dlgNode.value = node
  openDlg('rename', node.name)
}

async function submitDlg() {
  const name = dlgName.value.trim()
  if (!name) return
  dlgOpen.value = false
  if (dlgOp.value === 'folder') void materials.createFolder(name, currentUploadDir())
  else if (dlgOp.value === 'article') void materials.createArticle(name, currentUploadDir())
  else if (dlgNode.value && name !== dlgNode.value.name) void materials.renameNode(dlgNode.value.path, name)
}

function onDeleteNode(node: MaterialNode) {
  if (window.confirm(t('materials.confirmDelete', { name: node.name }))) void materials.deleteNode(node.path)
}

function updatedTs(ts: number): string {
  return relTime(new Date(ts).toISOString())
}

/** 阅读态元信息用绝对日期（相对时间放在列表与树里更合适） */
function dateText(ts: number): string {
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
</script>

<template>
  <div
    class="kb-view"
    @dragenter.prevent="dragDepth++"
    @dragover.prevent
    @dragleave.prevent="dragDepth = Math.max(0, dragDepth - 1)"
    @drop.prevent="onDrop"
  >
    <!-- 栏 1：文章树（操作：树头=新建，树行=打开/重命名/删除，树底=上传） -->
    <div class="kb-tree-col">
      <div class="tree-head">
        <i class="fas fa-book-open"></i>
        <span class="t" :title="materials.current?.name">{{ materials.current?.name }}</span>
        <span class="spring"></span>
        <button class="head-btn" :title="t('materials.newFolder')" @click="onNewFolder">
          <i class="fas fa-folder-plus"></i>
        </button>
        <button class="head-btn" :title="t('materials.newArticle')" @click="onNewArticle">
          <i class="fas fa-file-circle-plus"></i>
        </button>
        <!-- 创建类与导入类语义分组 -->
        <span class="head-sep"></span>
        <!-- 上传唯一入口（拖拽为等价手势，悬停提示格式与上限） -->
        <button class="head-btn" :title="t('materials.upload') + ' · ' + t('materials.dropHintShort')" @click="pickFiles">
          <i class="fas fa-arrow-up-from-bracket"></i>
        </button>
      </div>
      <!-- 树过滤：纯前端本地，输入即过滤（清空恢复，Esc 快捷清空） -->
      <div class="filter-row">
        <label class="filter">
          <i class="fas fa-magnifying-glass"></i>
          <input v-model="filterKw" type="text" :placeholder="t('materials.treeFilter')" @keydown.esc="filterKw = ''" />
        </label>
      </div>
      <div ref="treeEl" class="kb-tree">
        <!-- 上传中的短暂过渡行（同步转换，秒级） -->
        <div v-for="name in materials.uploading" :key="name" class="tree-row uploading">
          <i class="caret"></i>
          <i class="fic fas" :class="uploadIcon(name)"></i>
          <span class="nm2">{{ name }}</span>
          <span class="conv"><i class="fas fa-circle-notch fa-spin"></i>{{ t('materials.converting') }}</span>
        </div>

        <MaterialTreeNode
          v-for="node in filteredTree"
          :key="node.path"
          :node="node"
          :depth="0"
          :filtering="filtering"
          @open="materials.openArticle($event.path)"
          @menu="onRowMenu"
        />

        <div v-if="filtering && filteredTree.length === 0" class="tree-empty">
          <i class="fas fa-magnifying-glass"></i>
          <div>{{ t('materials.filterEmpty') }}</div>
        </div>
        <div v-else-if="!filtering && materials.tree.length === 0 && materials.uploading.length === 0 && !materials.treeLoading" class="tree-empty">
          <i class="fas fa-file-circle-plus"></i>
          <div>{{ t('materials.treeEmpty') }}</div>
        </div>
      </div>
    </div>

    <!-- 栏 2：预览阅读区（头部只放阅读上下文操作） -->
    <div class="kb-reader">
      <div class="reader-head">
        <button class="back-btn" :title="t('materials.backToList')" @click="materials.backToList()">
          <i class="fas fa-arrow-left"></i>
        </button>
        <div class="crumbs">
          <template v-for="(crumb, i) in crumbs" :key="i">
            <i v-if="i > 0" class="sep fas fa-chevron-right"></i>
            <span
              :class="{ lnk: crumb.kind !== 'current', cur: crumb.kind === 'current' }"
              @click="crumb.kind !== 'current' && onCrumb(crumb)"
            >{{ crumb.text }}</span>
          </template>
        </div>
        <span class="spring"></span>

        <!-- 阅读态：按 kind 提供视图切换（文本类 预览/源码 · 富文档 原件/文本）+ 编辑或打开原件 -->
        <template v-if="materials.readerMode === 'article'">
          <div class="view-switch">
            <template v-if="isRichDoc">
              <button :class="{ on: view === 'original' }" @click="view = 'original'">
                <i class="fas fa-file-lines"></i>{{ t('materials.viewOriginal') }}
              </button>
              <button :class="{ on: view === 'text' }" @click="view = 'text'">
                <i class="fas fa-align-left"></i>{{ t('materials.viewText') }}
              </button>
            </template>
            <template v-else>
              <button :class="{ on: view === 'preview' }" @click="view = 'preview'">
                <i class="fas fa-eye"></i>{{ t('materials.viewPreview') }}
              </button>
              <button :class="{ on: view === 'source' }" @click="view = 'source'">
                <i class="fas fa-code"></i>{{ t('materials.viewSource') }}
              </button>
            </template>
          </div>
          <button v-if="isRichDoc && materials.article?.source" class="act-btn" @click="openOriginal">
            <i class="fas fa-up-right-from-square"></i>{{ t('materials.openOriginal') }}
          </button>
          <button v-else-if="!isRichDoc" class="act-btn" @click="materials.startEdit()">
            <i class="fas fa-pen"></i>{{ t('materials.edit') }}
          </button>
        </template>

        <!-- 编辑态：取消 / 保存 -->
        <template v-else-if="materials.readerMode === 'edit'">
          <button class="act-btn" @click="materials.cancelEdit()">
            <i class="fas fa-times"></i>{{ t('common.cancel') }}
          </button>
          <button class="act-btn primary" :disabled="saving" @click="saveEdit">
            <i class="fas fa-check"></i>{{ t('common.save') }}
          </button>
        </template>
      </div>

      <div ref="readerBodyEl" class="reader-body" :class="{ flush: fullbleed }" @scroll="onReaderScroll">
        <!-- 加载态 -->
        <div v-if="materials.articleLoading" class="reader-loading"><i class="fas fa-circle-notch fa-spin"></i></div>

        <!-- 目录首页（文库封面页：Hero 锚点 + 文章卡片陈列，V2） -->
        <div v-else-if="materials.readerMode === 'home'" class="reader-wrap">
          <div class="kb-home">
            <div class="kb-hero">
              <div class="mark"><i class="fas fa-book-open"></i></div>
              <div class="bd">
                <div class="t">{{ materials.current?.name }}</div>
                <div v-if="materials.current?.description" class="desc">{{ materials.current.description }}</div>
                <div v-if="materials.tree.length > 0" class="meta">
                  <span><b>{{ materials.current?.articleCount ?? 0 }}</b> {{ t('materials.articlesShort') }}</span>
                  <span><b>{{ formatWordCount(materials.current?.totalChars) }}</b> {{ t('materials.charsShort') }}</span>
                  <span>{{ t('materials.updatedAt', { time: updatedTs(materials.current?.updatedAt ?? Date.now()) }) }}</span>
                </div>
              </div>
            </div>

            <template v-for="group in homeGroups" :key="group.name">
              <div v-if="group.name" class="gt">
                <i class="fas fa-folder"></i><span class="t">{{ group.name }}</span>
                <span class="cnt">{{ group.items.length }}</span><span class="rule"></span>
              </div>
              <div class="card-grid">
                <div v-for="item in group.items" :key="item.path" class="kb-card" @click="materials.openArticle(item.path)">
                  <div class="c-head">
                    <div class="fic"><i class="fas" :class="iconOf(item.kind)"></i></div>
                    <span class="nm" :title="item.name">{{ item.name }}</span>
                  </div>
                  <div class="c-sm">{{ item.summary }}</div>
                  <div class="c-mt">{{ formatWordCount(item.words) }} · {{ updatedTs(item.updatedAt) }}</div>
                </div>
              </div>
            </template>

            <div v-if="materials.tree.length === 0" class="home-empty">
              <div class="big"><i class="fas fa-arrow-up-from-bracket"></i></div>
              <div class="t">{{ t('materials.homeEmptyTip') }}</div>
              <button class="btn-upload" @click="pickFiles">
                <i class="fas fa-arrow-up-from-bracket"></i>{{ t('materials.uploadDoc') }}
              </button>
              <div class="hint">{{ t('materials.homeEmptyHint') }}</div>
            </div>
          </div>
        </div>

        <!-- 整篇阅读（按 view × kind 分支：源码 / 原件 / HTML 预览 / Markdown 渲染） -->
        <template v-else-if="materials.readerMode === 'article' && materials.article">
          <!-- 源码：md / html 原文只读 -->
          <div v-if="view === 'source'" class="reader-wrap">
            <pre class="src-code">{{ materials.article.content }}</pre>
          </div>

          <!-- 原件：pdf/docx 统一走 MaterialOriginalViewer（占满阅读区：pdf 原生内嵌 · docx 前端分页渲染） -->
          <div v-else-if="view === 'original'" class="bleed-host">
            <MaterialOriginalViewer v-if="originalUrl" :kind="kind === 'pdf' ? 'pdf' : 'docx'" :src="originalUrl" @open="openOriginal" />
            <div v-else class="doc-card">
              <div class="doc-ic"><i class="fas" :class="iconOf(kind)"></i></div>
              <div class="doc-t">{{ materials.article.title }}</div>
              <div class="doc-s">{{ t('materials.docxNoInline') }}</div>
              <button class="btn-upload" @click="openOriginal">
                <i class="fas fa-up-right-from-square"></i>{{ t('materials.openOriginal') }}
              </button>
            </div>
          </div>

          <!-- HTML 预览：沙箱 iframe 渲染原文（sandbox 空值禁脚本，防 XSS），占满阅读区 -->
          <div v-else-if="kind === 'html'" class="bleed-host">
            <iframe class="html-frame" sandbox="" :srcdoc="materials.article.content" title="HTML"></iframe>
          </div>

          <!-- Markdown 渲染：普通 md 预览 / 富文档抽取正文（672px 正文 + 180px 目录栏整体居中） -->
          <div v-else class="reader-wrap" :class="{ 'with-toc': toc.length > 0 }">
            <div class="article">
              <div class="art-title">{{ materials.article.title }}</div>
              <div class="art-meta">
                <span
                  v-if="materials.article.source"
                  class="src-link"
                  :title="t('materials.originalTip')"
                  @click="openOriginal"
                >
                  <i class="fas fa-file-shield"></i>{{ t('materials.fromOriginal', { name: materials.article.source.split('/').pop() }) }}
                </span>
                <span v-if="authorShort" class="who"><span class="av">{{ authorShort }}</span>{{ materials.article.author }}</span>
                <span class="dot"></span>
                <span>{{ dateText(materials.article.updatedAt) }}</span>
                <span class="dot"></span>
                <span>{{ formatWordCount(materials.article.words) }} {{ t('materials.unitChars') }}</span>
                <span class="dot"></span>
                <span>{{ t('materials.readingTime', { n: readingMins }) }}</span>
              </div>
              <div class="art-rule"></div>
              <div class="art-body">
                <!-- eslint-disable-next-line vue/no-v-html —— md 渲染经 renderMarkdown 统一出口（对齐聊天区） -->
                <div class="md mat-md" v-html="articleHtml" @click="onArticleClick"></div>
              </div>
            </div>
            <aside v-if="toc.length > 0" class="art-toc">
              <div class="t">{{ t('materials.toc') }}</div>
              <a
                v-for="(item, i) in toc"
                :key="i"
                :class="{ sub: item.level === 3, act: i === activeToc }"
                @click="jumpToc(i)"
              >{{ item.text }}</a>
            </aside>
          </div>
        </template>

        <!-- 源码编辑（MaterialEditor：格式工具条 / 编辑预览切换 / 状态栏 / 快捷键） -->
        <div v-else-if="materials.readerMode === 'edit'" class="editor-host">
          <MaterialEditor
            v-model="editContent"
            :original="materials.article?.content ?? ''"
            @save="saveEdit"
            @cancel="materials.cancelEdit()"
          />
        </div>
      </div>
    </div>

    <!-- 拖拽落区（整详情页覆盖） -->
    <div v-if="dragDepth > 0" class="drop-veil">
      <div class="box">
        <i class="fas fa-file-arrow-up"></i>
        <div class="t">{{ t('materials.dropTitle', { name: materials.current?.name }) }}</div>
        <div class="s">{{ t('materials.dropHint') }}</div>
      </div>
    </div>

    <!-- 隐藏文件选择器 -->
    <input ref="fileInput" type="file" multiple accept=".md,.markdown,.txt,.pdf,.docx,.html,.htm" style="display: none" @change="onPickFiles" />

    <!-- 行右键菜单（行操作唯一入口；Teleport 到 body 防树区 overflow 裁剪） -->
    <Teleport to="body">
      <div v-if="ctxMenu" ref="menuEl" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
        <button v-if="ctxMenu.node.type === 'file'" @click="ctxAction('open')">
          <i class="far fa-eye"></i>{{ t('materials.open') }}
        </button>
        <button @click="ctxAction('rename')">
          <i class="fas fa-pen"></i>{{ t('common.rename') }}
        </button>
        <button class="danger" @click="ctxAction('delete')">
          <i class="fas fa-trash-alt"></i>{{ t('common.delete') }}
        </button>
      </div>
    </Teleport>

    <!-- 新建文件夹 / 新建文章 / 重命名共用轻弹窗（.dlg-* 全局样式，替代 window.prompt） -->
    <Teleport to="body">
      <div v-if="dlgOpen" class="dlg-mask" @click.self="dlgOpen = false">
        <div class="dlg">
          <div class="dlg-title">{{ dlgCfg(dlgOp).title }}</div>
          <label class="dlg-label">{{ dlgCfg(dlgOp).label }}</label>
          <input
            v-model="dlgName"
            class="dlg-input"
            :placeholder="dlgCfg(dlgOp).ph"
            autofocus
            @keyup.enter="submitDlg"
          />
          <div class="dlg-acts">
            <button class="dlg-btn ghost" @click="dlgOpen = false">{{ t('common.cancel') }}</button>
            <button class="dlg-btn solid" :disabled="!dlgName.trim()" @click="submitDlg">{{ t('common.save') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.kb-view {
  height: 100%; /* 父级 .materials-view 非 flex 容器，用显式高度铺满 */
  display: flex;
  min-height: 0;
  position: relative;
}

/* ===== 栏 1：文章树（头部规格对齐 steps-panel-header） ===== */
.kb-tree-col {
  width: 264px;
  flex-shrink: 0;
  border-right: 1px solid var(--mat-line);
  background: var(--mat-card);
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.tree-head {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 49px; /* 定高垂直居中，与右侧 reader-head 底线对齐 */
  padding: 0 14px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.tree-head > i {
  color: var(--text-secondary);
  font-size: 12px;
  flex-shrink: 0;
}
.tree-head .t {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-head .spring {
  flex: 1;
}
.head-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 7px;
  font-size: 11.5px;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;
}
.head-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.kb-tree {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
}
/* 上传过渡行：与树行同规格（基础行样式在 MaterialTreeNode scoped 内，此处补齐同款布局） */
.tree-row.uploading {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  margin: 2px 0;
  padding: 0 9px;
  border-radius: 8px;
  font-size: 12.5px;
}
.tree-row.uploading .caret {
  width: 12px;
  flex-shrink: 0;
}
.tree-row.uploading .fic {
  width: 16px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
  flex-shrink: 0;
}
/* 上传中行同树规则全类型着色：作用域提权覆盖上方基色，色值取 index.css :root */
.tree-row.uploading .fic.icon-pdf { color: var(--ic-pdf); }
.tree-row.uploading .fic.icon-word { color: var(--ic-word); }
.tree-row.uploading .fic.icon-md { color: var(--ic-md); }
.tree-row.uploading .fic.icon-html { color: var(--ic-html); }
.tree-row.uploading .nm2 {
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-row.uploading .conv {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--info);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.tree-empty {
  margin: 12px 8px;
  padding: 16px 10px;
  text-align: center;
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 1.9;
}
.tree-empty i {
  font-size: 16px;
  opacity: 0.6;
  margin-bottom: 4px;
}

/* ===== 栏 2：阅读区（页面级宽松头部：10px 20px 内距 + 48px 高，对齐设计稿比例） ===== */
.kb-reader {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  /* 靠底色分层：树栏白卡 → 内容区极浅灰 → 文章条目回到白底 */
  background: var(--mat-surface-2);
  color: var(--mat-ink);
}
.reader-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  height: 49px; /* 定高与左侧 tree-head 同高，左右分隔线严格同线 */
  background: var(--mat-card);
  border-bottom: 1px solid var(--mat-line);
  flex-shrink: 0;
}
/* 返回按钮：图标钮规格（无边框，悬停浅洗底） */
.back-btn {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--mat-faint);
  font-size: 12px;
  margin-left: -6px;
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.16s ease;
}
.back-btn:hover {
  background: var(--mat-wash);
  color: var(--mat-ink);
}
.crumbs {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--mat-faint);
  min-width: 0;
  overflow: hidden;
}
.crumbs .lnk {
  cursor: pointer;
  white-space: nowrap;
}
.crumbs .lnk:hover {
  color: var(--mat-ink);
}
.crumbs .sep {
  color: var(--mat-line-2);
  font-size: 7.5px;
}
.crumbs .cur {
  color: var(--mat-ink);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.reader-head .spring {
  flex: 1;
}

/* 动作按钮：描边次按钮（白底 + --mat-line-2 边），主动作为墨色实心 */
.act-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 28px;
  padding: 0 11px;
  border: 1px solid var(--mat-line-2);
  border-radius: 9px;
  background: var(--mat-card);
  color: var(--mat-ink-2);
  font-size: 12px;
  font-family: inherit;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}
.act-btn:hover {
  border-color: var(--mat-faint);
  background: var(--mat-surface-2);
  color: var(--mat-ink);
}
.act-btn i {
  font-size: 10px;
  color: var(--mat-mut);
}
.act-btn.primary {
  background: var(--mat-ink);
  border-color: var(--mat-ink);
  color: var(--mat-card);
  font-weight: 600;
}
.act-btn.primary i {
  color: var(--mat-card);
  opacity: 0.8;
}
.act-btn.primary:hover {
  background: var(--mat-ink-2);
  border-color: var(--mat-ink-2);
  color: var(--mat-card);
}
.act-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
.act-btn:disabled:hover {
  background: var(--mat-card);
  border-color: var(--mat-line-2);
  color: var(--mat-ink-2);
}
.act-btn.primary:disabled:hover {
  background: var(--mat-ink);
  border-color: var(--mat-ink);
  color: var(--mat-card);
}

.reader-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.reader-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
}
.reader-wrap {
  padding: 32px 32px 64px;
}
/* 有文内目录时：正文列 672px + 目录列 180px 整体居中，正文列宽稳定 */
.reader-wrap.with-toc {
  display: grid;
  grid-template-columns: minmax(0, 672px) 180px;
  gap: 36px;
  justify-content: center;
  align-items: start;
}

/* 版式预览满幅：外层不滚、不留白，内层（iframe / 渲染容器）自滚占满 */
.reader-body.flush {
  overflow: hidden;
}
.bleed-host {
  height: 100%;
  display: flex;
}
.bleed-host > .doc-card {
  margin: auto; /* 无原件兜底卡片：满幅区居中的唯一例外 */
}

/* 目录首页：文库封面页（Hero 锚点 + 文章卡片陈列） */
.kb-home {
  max-width: 720px;
  margin: 0 auto;
}
/* Hero：标识块 + 库名大字 + 描述 + 元信息 chips——进库第一眼有锚点 */
.kb-hero {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 20px;
}
.kb-hero .mark {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: var(--bg-blockquote);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.kb-hero .mark i { font-size: 19px; color: var(--accent); }
.kb-hero .t {
  font-size: 21px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0.2px;
}
.kb-hero .desc {
  font-size: 12.5px;
  color: var(--text-secondary);
  margin-top: 4px;
  line-height: 1.6;
}
.kb-hero .meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.kb-hero .meta span {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-input);
  border-radius: 20px;
  padding: 3px 10px;
}
.kb-hero .meta b {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
  color: var(--text-primary);
}

/* 文件夹组头：名称 + 计数徽标 + 延展细线 */
.gt {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 20px 0 10px;
}
.gt i { font-size: 11px; color: var(--text-secondary); }
.gt .t { font-size: 12px; font-weight: 600; }
.gt .cnt {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: var(--text-secondary);
  background: var(--bg-input);
  border-radius: 9px;
  padding: 1px 8px;
}
.gt .rule {
  flex: 1;
  height: 1px;
  background: var(--border-color);
  opacity: 0.6;
}

/* 文章卡片：图标块 + 标题 + 两行摘要 + mono 元信息（与列表页卡片语言同源） */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(218px, 1fr));
  gap: 12px;
}
.kb-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 13px 14px 11px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  cursor: pointer;
}
.kb-card:hover {
  border-color: var(--text-muted);
  background: var(--bg-hover);
}
.kb-card .c-head {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}
.kb-card .fic {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--bg-blockquote);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.kb-card .fic i { font-size: 11.5px; color: var(--accent); }
/* 首页卡片图标同树全类型着色：作用域提权覆盖 accent 基色 */
.kb-card .fic i.icon-pdf { color: var(--ic-pdf); }
.kb-card .fic i.icon-word { color: var(--ic-word); }
.kb-card .fic i.icon-md { color: var(--ic-md); }
.kb-card .fic i.icon-html { color: var(--ic-html); }
.kb-card .nm {
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kb-card .c-sm {
  font-size: 11.5px;
  color: var(--text-secondary);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kb-card .c-mt {
  margin-top: auto;
  padding-top: 2px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: var(--text-muted);
}

/* 空库：行动点前置（实底上传主按钮） */
.home-empty {
  text-align: center;
  padding: 34px 0 8px;
}
.home-empty .big {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: var(--bg-blockquote);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
}
.home-empty .big i { font-size: 24px; color: var(--accent); }
.home-empty .t {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.btn-upload {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 16px;
  padding: 9px 22px;
  border: none;
  border-radius: 9px;
  background: var(--accent);
  color: var(--accent-ink);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}
.btn-upload:hover { opacity: 0.88; }
.home-empty .hint {
  margin-top: 12px;
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.7;
}

/* 文章阅读：无目录时正文列仍占 672px 同宽，切换文章不产生横向跳动 */
.article {
  max-width: 672px;
  min-width: 0;
  margin: 0 auto;
}
.art-title {
  font-size: 27px;
  font-weight: 650;
  letter-spacing: -0.028em;
  line-height: 1.28;
  color: var(--mat-ink);
}
/* 元信息：一行点分轻量文字（faint），来源为强调色可点文字，作者带缩写头像 */
.art-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 14px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--mat-faint);
}
.art-meta .dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--mat-line-2);
  margin: 0 8px;
}
.art-meta .who {
  display: inline-flex;
  align-items: center;
}
.art-meta .av {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--mat-wash-2);
  color: var(--mat-ink-2);
  font-size: 8.5px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 7px;
}
.art-rule {
  height: 1px;
  background: var(--mat-line);
  margin: 22px 0 26px;
}
.src-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--mat-accent);
  font-weight: 550;
  cursor: pointer;
}
.src-link:hover {
  text-decoration: underline;
}
.src-link i {
  font-size: 10px;
}

/* md 排版见全局 .mat-md（index.css）：整篇阅读与编辑器预览共用同一套长文规格 */

/* 文内 TOC：1px 左线串联条目，当前项靠强调色改线色与文字而非加底色块 */
.art-toc {
  position: sticky;
  top: 16px;
  font-size: 12px;
  color: var(--mat-mut);
}
.art-toc .t {
  padding-left: 13px; /* 对齐条目文字（border-left 1px + 内距 12px） */
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--mat-faint);
  margin-bottom: 12px;
}
.art-toc a {
  position: relative;
  display: block;
  padding: 5px 0 5px 13px;
  line-height: 1.45;
  color: var(--mat-mut);
  border-left: 1px solid var(--mat-line);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.14s ease, border-color 0.14s ease;
}
.art-toc a:hover {
  color: var(--mat-ink);
}
.art-toc a.sub {
  padding-left: 24px;
  font-size: 11.5px;
  color: var(--mat-faint);
}
.art-toc a.act {
  color: var(--mat-accent);
  font-weight: 600;
  border-left-color: var(--mat-accent);
}

/* 源码编辑：MaterialEditor 铺满 reader-body，滚动由编辑器内部（textarea/预览列）自理 */
.editor-host {
  height: 100%;
  min-height: 0;
}

/* 阅读头部视图切换器：浅洗凹槽分段（预览/源码 · 原件/文本），激活项白底浮起 */
.view-switch {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--mat-line);
  border-radius: 9px;
  background: var(--mat-wash);
  flex-shrink: 0;
}
.view-switch button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 10px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--mat-mut);
  font-size: 11.5px;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s ease, color 0.16s ease;
}
.view-switch button:hover {
  color: var(--mat-ink);
}
.view-switch button.on {
  background: var(--mat-card);
  color: var(--mat-ink);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(28, 33, 40, 0.06);
}
.view-switch button i {
  font-size: 9px;
}

/* 源码只读视图：白底描边等宽块，横向滚动由 pre 自理 */
.src-code {
  max-width: 672px;
  margin: 0 auto;
  padding: 18px 22px;
  background: var(--mat-card);
  border: 1px solid var(--mat-line);
  border-radius: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--mat-ink-2);
  white-space: pre;
  overflow: auto;
}

/* HTML 预览：沙箱 iframe 渲染原文，满幅占位（无卡片边框/圆角，与 pdf/docx 一致），白底贴合文档自身配色 */
.html-frame {
  display: block;
  flex: 1;
  width: 100%;
  min-height: 0;
  height: 100%;
  border: none;
  background: #ffffff;
}

/* docx 无法内联：居中占位卡片 + 打开原件主按钮 */
.doc-card {
  max-width: 440px;
  margin: 40px auto;
  text-align: center;
  padding: 34px 28px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
}
.doc-card .doc-ic {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  background: var(--bg-blockquote);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
}
.doc-card .doc-ic i {
  font-size: 24px;
  color: var(--accent);
}
/* docx 占位卡跟随 word 蓝（该卡仅 docx 可达） */
.doc-card .doc-ic i.icon-word { color: var(--ic-word); }
.doc-card .doc-t {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.doc-card .doc-s {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 8px;
  line-height: 1.6;
}

.head-sep {
  width: 1px;
  height: 14px;
  background: var(--border-color);
  margin: 0 3px;
  flex-shrink: 0;
}
/* ===== 树过滤行 ===== */
.filter-row {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.filter {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 10px;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  transition: border-color 0.15s ease;
}
.filter:focus-within {
  border-color: var(--text-muted);
}
.filter > i {
  color: var(--text-muted);
  font-size: 10.5px;
}
.filter input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
}
.filter input::placeholder {
  color: var(--text-muted);
}
/* ===== 行右键菜单 ===== */
.ctx-menu {
  position: fixed;
  z-index: 200;
  min-width: 132px;
  padding: 4px;
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: var(--shadow);
}
.ctx-menu button {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  background: none;
  border-radius: 7px;
  font-size: 12px;
  color: var(--text-primary);
  cursor: pointer;
  text-align: left;
}
.ctx-menu button:hover {
  background: var(--bg-hover);
}
.ctx-menu button i {
  width: 14px;
  font-size: 11px;
  color: var(--text-secondary);
  text-align: center;
}
.ctx-menu button.danger:hover {
  color: var(--error);
  background: color-mix(in srgb, var(--error) 8%, transparent);
}
.ctx-menu button.danger:hover i {
  color: var(--error);
}

/* 拖拽落区 */
.drop-veil {
  position: absolute;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--info) 6%, transparent);
}
.drop-veil .box {
  border: 2px dashed var(--info);
  border-radius: 16px;
  padding: 34px 52px;
  text-align: center;
  color: var(--info);
  background: var(--bg-app);
  box-shadow: var(--shadow);
}
.drop-veil .box i {
  font-size: 26px;
  margin-bottom: 10px;
}
.drop-veil .box .t {
  font-size: 14px;
  font-weight: 600;
}
.drop-veil .box .s {
  font-size: 11.5px;
  margin-top: 6px;
  opacity: 0.85;
}
</style>
