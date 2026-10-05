<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { listWorkspaceFiles } from '@/api/config'
import { useWorkspaceStore, type FileBadgeKind } from '@/stores/workspace'
import { normPath } from '@/utils/steps'
import { fileIcon } from '@/utils/fileMeta'
import type { WorkspaceFileInfo } from '@/types'

/**
 * 文件树节点（递归组件）：目录懒加载展开；文件节点携带活动徽标（R/W/M，来自 store）。
 * 徽标路径与树路径可能一端带工作区前缀，做宽松匹配（平移旧 findTreeItem 规则）
 */
const props = defineProps<{
  file: WorkspaceFileInfo
  depth: number
  /** 当前预览文件路径（侧栏文件树下传，用于选中高亮） */
  activePath?: string
}>()

const emit = defineEmits<{ open: [path: string] }>()

const { t } = useI18n()
const ws = useWorkspaceStore()

const open = ref(false)
const kids = ref<WorkspaceFileInfo[] | null>(null)
const kidError = ref('')
const loading = ref(false)

const badge = computed<FileBadgeKind | null>(() => {
  if (props.file.isDirectory) return null
  const norm = normPath(props.file.path)
  for (const [bp, kind] of Object.entries(ws.fileBadges)) {
    const b = normPath(bp)
    if (b === norm || norm.endsWith('/' + b) || b.endsWith('/' + norm)) return kind
  }
  return null
})

const badgeClass = computed(() =>
  badge.value === 'R' ? 'reading' : badge.value === 'W' ? 'writing' : 'modified',
)

/** 当前预览文件（树内路径与 open 路径同源，精确匹配即可，对齐旧 markActive） */
const isActive = computed(
  () => !props.file.isDirectory && !!props.activePath && normPath(props.file.path) === normPath(props.activePath),
)

function onNodeClick() {
  if (props.file.isDirectory) void toggle()
  else emit('open', props.file.path)
}

async function toggle() {
  open.value = !open.value
  if (!open.value || kids.value || kidError.value || loading.value) return
  // 懒加载子目录
  loading.value = true
  try {
    kids.value = await listWorkspaceFiles(props.file.path)
  } catch (e) {
    kidError.value = e instanceof Error ? e.message : t('treeNode.loadFailed')
  } finally {
    loading.value = false
  }
}

/** 徽标点击：W/M 跳转审查面板定位；R 瞬时态不可点（顺带收起聊天区文件预览，回到聊天上下文） */
function onBadgeClick() {
  if (badge.value === 'R') return
  ws.closeFilePreview()
  ws.panelCollapsed = false
  ws.switchTab('wsChanges')
  ws.requestFileFocus(props.file.path)
}
</script>

<template>
  <div class="tree-item">
    <div
      class="node"
      :class="{ 'tree-active': isActive }"
      :style="{ paddingLeft: depth * 18 + 7 + 'px' }"
      @click="onNodeClick"
    >
      <i
        class="fas"
        :class="file.isDirectory ? (open ? 'fa-folder-open' : 'fa-folder') : fileIcon(file.name)"
      ></i>
      <span :title="file.name">{{ file.name }}</span>
      <span v-if="badge" class="factivity" :class="badgeClass" @click.stop="onBadgeClick">{{ badge }}</span>
    </div>
    <div v-if="file.isDirectory" class="tree-children" :class="{ open }">
      <template v-if="open">
        <div v-if="loading" class="tree-err"><i class="fas fa-circle-notch fa-spin"></i> {{ t('treeNode.loading') }}</div>
        <div v-else-if="kidError" class="tree-err">{{ kidError }}</div>
        <TreeNode
          v-for="k in kids ?? []"
          :key="k.path"
          :file="k"
          :depth="depth + 1"
          :active-path="activePath"
          @open="emit('open', $event)"
        />
      </template>
    </div>
  </div>
</template>
