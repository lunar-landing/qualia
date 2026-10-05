<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { OUT_LIMIT, truncate, type ToolArgs } from '@/utils/chip'
import type { AgentStep } from '@/types'

/** 对照视角详情（edit：路径头 + 旧文本 / 新文本 diff 块 + 结果行） */
const props = defineProps<{
  step: AgentStep
  result: { ok: boolean; content: string } | null
}>()

const { t } = useI18n()

const args = computed(() => (props.step.toolArgs ?? {}) as ToolArgs)
const path = computed(() => {
  const a = args.value
  for (const k of ['path', 'file_path']) {
    const v = a[k]
    if (typeof v === 'string' && v) return v
  }
  return ''
})
const oldText = computed(() => String(args.value.old_text ?? ''))
const newText = computed(() => String(args.value.new_text ?? ''))
</script>

<template>
  <div class="tc-file-head">
    <i class="fas fa-file-lines"></i>
    <span class="tc-path">{{ path || t('tool.unknownPath') }}</span>
    <span v-if="args.replace_all" class="tc-badge">{{ t('tool.replaceAll') }}</span>
  </div>

  <div class="tc-diff">
    <div class="tc-diff-del">{{ truncate(oldText, OUT_LIMIT) }}</div>
    <div class="tc-diff-ins">{{ truncate(newText, OUT_LIMIT) }}</div>
  </div>

  <div v-if="!result" class="tc-running">
    <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('common.running') }}</span>
  </div>
  <div v-else class="tc-result">
    <i class="fas fa-check"></i>
    <span>{{ truncate(result.content, 200) }}</span>
  </div>
</template>
