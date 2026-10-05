<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWorkspaceStore } from '@/stores/workspace'
import { usePreviewStore } from '@/stores/preview'
import { rawFileUrl, readFileContent, revealWorkspaceFile } from '@/api/config'
import { useTheme } from '@/composables/theme'
import { buildMarkdownPage, isMarkdownFile } from '@/utils/markdownPage'

/**
 * 工作区面板「浏览器」Tab 内容：工具栏（地址栏 + 动作钮）+ 渲染区 + 沙箱脚注 + 空态。
 * 地址栏按模式驱动：文件（mono 路径面包屑，解锁 reveal/复制路径等文件动作）、
 * 内联（代码块预览等无文件语义的 srcdoc 页）、网页（外部 URL）。
 * 组件为多根 fragment，节点直接参与 .ws-pane（flex column）布局。
 */
const { t } = useI18n()
const ws = useWorkspaceStore()
const preview = usePreviewStore()
const { isLight } = useTheme()

/** 刷新重挂计数（srcdoc/同 URL 内容不变时也强制重建 iframe 的 key） */
const tick = ref(0)
const reloading = ref(false)
/** 复制成功反馈（按钮图标短暂切换为对勾） */
const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | null = null

/** 文件模式 = srcdoc 且带工作区文件语义 */
const isFile = computed(() => preview.mode === 'html' && !!preview.path)

/**
 * 地址栏面包屑：工作区名 › 目录 › 文件，末段加粗；
 * 超过 4 段折叠中段为 …（工作区名占首段，不参与折叠）
 */
const crumbs = computed(() => {
  if (!isFile.value) return []
  const segs = preview.path.split('/')
  const out: { text: string; cur: boolean }[] = []
  const wsName = ws.current?.name
  if (wsName) out.push({ text: wsName, cur: false })
  if (segs.length > 4) {
    out.push(
      { text: segs[0] ?? '', cur: false },
      { text: '…', cur: false },
      { text: segs[segs.length - 1] ?? '', cur: true },
    )
  } else {
    segs.forEach((s, i) => out.push({ text: s, cur: i === segs.length - 1 }))
  }
  return out
})

/** 刷新：文件模式重拉内容重建 srcdoc（md 重新转阅读页）；内联/网页直接重挂 iframe */
async function reload() {
  if (isFile.value) {
    reloading.value = true
    try {
      const res = await readFileContent(preview.path)
      if (res.type === 'text') {
        const content = String(res.content ?? '')
        preview.openHtml(
          isMarkdownFile(preview.path) ? buildMarkdownPage(content, isLight.value) : content,
          preview.title,
          preview.path,
        )
      }
    } catch {
      // 拉取失败保留当前内容，仅重挂
    }
    reloading.value = false
  }
  tick.value++
}

/** 在系统文件管理器中定位文件（后端 explorer /select，失败静默） */
async function revealFolder() {
  if (!preview.path) return
  try {
    await revealWorkspaceFile(preview.path)
  } catch {
    // 后端报错（未选工作区/路径越界）静默
  }
}

/** 系统浏览器打开：文件走 raw 字节端点，网页走原 URL */
function openExternal() {
  window.open(isFile.value ? rawFileUrl(preview.path) : preview.url)
}

/** 复制路径/链接，按钮短暂显示对勾反馈 */
async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    if (copiedTimer) clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => (copied.value = false), 1200)
  } catch {
    // 剪贴板不可用（非安全上下文等）静默
  }
}
</script>

