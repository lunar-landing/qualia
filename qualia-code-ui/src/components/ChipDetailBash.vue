<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { OUT_LIMIT, truncate, type ToolArgs } from '@/utils/chip'
import type { AgentStep } from '@/types'

/** 终端视角详情（bash：$ 命令 + 输出） */
const props = defineProps<{
  step: AgentStep
  result: { ok: boolean; content: string } | null
}>()

const { t } = useI18n()

const command = computed(() => String(((props.step.toolArgs ?? {}) as ToolArgs).command ?? ''))
const OUT = OUT_LIMIT
</script>

<template>
  <div class="tc-term">
    <div class="tc-term-cmd">{{ command }}</div>
    <div v-if="!result" class="tc-term-out tc-term-wait">
      <i class="fas fa-circle-notch fa-spin"></i> {{ t('common.running') }}
    </div>
    <div v-else-if="result.content" class="tc-term-out">{{ truncate(result.content, OUT) }}</div>
  </div>
</template>