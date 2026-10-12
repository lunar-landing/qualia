<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MaterialNode } from '@/api/materials'
import { formatWordCount } from '@/utils/format'
import { useMaterialsStore } from '@/stores/materials'

/**
 * 资料树递归节点：折叠状态记忆于 store（collapsedPaths，随库重置），过滤态强制展开；
 * 行操作收敛到右键菜单（contextmenu 上抛给详情页统一渲染），选中态带左缘竖条。
 */
const props = defineProps<{ node: MaterialNode; depth: number; filtering?: boolean }>()
const emit = defineEmits<{
  open: [node: MaterialNode]
  menu: [node: MaterialNode, e: MouseEvent]
}>()

const { t } = useI18n()
const materials = useMaterialsStore()

const expanded = computed(() => props.filtering === true || !materials.collapsedPaths.has(props.node.path))

function onRowClick() {
  materials.selectedPath = props.node.path
  if (props.node.type === 'file') emit('open', props.node)
  else materials.toggleCollapsed(props.node.path)
}

/** 右键即选中（菜单动作作用于该行），菜单渲染由详情页统一承担 */
function onContextmenu(e: MouseEvent) {
  e.preventDefault()
  materials.selectedPath = props.node.path
  emit('menu', props.node, e)
}
</script>

<template>
  <div>
    <div
      class="tree-row"
      :class="{ file: node.type === 'file', on: materials.selectedPath === node.path }"
      :style="{ paddingLeft: 9 + depth * 20 + 'px' }"
      @click="onRowClick"
      @contextmenu="onContextmenu"
    >
      <i class="caret fas" :class="node.type === 'folder' ? (expanded ? 'fa-chevron-down' : 'fa-chevron-right') : ''"></i>
      <i class="fic fas" :class="node.type === 'folder' ? (expanded ? 'fa-folder-open' : 'fa-folder') : 'fa-file-lines'"></i>
      <span class="nm2" :title="node.name">{{ node.name }}</span>

      <!-- 字数：主信息名称之后的轻元信息 -->
      <span v-if="node.type === 'file' && node.words" class="words">{{ formatWordCount(node.words) }}</span>
    </div>

    <template v-if="node.type === 'folder' && expanded">
      <MaterialTreeNode
        v-for="child in node.children ?? []"
        :key="child.path"
        :node="child"
        :depth="depth + 1"
        :filtering="filtering"
        @open="emit('open', $event)"
        @menu="(n, e) => emit('menu', n, e)"
      />
      <div v-if="(node.children ?? []).length === 0" class="tree-sub-empty" :style="{ paddingLeft: 29 + depth * 20 + 'px' }">
        {{ t('materials.folderEmpty') }}
      </div>
    </template>
  </div>
</template>

<script lang="ts">
// 递归自引用（script setup 需显式 name 供模板递归）
export default { name: 'MaterialTreeNode' }
</script>

<style scoped>
/* 固定行高；选中行用背景色标识，与 hover 拉开层次 */
.tree-row {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  margin: 2px 0; /* 行间留缝，hover/选中背景块不粘连（flex 不折叠，相邻行间隔 4px） */
  padding: 0 9px;
  border-radius: 8px;
  font-size: 12.5px;
  cursor: default;
  user-select: none;
}
.tree-row.file {
  cursor: pointer;
}
.tree-row:hover {
  background: var(--bg-hover);
}
.tree-row.on {
  background: var(--bg-active);
}
.caret {
  width: 12px;
  color: var(--text-secondary);
  font-size: 9px;
  flex-shrink: 0;
  text-align: center;
}
.fic {
  width: 16px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
  flex-shrink: 0;
}
.nm2 {
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.words {
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10.5px;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.tree-sub-empty {
  padding: 4px 9px;
  font-size: 11px;
  color: var(--text-secondary);
}
</style>