<template>
  <!-- 空态（复用面板空态样式，与审查/终端 Tab 视觉一致） -->
  <div v-if="preview.mode === 'none'" class="empty-steps">
    <i class="fas fa-globe"></i>
    <span>{{ t('wsPanel.emptyBrowser') }}</span>
    <span class="bp-empty-hint">{{ t('wsPanel.emptyBrowserHint') }}</span>
  </div>
  <template v-else>
    <div class="bp-bar">
      <div class="bp-addr" :title="isFile ? preview.path : preview.url">
        <!-- 文件模式：mono 路径面包屑 -->
        <template v-if="isFile">
          <i class="fas fa-file-lines bp-aico file"></i>
          <span class="bp-crumb">
            <template v-for="(c, i) in crumbs" :key="i">
              <i v-if="i > 0" class="fas fa-chevron-right bp-sep"></i>
              <span class="bp-seg" :class="{ cur: c.cur }">{{ c.text }}</span>
            </template>
          </span>
        </template>
        <!-- 内联预览（代码块预览等，无文件语义） -->
        <template v-else-if="preview.mode === 'html'">
          <i class="fas fa-code bp-aico"></i>
          <span class="bp-seg">{{ t('wsPanel.inlinePreview') }}</span>
        </template>
        <!-- 网页模式：来源 URL -->
        <template v-else>
          <i class="fas fa-globe bp-aico"></i>
          <span class="bp-seg">{{ preview.url }}</span>
        </template>
      </div>
      <div class="bp-acts">
        <button :title="t('wsPanel.reload')" :class="{ spin: reloading }" @click="reload">
          <i class="fas fa-arrows-rotate"></i>
        </button>
        <button v-if="isFile" :title="t('wsPanel.revealFolder')" @click="revealFolder">
          <i class="fas fa-folder-open"></i>
        </button>
        <button :title="t('wsPanel.openExternal')" @click="openExternal">
          <i class="fas fa-arrow-up-right-from-square"></i>
        </button>
        <button
          :title="isFile ? t('wsPanel.copyPath') : t('wsPanel.copyLink')"
          @click="copyText(isFile ? preview.path : preview.url)"
        >
          <i class="fas" :class="copied ? 'fa-check' : 'fa-copy'"></i>
        </button>
      </div>
    </div>
    <!-- 沙箱策略对齐旧版：srcdoc（自建页）绝不可给 allow-same-origin，否则可逆向操作父页；
         外部 URL（源网页）则需要它，否则站点访问 cookie/localStorage 会报错白屏 -->
    <iframe
      v-if="preview.mode === 'html'"
      :key="`h${tick}`"
      class="ws-preview-frame"
      :srcdoc="preview.html"
      sandbox="allow-scripts allow-popups allow-popups-to-escape-sandbox"
    ></iframe>
    <!-- 部分站点用 X-Frame-Options/CSP 禁止嵌入会显示空白（改用来源 chip 的阅读模式） -->
    <iframe
      v-else-if="preview.mode === 'url'"
      :key="`u${tick}`"
      class="ws-preview-frame"
      :src="preview.url"
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
    ></iframe>
    <div v-if="preview.mode === 'html'" class="bp-note">
      <i class="fas fa-circle-info"></i>
      {{ t('wsPanel.sandboxNote') }}
    </div>
  </template>
</template>

<style>
/* 浏览器 Tab 工具栏（bp- 前缀全局；地址栏 pill 同 tool-chip 的 bg-hover + mono 语言） */
.bp-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 9px;
  border-bottom: 1px solid var(--border-color);
}
.bp-addr {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  background: var(--bg-hover);
  border: 1px solid transparent;
  border-radius: 7px;
  padding: 5px 10px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: var(--text-secondary);
  overflow: hidden;
  white-space: nowrap;
}
.bp-addr:hover {
  border-color: var(--border-active);
}
.bp-aico {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 10px;
}
.bp-aico.file {
  color: var(--text-primary);
}
.bp-crumb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}
.bp-seg {
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-muted);
}
.bp-seg.cur {
  color: var(--text-primary);
  font-weight: 600;
}
.bp-sep {
  flex-shrink: 0;
  font-size: 7px;
  color: var(--text-muted);
  opacity: 0.55;
}
.bp-acts {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.bp-acts button {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  transition:
    border-color 0.12s,
    color 0.12s,
    background 0.12s;
}
.bp-acts button:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  border-color: var(--border-color);
}
.bp-acts button.spin i {
  animation: bp-spin 0.8s linear infinite;
}
@keyframes bp-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
.bp-note {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 11px;
  border-top: 1px solid var(--border-color);
  font-size: 10px;
  color: var(--text-muted);
}
.bp-note i {
  font-size: 9px;
}
.bp-empty-hint {
  font-size: 10.5px;
  opacity: 0.75;
}
</style>
