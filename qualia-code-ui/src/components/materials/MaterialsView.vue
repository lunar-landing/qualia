<script setup lang="ts">
import { useMaterialsStore } from '@/stores/materials'
import MaterialListPage from './MaterialListPage.vue'
import MaterialDetailPage from './MaterialDetailPage.vue'

/**
 * 资料整栏视图（聊天区 + 工作区面板之上的覆盖层，双页结构）：
 * 列表页（全部资料卡片）⇄ 详情页（文章树 + 预览阅读区）。
 * 入口在侧栏底部「设置」上方；toast 汇总上传结果。
 */
const materials = useMaterialsStore()
</script>

<template>
  <section v-if="materials.open" class="materials-view">
    <MaterialListPage v-if="!materials.currentId" />
    <MaterialDetailPage v-else />

    <!-- 右下角结果 toast（上传完成 / 失败汇总） -->
    <Transition name="toast">
      <div v-if="materials.toast" class="materials-toast" :class="{ fail: materials.toast.fail }">
        <i class="fas" :class="materials.toast.fail ? 'fa-circle-exclamation' : 'fa-circle-check'"></i>
        <div class="tt">
          <div class="t1">{{ materials.toast.text }}</div>
          <div v-if="materials.toast.sub" class="t2">{{ materials.toast.sub }}</div>
        </div>
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.materials-view {
  position: absolute;
  inset: 0;
  z-index: 45;
  background: var(--bg-app);
}

/* toast */
.materials-toast {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 1300;
  display: flex;
  align-items: flex-start;
  gap: 9px;
  max-width: 420px;
  padding: 11px 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-app);
  box-shadow: var(--shadow);
  font-size: 12px;
}
.materials-toast > i {
  color: var(--success);
  margin-top: 1px;
}
.materials-toast.fail > i {
  color: var(--error);
}
.materials-toast .t1 {
  color: var(--text-primary);
  font-weight: 600;
}
.materials-toast .t2 {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.6;
  word-break: break-all;
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
