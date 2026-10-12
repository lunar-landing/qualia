<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import TreeNode from './TreeNode.vue'

/**
 * 文件树根层：根目录列表由 workspace store 持有，子目录由 TreeNode 懒加载。
 * 刷新语义对齐旧 loadTree——全量重渲染（所有展开收起）：store.fileTree 每次变化递增 epoch，
 * 根层容器以 :key="epoch" 强制重建子树。
 */
const ws = useWorkspaceStore()
const sessionStore = useSessionStore()

const emit = defineEmits<{ open: [path: string] }>()

const epoch = ref(0)
watch(
  () => ws.fileTree,
  () => {
    epoch.value++
  },
)

// 按会话权威解析工作区（绑定 → 项目根；未绑定 → 派生沙箱），沙箱空目录也正常呈现空树；
// 监听会话 id：绑定态会话间切换时 FileTree 不重挂（面板 v-if 恒真），必须显式重载
function reload() {
  ws.loadFileTree(sessionStore.currentSessionId).catch(() => undefined)
}
onMounted(reload)
watch(() => sessionStore.currentSessionId, reload)
</script>

<template>
  <div :key="epoch">
    <TreeNode v-for="f in ws.fileTree" :key="f.path" :file="f" :depth="0" @open="emit('open', $event)" />
  </div>
</template>
