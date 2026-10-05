<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
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

/** 选中即持久化偏好并收起（config.readOnly 随下次发送经 openStream 透传后端） */
function pick(mode: 'agent' | 'ask') {
  config.setMode(mode)
  open.value = false
}
</script>

<template>
  <!-- 会话模式切换：ghost chip + 上拉菜单。chip 对齐 model-selector，浮层规格对齐 model-dropdown/slash-menu；
       着色规则：当前选中恒为主题强调色（--accent，日间近黑/夜间近白），未选中恒灰。菜单项带一行说明，新手引导内建 -->
  <div class="mode-selector" :class="{ open }" :title="t('input.modeTitle')" @click.stop="open = !open">
    <i class="fas mode-icon" :class="config.readOnly ? 'fa-comment' : 'fa-bolt'"></i>
    <span class="mode-name">{{ config.readOnly ? t('input.modeAsk') : t('input.modeAgent') }}</span>
    <i class="fas fa-chevron-down mode-arrow"></i>
    <div class="mode-dropdown" v-show="open">
      <div class="mode-dropdown-item" :class="{ active: !config.readOnly }" @click.stop="pick('agent')">
        <i class="fas fa-bolt item-icon"></i>
        <span class="item-body">
          <span class="item-name">
            {{ t('input.modeAgent') }}
            <i class="fas fa-check check-icon"></i>
          </span>
          <span class="item-desc">{{ t('input.modeAgentDesc') }}</span>
        </span>
      </div>
      <div class="mode-dropdown-item" :class="{ active: config.readOnly }" @click.stop="pick('ask')">
        <i class="fas fa-comment item-icon"></i>
        <span class="item-body">
          <span class="item-name">
            {{ t('input.modeAsk') }}
            <i class="fas fa-check check-icon"></i>
          </span>
          <span class="item-desc">{{ t('input.modeAskDesc') }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
