<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMaterialsStore } from '@/stores/materials'
import { formatWordCount, relTime } from '@/utils/format'
import type { MaterialInfo } from '@/api/materials'

/**
 * 资料列表页（页面 A）：三列留白卡片，底部元信息行（篇数/字数/更新时间）。
 * 头部提供本地搜索与排序（库数量级小，无需后端）；新建与重命名共用弹窗，删除走 confirm 二次确认。
 * 视觉语言走 --mat-* 模块 token：靠底色与发丝线分层，不画框、不用重阴影。
 */
const { t } = useI18n()
const materials = useMaterialsStore()

// ===== 检索与排序（纯前端） =====
type SortKey = 'recent' | 'name' | 'words'
const kw = ref('')
const sortBy = ref<SortKey>('recent')
const sortOpen = ref(false)
const SORTS = computed(() => [
  { key: 'recent' as SortKey, label: t('materials.sortRecent') },
  { key: 'name' as SortKey, label: t('materials.sortName') },
  { key: 'words' as SortKey, label: t('materials.sortWords') },
])
const sortLabel = computed(() => SORTS.value.find((s) => s.key === sortBy.value)?.label ?? '')

const visible = computed(() => {
  const q = kw.value.trim().toLowerCase()
  const list = q
    ? materials.list.filter(
        (kb) => kb.name.toLowerCase().includes(q) || (kb.description ?? '').toLowerCase().includes(q),
      )
    : [...materials.list]
  const order = new Intl.Collator('zh-Hans-CN')
  list.sort((a, b) => {
    if (sortBy.value === 'name') return order.compare(a.name, b.name)
    if (sortBy.value === 'words') return b.totalChars - a.totalChars
    return b.updatedAt - a.updatedAt
  })
  return list
})

// ===== 新建 / 重命名共用弹窗：editing=null 为新建态 =====
const dialogOpen = ref(false)
const editing = ref<MaterialInfo | null>(null)
const formName = ref('')
const formDesc = ref('')
const submitting = ref(false)

function openCreate() {
  editing.value = null
  formName.value = ''
  formDesc.value = ''
  dialogOpen.value = true
}

function openEdit(kb: MaterialInfo) {
  editing.value = kb
  formName.value = kb.name
  formDesc.value = kb.description
  dialogOpen.value = true
}

async function submitDialog() {
  if (!formName.value.trim() || submitting.value) return
  submitting.value = true
  try {
    if (editing.value) {
      await materials.updateKb(editing.value.id, formName.value.trim(), formDesc.value.trim())
    } else {
      await materials.createKb(formName.value.trim(), formDesc.value.trim())
    }
    dialogOpen.value = false
  } catch (e) {
    materials.showToast(e instanceof Error ? e.message : String(e), undefined, true)
  } finally {
    submitting.value = false
  }
}

async function removeKb(kb: MaterialInfo) {
  if (!window.confirm(t('materials.deleteKbConfirm', { name: kb.name }))) return
  try {
    await materials.deleteKb(kb.id)
  } catch (e) {
    materials.showToast(e instanceof Error ? e.message : String(e), undefined, true)
  }
}

function updatedText(kb: MaterialInfo): string {
  return relTime(new Date(kb.updatedAt).toISOString())
}
</script>

