<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { OUT_LIMIT, truncate, WEB_SEARCH_SOURCES, type ToolArgs } from '@/utils/chip'
import ChipDetailFile from '@/components/ChipDetailFile.vue'
import ChipDetailEdit from '@/components/ChipDetailEdit.vue'
import ChipDetailBash from '@/components/ChipDetailBash.vue'
import ChipDetailSearch from '@/components/ChipDetailSearch.vue'
import ChipDetailSkill from '@/components/ChipDetailSkill.vue'
import ChipDetailWeb from '@/components/ChipDetailWeb.vue'
import type { AgentStep } from '@/types'

/**
 * chip 详情分发器：按工具类型选择差异化视图；
 * 无专属渲染器的工具回退「参数 JSON + 输出」（联网搜索族 / web_fetch 走 ChipDetailWeb）
 */
const props = defineProps<{
  step: AgentStep
  result: { ok: boolean; content: string } | null
}>()

const { t } = useI18n()

const SKILL_TOOLS = new Set(['skill-loader', 'skill-script-runner', 'skill-reference-reader', 'skill-selector'])

const kind = computed(() => {
  const t = props.step.toolName ?? ''
  if (t === 'read' || t === 'write' || t === 'delete') return 'file'
  if (t === 'edit') return 'edit'
  if (t === 'bash') return 'bash'
  if (t === 'grep' || t === 'glob') return 'search'
  if (SKILL_TOOLS.has(t)) return 'skill'
  if (t in WEB_SEARCH_SOURCES || t === 'web_fetch') return 'web'
  return 'fallback'
})

const argJson = computed(() => {
  const args = (props.step.toolArgs ?? {}) as ToolArgs
  return Object.keys(args).length ? JSON.stringify(args, null, 2) : t('tool.noArgs')
})
</script>

<template>
  <ChipDetailFile v-if="kind === 'file'" :step="step" :result="result" />
  <ChipDetailEdit v-else-if="kind === 'edit'" :step="step" :result="result" />
  <ChipDetailBash v-else-if="kind === 'bash'" :step="step" :result="result" />
  <ChipDetailSearch v-else-if="kind === 'search'" :step="step" :result="result" />
  <ChipDetailSkill v-else-if="kind === 'skill'" :step="step" :result="result" />
  <ChipDetailWeb v-else-if="kind === 'web'" :step="step" :result="result" />

  <!-- 通用回退：参数 JSON + 输出 -->
  <template v-else>
    <div class="tc-sec">
      <div class="tc-label">{{ step.toolName || t('tool.fallback') }} · {{ t('tool.params') }}</div>
      <div class="tc-code">{{ argJson }}</div>
    </div>
    <div class="tc-sec">
      <div class="tc-label">{{ t('tool.output') }}</div>
      <div v-if="!result" class="tc-running">
        <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('common.running') }}</span>
      </div>
      <div v-else class="tc-code">{{ truncate(result.content, OUT_LIMIT) }}</div>
    </div>
  </template>
</template>
