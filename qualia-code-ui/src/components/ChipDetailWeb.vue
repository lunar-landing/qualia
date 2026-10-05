<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  absUrl,
  argStr,
  displayUrl,
  isFetchError,
  parseWebFetch,
  parseWebSearch,
  shortContentType,
  WEB_SEARCH_SOURCES,
  type ToolArgs,
} from '@/utils/chip'
import { buildReaderPage, buildSearchPage } from '@/utils/webpage'
import { usePreviewStore } from '@/stores/preview'
import { useTheme } from '@/composables/theme'
import type { AgentStep } from '@/types'

/**
 * chip 联网工具详情（平移旧 js/browser.js：WebSearchDetail + WebFetchDetail）：
 * 搜索（baidu/bing/google/duckduckgo/tavily）紧凑列表 + 「查看全部」生成自包含结果页；
 * web_fetch 徽章头 + 正文预览 + 「源网页 / 阅读模式」；解析失败一律降级 tc-code 纯文本。
 * 「查看全部 / 阅读模式」改由 preview store 驱动工作区浏览器 Tab（旧 STORE + onclick 内联的 Vue 化）。
 */
const props = defineProps<{
  step: AgentStep
  result: { ok: boolean; content: string } | null
}>()

const preview = usePreviewStore()
const { isLight } = useTheme()
const { t } = useI18n()

const args = computed(() => (props.step.toolArgs ?? {}) as ToolArgs)

// ===== 网页搜索 =====
const isSearch = computed(() => (props.step.toolName ?? '') in WEB_SEARCH_SOURCES)
const searchSource = computed(() => WEB_SEARCH_SOURCES[props.step.toolName ?? ''] ?? '')
const searchQuery = computed(() => argStr(args.value, 'query'))
const searchParsed = computed(() => (props.result ? parseWebSearch(props.result.content) : null))
/** chip 详情默认展示条数，其余走「查看全部」 */
const LIST_LIMIT = 3
const searchShown = computed(() => searchParsed.value?.items.slice(0, LIST_LIMIT) ?? [])

const searchBadges = computed(() => {
  const badges = [searchSource.value]
  const parsed = searchParsed.value
  if (parsed) {
    badges.push(t('web.countBadge', { n: parsed.items.length }))
    if (parsed.time) badges.push(parsed.time)
  }
  return badges.filter(Boolean)
})

/** 「查看全部」：生成完整结果页送工作区浏览器 Tab */
function openAll() {
  const parsed = searchParsed.value
  if (!parsed) return
  const rec = {
    query: searchQuery.value,
    source: searchSource.value,
    items: parsed.items,
    time: parsed.time,
  }
  preview.openHtml(buildSearchPage(rec, isLight.value))
}

// ===== 网页抓取 =====
const fetchUrl = computed(() => argStr(args.value, 'url'))
const fetchParsed = computed(() => (props.result ? parseWebFetch(props.result.content) : null))
const fetchTitle = computed(() => fetchParsed.value?.title ?? '')
const fetchHeadUrl = computed(() => fetchParsed.value?.url || fetchUrl.value)
const fetchFailed = computed(() => !!fetchParsed.value && isFetchError(fetchParsed.value))
/** SPA 等场景后端会附带说明：仅在正文正常展示时以提示条呈现（错误态另有错误条） */
const fetchHint = computed(() => {
  const r = fetchParsed.value
  if (!r || isFetchError(r)) return ''
  return String(r.content ?? '').trim() ? (r.message ?? '') : ''
})
const fetchEmpty = computed(() => {
  const r = fetchParsed.value
  return !!r && !isFetchError(r) && !String(r.content ?? '').trim()
})
/** 正文预览：截前 600 字符压成单段连续展示（详情卡片内不可滚动，完整阅读走浏览器 Tab） */
const fetchPreview = computed(() => {
  const r = fetchParsed.value
  if (!r) return ''
  return String(r.content ?? '')
    .slice(0, 600)
    .replace(/\s+/g, ' ')
    .trim()
})

const fetchBadges = computed(() => {
  const r = fetchParsed.value
  if (!r) return []
  if (isFetchError(r)) {
    return r.status ? [{ text: String(r.status), cls: 'err' }] : []
  }
  return [
    r.status ? { text: String(r.status), cls: 'ok' } : null,
    r.content_type ? { text: shortContentType(r.content_type), cls: '' } : null,
    r.extracted_length ? { text: t('web.charsBadge', { n: r.extracted_length.toLocaleString() }), cls: '' } : null,
  ].filter((b) => b !== null)
})