<template>
  <div class="list-page">
    <div class="list-pad">
      <!-- 页头：标题 + 计数 + 搜索 + 排序 + 唯一实心主操作 -->
      <div class="list-head">
        <h2>{{ t('materials.title') }}</h2>
        <span v-if="materials.list.length" class="kb-count">{{ materials.list.length }}</span>
        <div class="head-tools">
          <label class="kb-search">
            <i class="fas fa-magnifying-glass"></i>
            <input v-model="kw" type="text" :placeholder="t('materials.searchKb')" />
          </label>
          <div class="kb-sort">
            <button class="btn ghost" @click="sortOpen = !sortOpen">
              <i class="fas fa-arrow-down-short-wide"></i>{{ sortLabel }}
            </button>
            <template v-if="sortOpen">
              <div class="sort-backdrop" @click="sortOpen = false"></div>
              <div class="sort-menu">
                <button
                  v-for="opt in SORTS"
                  :key="opt.key"
                  :class="{ on: opt.key === sortBy }"
                  @click="((sortBy = opt.key), (sortOpen = false))"
                >
                  <i class="fas fa-check"></i>{{ opt.label }}
                </button>
              </div>
            </template>
          </div>
          <button class="btn solid" @click="openCreate">
            <i class="fas fa-plus"></i>{{ t('materials.create') }}
          </button>
        </div>
      </div>

      <!-- 有库：留白卡片网格 -->
      <div v-if="materials.list.length > 0" class="list-scroll">
        <div v-if="visible.length === 0" class="search-empty">{{ t('materials.searchEmpty') }}</div>
        <div v-else class="kb-grid">
          <div v-for="kb in visible" :key="kb.id" class="lib" @click="materials.enterKb(kb.id)">
            <div class="lib-hd">
              <span class="sq"><i class="fas fa-book-open"></i></span>
              <span class="nm" :title="kb.name">{{ kb.name }}</span>
              <span class="lib-acts">
                <button :title="t('materials.editKb')" @click.stop="openEdit(kb)">
                  <i class="fas fa-pen"></i>
                </button>
                <button class="danger" :title="t('common.delete')" @click.stop="removeKb(kb)">
                  <i class="fas fa-trash-can"></i>
                </button>
              </span>
            </div>
            <div class="lib-ds" :class="{ none: !kb.description }">
              {{ kb.description || t('materials.noDesc') }}
            </div>
            <div class="lib-ft">
              <span class="num">{{ kb.articleCount }}</span> {{ t('materials.unitArticles') }}
              <span class="sep"></span>
              <span class="num">{{ formatWordCount(kb.totalChars) }}</span> {{ t('materials.unitChars') }}
              <span class="sep"></span>
              <span>{{ updatedText(kb) }}</span>
              <i class="fas fa-arrow-right go"></i>
            </div>
          </div>
        </div>
      </div>

      <!-- 空态引导 -->
      <div v-else class="list-empty">
        <div class="inner">
          <div class="ic"><i class="fas fa-book-open"></i></div>
          <div class="t1">{{ t('materials.emptyTitle') }}</div>
          <div class="steps">
            <div><i class="fas fa-1"></i>{{ t('materials.emptyStep1') }}</div>
            <div><i class="fas fa-2"></i>{{ t('materials.emptyStep2') }}</div>
            <div><i class="fas fa-3"></i>{{ t('materials.emptyStep3') }}</div>
          </div>
          <div class="hint">{{ t('materials.emptyCtaHint', { action: t('materials.create') }) }}</div>
          <div class="future">{{ t('materials.emptyFuture') }}</div>
        </div>
      </div>
    </div>

    <!-- 新建 / 重命名弹窗 -->
    <Teleport to="body">
      <div v-if="dialogOpen" class="dlg-mask" @click.self="dialogOpen = false">
        <div class="dlg">
          <div class="dlg-title">{{ editing ? t('materials.editKb') : t('materials.createTitle') }}</div>
          <label class="dlg-label">{{ t('materials.nameLabel') }}</label>
          <input v-model="formName" class="dlg-input" :placeholder="t('materials.namePlaceholder')" @keyup.enter="submitDialog" />
          <label class="dlg-label">{{ t('materials.descLabel') }}</label>
          <input v-model="formDesc" class="dlg-input" :placeholder="t('materials.descPlaceholder')" @keyup.enter="submitDialog" />
          <div class="dlg-acts">
            <button class="btn ghost" @click="dialogOpen = false">{{ t('common.cancel') }}</button>
            <button class="btn solid" :disabled="!formName.trim() || submitting" @click="submitDialog">
              {{ t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.list-page {
  height: 100%; /* 父级 .materials-view 非 flex 容器，用显式高度铺满 */
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--mat-stage);
  color: var(--mat-ink);
}
.list-pad {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 26px 30px 0;
}

/* ===== 页头 ===== */
.list-head {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 22px;
  flex-shrink: 0;
}
.list-head h2 {
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.2;
}
.kb-count {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 11.5px;
  color: var(--mat-faint);
  margin-bottom: 4px;
}
.head-tools {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.kb-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 30px;
  padding: 0 11px;
  border: 1px solid var(--mat-line-2);
  border-radius: 9px;
  background: var(--mat-surface-2);
  color: var(--mat-faint);
  font-size: 12px;
  width: 200px;
  transition: border-color 0.16s ease, background 0.16s ease;
}
.kb-search:focus-within {
  border-color: var(--mat-accent);
  background: var(--mat-card);
  box-shadow: 0 0 0 3px var(--mat-accent-wash);
}
.kb-search i {
  font-size: 10px;
}
.kb-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--mat-ink);
  font: inherit;
}
.kb-search input::placeholder {
  color: var(--mat-faint);
}
.kb-sort {
  position: relative;
}
.sort-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
}
.sort-menu {
  position: absolute;
  z-index: 21;
  top: calc(100% + 4px);
  right: 0;
  min-width: 132px;
  padding: 4px;
  border: 1px solid var(--mat-line);
  border-radius: 10px;
  background: var(--mat-card);
  box-shadow: var(--mat-lift);
}
.sort-menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 9px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--mat-ink-2);
  font-size: 12px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.14s ease;
}
.sort-menu button i {
  font-size: 9px;
  width: 9px;
  color: transparent;
  flex-shrink: 0;
}
.sort-menu button:hover {
  background: var(--mat-wash);
}
.sort-menu button.on {
  color: var(--mat-ink);
  font-weight: 600;
}
.sort-menu button.on i {
  color: var(--mat-accent);
}

