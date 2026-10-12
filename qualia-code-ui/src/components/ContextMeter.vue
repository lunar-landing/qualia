<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { fmtTokens } from '@/utils/format'
import type { ContextInfo } from '@/types'

/**
 * 上下文信号条（输入框工具行内，与模型选择并列，只读展示）：
 * 6 条等宽垂直横线按上下文占用比例自下而上点亮；hover 浮层显示轮数与全会话
 * token 总消耗。未配置 contextWindow 时按 128k 默认窗口估算点亮程度。
 */
const props = defineProps<{ info: ContextInfo | null }>()

const { t } = useI18n()

/** 未配置窗口时的估算基准（业界常见上下文窗口），tooltip 中注明为估算 */
const DEFAULT_WINDOW = 128000

const windowTokens = computed(() => props.info?.contextWindow ?? DEFAULT_WINDOW)
const pct = computed(() =>
  props.info ? Math.min(100, Math.round((props.info.usedTokens / windowTokens.value) * 100)) : 0,
)
/** 点亮条数：占用比例映射 0~6，有消息时至少点亮 1 条 */
const lit = computed(() => (props.info ? Math.max(1, Math.ceil((pct.value / 100) * 6)) : 0))
</script>

<template>
  <div v-if="info && info.turnCount > 0" class="ctx-gauge">
    <div class="cg-bars">
      <span
        v-for="i in 6"
        :key="i"
        class="cg-bar"
        :class="{ on: i <= lit, hot: pct >= 80 }"
      ></span>
    </div>
    <div class="cg-tip">
      <div class="cg-tip-row cg-tip-strong">{{ t('input.ctxTurns', { n: info.turnCount }) }}</div>
      <div class="cg-tip-row">{{ t('input.ctxTotal', { n: fmtTokens(info.sessionTotalTokens) }) }}</div>
    </div>
  </div>
</template>
