<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { argStr, OUT_LIMIT, parseEditorLines, truncate, type ToolArgs } from '@/utils/chip'
import type { AgentStep } from '@/types'

/** 文件视角详情（read / write / delete：路径头 + 代码块 / 编辑器块 / 结果行） */
const props = defineProps<{
  step: AgentStep
  result: { ok: boolean; content: string } | null
}>()

const { t } = useI18n()

const args = computed(() => (props.step.toolArgs ?? {}) as ToolArgs)
const tool = computed(() => props.step.toolName ?? '')
const isRead = computed(() => tool.value === 'read')
const path = computed(() => argStr(args.value, 'path', 'file_path'))

/** 路径头右侧徽标：read 行号区间 / write 模式 / delete 固定「删除」 */
const badges = computed<string[]>(() => {
  const a = args.value
  if (isRead.value) {
    return a.begin || a.end ? [`L${a.begin || 1}${a.end ? '-' + a.end : '+'}`] : []
  }
  if (tool.value === 'write') {
    const modeMap: Record<string, string> = {
      overwrite: t('tool.modeOverwrite'),
      append: t('tool.modeAppend'),
      insert: t('tool.modeInsert'),
    }
    const mode = modeMap[String(a.mode ?? '')]
    return mode ? [mode] : []
  }
  return [t('tool.delete')]
})

/** read 输出解析为编辑器行；null 回退普通代码块 */
const readLines = computed(() =>
  isRead.value && props.result ? parseEditorLines(props.result.content) : null,
)
const numWidth = computed(() => (readLines.value?.[readLines.value.length - 1]?.num.length ?? 1) + 1)
const writeContent = computed(() => (tool.value === 'write' ? String(args.value.content ?? '') : ''))
const OUT = OUT_LIMIT
</script>

<template>
  <div class="tc-file-head">
    <i class="fas fa-file-lines"></i>
    <span class="tc-path">{{ path || t('tool.unknownPath') }}</span>
    <span v-for="b in badges" :key="b" class="tc-badge">{{ b }}</span>
  </div>

  <!-- read：编辑器风格块（行号列 + 代码列）；解析失败回退普通代码块 -->
  <template v-if="isRead">
    <div v-if="!result" class="tc-running">
      <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('common.running') }}</span>
    </div>
    <div v-else-if="readLines" class="tc-editor">
      <div v-for="(l, i) in readLines" :key="i" class="tc-ed-line">
        <span class="tc-ed-num" :style="{ width: numWidth + 'ch' }">{{ l.num }}</span>
        <span class="tc-ed-txt">{{ l.txt || ' ' }}</span>
      </div>
    </div>
    <div v-else class="tc-code">{{ truncate(result?.content, OUT) }}</div>
  </template>

  <!-- write：写入内容代码块 + 结果行；delete：结果行 -->
  <template v-else>
    <div v-if="writeContent" class="tc-code">{{ writeContent }}</div>
    <div v-if="!result" class="tc-running">
      <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('common.running') }}</span>
    </div>
    <div v-else class="tc-result">
      <i class="fas fa-check"></i>
      <span>{{ truncate(result.content, 200) }}</span>
    </div>
  </template>
</template>
