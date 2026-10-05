<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { argStr, OUT_LIMIT, truncate, type ToolArgs } from '@/utils/chip'
import type { AgentStep } from '@/types'

/** 搜索视角详情（grep / glob：模式头 + 徽标 + 匹配结果） */
const props = defineProps<{
  step: AgentStep
  result: { ok: boolean; content: string } | null
}>()

const { t } = useI18n()

const args = computed(() => (props.step.toolArgs ?? {}) as ToolArgs)
const pattern = computed(() => argStr(args.value, 'pattern', 'regex'))

/** 头部徽标：grep 带 path/glob 过滤器，glob 仅 path */
const badges = computed<string[]>(() => {
  const a = args.value
  const list = [a.path, props.step.toolName === 'grep' ? a.glob : null]
  return list.filter((v): v is string => typeof v === 'string' && !!v)
})
const OUT = OUT_LIMIT
</script>

<template>
  <div class="tc-search-head">
    <i class="fas fa-magnifying-glass"></i>
    <code class="tc-pattern">{{ pattern }}</code>
    <span v-for="b in badges" :key="b" class="tc-badge">{{ b }}</span>
  </div>

  <div v-if="!result" class="tc-running">
    <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('common.running') }}</span>
  </div>
  <div v-else class="tc-code">{{ truncate(result.content || t('tool.noMatch'), OUT) }}</div>
</template>