/** 「源网页」：浏览器 Tab 直接加载原页（禁止嵌入的站点会空白，改用阅读模式） */
function openSource() {
  const r = fetchParsed.value
  if (!r) return
  const href = absUrl(r.url || fetchUrl.value)
  if (href) preview.openUrl(href)
}

/** 「阅读模式」：生成阅读模式页送工作区浏览器 Tab */
function openReader() {
  const r = fetchParsed.value
  if (!r) return
  const content = String(r.content ?? '')
  preview.openHtml(
    buildReaderPage(
      {
        title: r.title || '',
        url: r.url || fetchUrl.value,
        content,
        type: shortContentType(r.content_type),
        length: r.extracted_length || content.length,
      },
      isLight.value,
    ),
  )
}

</script>

<template>
  <!-- ===== 网页搜索 ===== -->
  <template v-if="isSearch">
    <div class="tc-web-head">
      <i class="fas fa-globe"></i>
      <span class="tc-web-query">{{ searchQuery }}</span>
      <span v-for="b in searchBadges" :key="b" class="tc-badge">{{ b }}</span>
    </div>
    <div v-if="!result" class="tc-running">
      <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('web.searching') }}</span>
    </div>
    <div v-else-if="!searchParsed" class="tc-code">{{ result?.content || t('web.noOutput') }}</div>
    <template v-else>
      <div class="tc-web-list">
        <a
          v-for="(it, i) in searchShown"
          :key="i"
          class="tc-web-item"
          :href="absUrl(it.url) || undefined"
          target="_blank"
          rel="noopener"
        >
          <span class="tc-web-main">
            <span class="tc-web-title">{{ it.title }}</span>
            <span v-if="it.url" class="tc-web-url">{{ displayUrl(it.url) }}</span>
            <span v-if="it.snippet" class="tc-web-snippet">{{ it.snippet }}</span>
          </span>
          <i class="fas fa-arrow-up-right-from-square tc-web-ext"></i>
        </a>
      </div>
      <button v-if="searchParsed.items.length > LIST_LIMIT" class="tc-web-more" @click="openAll">
        <i class="fas fa-angles-right"></i> {{ t('web.viewAll', { n: searchParsed.items.length }) }}
      </button>
    </template>
  </template>

  <!-- ===== 网页抓取 ===== -->
  <template v-else>
    <div class="tc-fetch-head">
      <div class="tc-fetch-title-row">
        <i class="fas fa-file-lines"></i>
        <span class="tc-fetch-title">{{ fetchTitle }}</span>
        <span v-for="(b, i) in fetchBadges" :key="i" class="tc-badge" :class="b.cls">{{ b.text }}</span>
      </div>
      <a v-if="fetchHeadUrl" class="tc-fetch-url" :href="absUrl(fetchHeadUrl)" target="_blank" rel="noopener">
        {{ displayUrl(fetchHeadUrl) }}
      </a>
    </div>
    <div v-if="!result" class="tc-running">
      <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('web.fetching') }}</span>
    </div>
    <div v-else-if="!fetchParsed" class="tc-code">{{ result?.content || t('web.noOutput') }}</div>
    <div v-else-if="fetchFailed" class="tc-fetch-error">
      <i class="fas fa-triangle-exclamation"></i><span>{{ fetchParsed?.message || t('web.fetchFailed') }}</span>
    </div>
    <div v-else-if="fetchEmpty" class="tc-code">{{ fetchParsed?.message || t('web.emptyPage') }}</div>
    <template v-else>
      <div v-if="fetchHint" class="tc-fetch-hint">
        <i class="fas fa-circle-info"></i><span>{{ fetchHint }}</span>
      </div>
      <div class="tc-fetch-preview">{{ fetchPreview }}</div>
      <div class="tc-fetch-actions">
        <button class="tc-fetch-open" @click="openSource">
          <i class="fas fa-globe"></i> {{ t('web.openSource') }}
        </button>
        <button class="tc-fetch-open" :title="t('web.readerModeTitle')" @click="openReader">
          <i class="fas fa-book-open"></i> {{ t('web.readerMode') }}
        </button>
      </div>
    </template>
  </template>
</template>

