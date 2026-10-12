<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMaterialsStore } from '@/stores/materials'
import { formatWordCount, relTime } from '@/utils/format'
import type { MaterialInfo } from '@/api/materials'

/**
 * 资料列表页（页面 A）：整栏卡片网格，展示全部资料
 * （名称 / 描述 / 文章数 / 字数 / 更新时间），点击卡片进入详情页；
 * 新建与重命名共用一个小弹窗，删除走 confirm 二次确认。
 */
const { t } = useI18n()
const materials = useMaterialsStore()

/** 新建 / 重命名共用弹窗：editing=null 为新建态 */
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

function openRename(kb: MaterialInfo) {
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
      await materials.renameKb(editing.value.id, formName.value.trim())
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
  return t('materials.updatedAt', { time: relTime(new Date(kb.updatedAt).toISOString()) })
}
</script>

<template>
  <div class="list-page">
    <div class="list-head">
      <div class="ic"><i class="fas fa-book-open"></i></div>
      <div>
        <div class="t">{{ t('materials.title') }}</div>
        <div class="ds">{{ t('materials.subtitle') }}</div>
      </div>
      <span class="spring"></span>
      <button class="btn primary" @click="openCreate">
        <i class="fas fa-plus"></i> {{ t('materials.create') }}
      </button>
    </div>

    <!-- 有库：卡片网格 -->
    <div v-if="materials.list.length > 0" class="list-scroll">
      <div class="kb-grid">
        <div v-for="kb in materials.list" :key="kb.id" class="kb-tile" @click="materials.enterKb(kb.id)">
          <div class="top">
            <div class="fic"><i class="fas fa-book-open"></i></div>
            <div class="nm" :title="kb.name">{{ kb.name }}</div>
            <span class="acts">
              <button :title="t('common.rename')" @click.stop="openRename(kb)">
                <i class="fas fa-pen"></i>
              </button>
              <button :title="t('common.delete')" class="danger" @click.stop="removeKb(kb)">
                <i class="fas fa-trash-alt"></i>
              </button>
            </span>
          </div>
          <div class="ds">{{ kb.description || t('materials.noDesc') }}</div>
          <div class="mt">
            <span class="m"><i class="fas fa-file-lines"></i>{{ t('materials.articlesCount', { n: kb.articleCount }) }}</span>
            <span class="m"><i class="fas fa-font"></i>{{ formatWordCount(kb.totalChars) }}</span>
            <span class="spring"></span>
            <span>{{ updatedText(kb) }}</span>
            <span class="enter"><i class="fas fa-arrow-right"></i></span>
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

    <!-- 新建 / 重命名弹窗 -->
    <Teleport to="body">
      <div v-if="dialogOpen" class="dlg-mask" @click.self="dialogOpen = false">
        <div class="dlg">
          <div class="dlg-title">{{ editing ? t('materials.renameKb') : t('materials.createTitle') }}</div>
          <label class="dlg-label">{{ t('materials.nameLabel') }}</label>
          <input v-model="formName" class="dlg-input" :placeholder="t('materials.namePlaceholder')" @keyup.enter="submitDialog" />
          <template v-if="!editing">
            <label class="dlg-label">{{ t('materials.descLabel') }}</label>
            <input v-model="formDesc" class="dlg-input" :placeholder="t('materials.descPlaceholder')" @keyup.enter="submitDialog" />
          </template>
          <div class="dlg-acts">
            <button class="btn" @click="dialogOpen = false">{{ t('common.cancel') }}</button>
            <button class="btn primary" :disabled="!formName.trim() || submitting" @click="submitDialog">
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
}
.list-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 28px 16px;
}
.list-head .ic {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  background: var(--bg-active);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.list-head .t {
  font-size: 16.5px;
  font-weight: 700;
}
.list-head .ds {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-secondary);
}
.list-head .spring {
  flex: 1;
}

/* 动作按钮：无边框幽灵（产品惯例），primary 用实心 accent */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 13px;
  border-radius: 8px;
  font-size: 12px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease;
}
.btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.btn.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
  font-weight: 600;
}
.btn.primary:hover {
  background: var(--accent-light);
}
.btn:disabled {
  opacity: 0.5;
  cursor: default;
}

/* 卡片网格 */
.list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 22px 28px 40px;
}
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(272px, 1fr));
  gap: 14px;
}
.kb-tile {
  position: relative;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  padding: 16px 16px 14px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.kb-tile:hover {
  border-color: var(--border-active);
  box-shadow: var(--shadow);
  transform: translateY(-1px);
}
.kb-tile .top {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 10px;
}
.kb-tile .fic {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  flex-shrink: 0;
  background: var(--bg-hover);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}
.kb-tile .nm {
  font-size: 13.5px;
  font-weight: 600;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kb-tile .acts {
  margin-left: auto;
  display: none;
  gap: 2px;
  flex-shrink: 0;
}
.kb-tile:hover .acts {
  display: inline-flex;
}
.kb-tile .acts button {
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 6px;
  font-size: 10.5px;
}
.kb-tile .acts button:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.kb-tile .acts button.danger:hover {
  color: var(--error);
}
.kb-tile .ds {
  font-size: 11.5px;
  color: var(--text-secondary);
  line-height: 1.65;
  height: 38px;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kb-tile .mt {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 10.5px;
  color: var(--text-secondary);
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
}
.kb-tile .mt .m {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.kb-tile .mt .spring {
  flex: 1;
}
.kb-tile .mt .enter {
  color: var(--text-secondary);
  font-size: 10px;
  opacity: 0;
  transition: opacity 0.15s;
}
.kb-tile:hover .enter {
  opacity: 1;
}
.kb-tile.dashed {
  border-style: dashed;
  background: transparent;
  box-shadow: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 148px;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12.5px;
}
.kb-tile.dashed i {
  font-size: 17px;
  color: var(--text-secondary);
}
.kb-tile.dashed:hover {
  color: var(--text-primary);
}

/* 空态 */
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
  color: var(--text-secondary);
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
  color: var(--text-secondary);
  line-height: 2.1;
}
.list-empty .steps i {
  width: 20px;
  color: var(--text-secondary);
  font-size: 11px;
}
.list-empty .future {
  margin-top: 14px;
  font-size: 10.5px;
  color: var(--text-secondary);
}

/* 空态右上入口文字指引（新建唯一入口在列表头） */
.hint {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 新建 / 重命名弹窗 */
.dlg-mask {
  position: fixed;
  inset: 0;
  z-index: 1250;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dlg {
  width: 380px;
  padding: 18px 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-app);
  box-shadow: var(--shadow);
}
.dlg-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 14px;
}
.dlg-label {
  display: block;
  font-size: 11px;
  color: var(--text-secondary);
  margin: 10px 0 5px;
}
.dlg-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 12.5px;
  font-family: inherit;
  outline: none;
}
.dlg-input:focus {
  border-color: var(--border-active);
}
.dlg-acts {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
</style>