/* ===== 按钮（幽灵 / 描边 / 实心） ===== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 13px;
  border: none;
  border-radius: 9px;
  font-size: 12.5px;
  font-family: inherit;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}
.btn i {
  font-size: 10.5px;
  color: var(--mat-mut);
}
.btn.ghost {
  background: transparent;
  color: var(--mat-ink-2);
}
.btn.ghost:hover {
  background: var(--mat-wash);
  color: var(--mat-ink);
}
.btn.solid {
  background: var(--mat-ink);
  color: var(--mat-card);
  font-weight: 600;
}
.btn.solid i {
  color: var(--mat-card);
  opacity: 0.82;
}
.btn.solid:hover {
  background: var(--mat-ink-2);
}
.btn.solid:disabled {
  opacity: 0.5;
  cursor: default;
}

/* ===== 卡片网格 ===== */
.list-scroll {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 30px;
}
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(258px, 1fr));
  gap: 14px;
}
.lib {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 18px 18px 0;
  border: 1px solid var(--mat-line);
  border-radius: 14px;
  background: var(--mat-card);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.lib:hover {
  border-color: var(--mat-line-2);
  box-shadow: var(--mat-lift);
}
.lib-hd {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}
.sq {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  flex-shrink: 0;
  background: var(--mat-wash);
  color: var(--mat-ink-2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: background 0.18s ease;
}
.lib:hover .sq {
  background: var(--mat-wash-2);
}
.nm {
  flex: 1;
  min-width: 0;
  margin-top: 2px;
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lib-acts {
  display: flex;
  gap: 1px;
  flex-shrink: 0;
  opacity: 0;
  transform: translateX(-3px);
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.lib:hover .lib-acts {
  opacity: 1;
  transform: none;
}
.lib-acts button {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--mat-faint);
  border-radius: 7px;
  font-size: 10px;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}
.lib-acts button:hover {
  background: var(--mat-wash);
  color: var(--mat-ink);
}
.lib-acts button.danger:hover {
  color: var(--error);
  background: color-mix(in srgb, var(--error) 8%, transparent);
}
.lib-ds {
  margin: 8px 0 16px;
  min-height: 41px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--mat-mut);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.lib-ds.none {
  color: var(--mat-faint);
}
.lib-ft {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 0 12px;
  border-top: 1px solid var(--mat-line);
  font-size: 11.5px;
  color: var(--mat-faint);
}
.lib-ft .num {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  color: var(--mat-ink-2);
  font-weight: 500;
}
.lib-ft .sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--mat-line-2);
}
.lib-ft .go {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--mat-faint);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.lib:hover .lib-ft .go {
  opacity: 1;
  transform: none;
  color: var(--mat-accent);
}
.search-empty {
  padding: 40px 0;
  text-align: center;
  font-size: 12.5px;
  color: var(--mat-faint);
}

/* ===== 空态（沿用既有文案与结构） ===== */
.list-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.list-empty .inner {
  text-align: center;
  max-width: 400px;
}
.list-empty .ic {
  font-size: 34px;
  color: var(--mat-faint);
  margin-bottom: 16px;
}
.list-empty .t1 {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
.list-empty .steps {
  text-align: left;
  display: inline-block;
  margin: 0 0 20px;
  font-size: 12px;
  color: var(--mat-mut);
  line-height: 2.1;
}
.list-empty .steps i {
  width: 20px;
  color: var(--mat-faint);
  font-size: 11px;
}
.list-empty .future {
  margin-top: 14px;
  font-size: 10.5px;
  color: var(--mat-faint);
}
.hint {
  font-size: 12px;
  color: var(--mat-mut);
}

/* ===== 弹窗 ===== */
.dlg-mask {
  position: fixed;
  inset: 0;
  z-index: 1250;
  background: rgba(20, 26, 34, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dlg {
  width: 400px;
  padding: 20px;
  border: 1px solid var(--mat-line);
  border-radius: 14px;
  background: var(--mat-card);
  box-shadow: var(--mat-lift);
}
.dlg-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.015em;
}
.dlg-label {
  display: block;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--mat-ink-2);
  margin: 16px 0 6px;
}
.dlg-input {
  width: 100%;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--mat-line-2);
  border-radius: 9px;
  background: var(--mat-surface-2);
  color: var(--mat-ink);
  font-size: 12.5px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}
.dlg-input::placeholder {
  color: var(--mat-faint);
}
.dlg-input:focus {
  background: var(--mat-card);
  border-color: var(--mat-accent);
  box-shadow: 0 0 0 3px var(--mat-accent-wash);
}
.dlg-acts {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}
</style>