<style>
/* ===== 联网工具详情（旧 browser.js 自包含样式平移；tc-web- 系 / tc-fetch- 系前缀全局防冲突） ===== */
/* ---------- 网页搜索（tc-web-*） ---------- */
/* 查询头 */
.tc-web-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}
.tc-web-head .fa-globe {
  font-size: 11px;
  color: var(--accent-light);
}
.tc-web-query {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 紧凑列表 */
.tc-web-list {
  display: flex;
  flex-direction: column;
  max-height: 320px;
  overflow-y: auto;
}
.tc-web-list::-webkit-scrollbar {
  width: 5px;
}
.tc-web-list::-webkit-scrollbar-thumb {
  background: var(--border-active);
  border-radius: 4px;
}
.tc-web-item {
  display: flex;
  gap: 10px;
  padding: 11px 12px;
  text-decoration: none;
  transition: background 0.15s;
}
.tc-web-item:hover {
  background: var(--bg-hover);
}
.tc-web-item + .tc-web-item {
  border-top: 1px solid var(--border-color);
}
.tc-web-main {
  min-width: 0;
  flex: 1;
}
.tc-web-title {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tc-web-item:hover .tc-web-title {
  color: var(--accent-light);
}
.tc-web-url {
  display: block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: #7ddfb0;
  margin: 2px 0 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.85;
}
body.light-theme .tc-web-url {
  color: #0f7b4f;
}
.tc-web-snippet {
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tc-web-ext {
  flex-shrink: 0;
  align-self: center;
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0;
  transition: opacity 0.15s;
}
.tc-web-item:hover .tc-web-ext {
  opacity: 1;
}

/* 查看全部按钮 */
.tc-web-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 6px;
  padding: 7px 0;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  font-size: 11.5px;
  cursor: pointer;
  transition:
    color 0.15s,
    border-color 0.15s,
    background 0.15s;
}
.tc-web-more:hover {
  color: var(--accent-light);
  border-color: var(--border-active);
  background: var(--bg-hover);
}
.tc-web-more .fa-angles-right {
  font-size: 10px;
}

/* ---------- 网页抓取（tc-fetch-*） ---------- */
/* 详情头：标题 + URL + 徽章 */
.tc-fetch-head {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}
.tc-fetch-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tc-fetch-title-row .fa-file-lines {
  font-size: 11px;
  color: var(--accent-light);
}
.tc-fetch-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 380px;
}
.tc-fetch-url {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: #7ddfb0;
  text-decoration: none;
  opacity: 0.9;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-left: 19px;
}
body.light-theme .tc-fetch-url {
  color: #0f7b4f;
}
.tc-fetch-url:hover {
  text-decoration: underline;
}
.tc-badge.ok {
  color: #34d399;
  border-color: rgba(52, 211, 153, 0.25);
}
body.light-theme .tc-badge.ok {
  color: #059669;
  border-color: rgba(5, 150, 105, 0.25);
}
.tc-badge.err {
  color: #f87171;
  border-color: rgba(248, 113, 113, 0.3);
}
body.light-theme .tc-badge.err {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.3);
}

/* 正文预览：连续一段不分段，固定高 + 底部渐隐，不可滚动 */
.tc-fetch-preview {
  position: relative;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--text-secondary);
  background: var(--bg-codeblock);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 12px;
  max-height: 96px;
  overflow: hidden;
}
.tc-fetch-preview::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 42px;
  background: linear-gradient(transparent, var(--bg-codeblock));
  border-radius: 0 0 8px 8px;
  pointer-events: none;
}

/* 按钮行：源网页 + 阅读模式（与搜索「查看全部」同款虚线框） */
.tc-fetch-actions {
  display: flex;
  gap: 8px;
}
.tc-fetch-open {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 7px 0;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  font-size: 11.5px;
  cursor: pointer;
  transition:
    color 0.15s,
    border-color 0.15s,
    background 0.15s;
}
.tc-fetch-open:hover {
  color: var(--accent-light);
  border-color: var(--border-active);
  background: var(--bg-hover);
}
.tc-fetch-open i {
  font-size: 10px;
}

/* 错误态 */
.tc-fetch-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #f87171;
  background: rgba(248, 113, 113, 0.06);
  border: 1px solid rgba(248, 113, 113, 0.2);
  border-radius: 8px;
  padding: 10px 12px;
}
body.light-theme .tc-fetch-error {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.05);
  border-color: rgba(220, 38, 38, 0.2);
}
.tc-fetch-error i {
  padding-top: 2px;
  font-size: 11px;
  flex-shrink: 0;
}

/* 提示条（如 SPA 页面说明） */
.tc-fetch-hint {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-muted);
  background: var(--bg-codeblock);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 12px;
}
.tc-fetch-hint i {
  padding-top: 2px;
  font-size: 10.5px;
  flex-shrink: 0;
  color: var(--accent-light);
}
</style>
