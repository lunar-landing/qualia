<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfigStore } from '@/stores/config'

const { t } = useI18n()
const config = useConfigStore()
const open = ref(false)

function onDocClick() {
  open.value = false
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

// 选中模型在新列表中不存在时回退默认（模型被删改名的保存场景，对齐旧 refreshModelSelector）
watch(
  () => config.models,
  (models) => {
    if (models.length && !models.some((m) => m.name === config.selectedModel)) {
      config.selectModel(config.defaultModel)
    }
  },
  { immediate: true },
)

const displayName = computed(() => config.selectedModel || t('model.notConfigured'))

function pick(name: string) {
  config.selectModel(name)
  open.value = false
}
</script>

<template>
  <!-- 点击切换下拉，点击外部关闭（document 委托；自身 @click.stop 防误关） -->
  <div class="model-selector" :class="{ open }" @click.stop="open = !open">
    <span class="model-icon"></span>
    <span class="model-name">{{ displayName }}</span>
    <i class="fas fa-chevron-down model-arrow"></i>
    <div class="model-dropdown" v-show="open">
      <div
        v-for="m in config.models"
        :key="m.name"
        class="model-dropdown-item"
        :class="{ active: m.name === config.selectedModel }"
        @click.stop="pick(m.name)"
      >
        <span>
          {{ m.name }}<span v-if="m.name === config.defaultModel" class="default-badge">{{ t('model.defaultBadge') }}</span>
        </span>
        <i class="fas fa-check check-icon"></i>
      </div>
    </div>
  </div>
</template>
