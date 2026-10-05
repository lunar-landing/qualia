<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  argStr,
  isSkillError,
  OUT_LIMIT,
  parseSkillArgs,
  parseSkillLoad,
  parseSkillSelectorOutput,
  shortScriptName,
  truncate,
  type SkillLoadRecord,
  type ToolArgs,
} from '@/utils/chip'
import { buildSkillPage } from '@/utils/skillPage'
import { usePreviewStore } from '@/stores/preview'
import { useTheme } from '@/composables/theme'
import type { AgentStep } from '@/types'

/**
 * 技能视角详情（skill-loader / skill-script-runner / skill-reference-reader / skill-selector）。
 * loader 只展示摘要，完整技能说明通过「查看技能详情」在浏览器 Tab 打开
 */
const props = defineProps<{
  step: AgentStep
  result: { ok: boolean; content: string } | null
}>()

const preview = usePreviewStore()
const { isLight } = useTheme()
const { t } = useI18n()

const args = computed(() => (props.step.toolArgs ?? {}) as ToolArgs)
const tool = computed(() => props.step.toolName ?? '')
const skillName = computed(() => argStr(args.value, 'skill_name'))

/** 头部主体名：loader=技能名 / runner=剥离前缀脚本名 / reader=文档名 */
const headName = computed(() => {
  const a = args.value
  if (tool.value === 'skill-loader') return argStr(a, 'skill_name')
  if (tool.value === 'skill-script-runner') return shortScriptName(a.script_name, a.skill_name)
  return argStr(a, 'file_name')
})
/** 右侧灰字标注所属技能（loader 自身就是技能主体，不标） */
const srcName = computed(() => (tool.value === 'skill-loader' ? '' : skillName.value))

const output = computed(() => props.result?.content ?? null)

/** loader 解析（null = 未解析出三段结构，回退整段展示） */
const loaded = computed<SkillLoadRecord | null>(() =>
  tool.value === 'skill-loader' && output.value !== null
    ? parseSkillLoad(output.value, skillName.value)
    : null,
)

/** script-runner 入参行 */
const argRow = computed(() =>
  tool.value === 'skill-script-runner' ? parseSkillArgs(args.value.arguments) : null,
)

/** 输出内容块错误态（「错误：」开头） */
const isErr = computed(() => isSkillError(output.value))

/** selector 技能列表（null = 空列表/非 JSON，回退整段展示） */
const selectorSkills = computed(() =>
  tool.value === 'skill-selector' && output.value !== null
    ? parseSkillSelectorOutput(output.value)
    : null,
)

/** loader 摘要暂存 → 自包含详情页（浏览器 Tab 打开，对齐旧 SKILL_STORE + openSkillDetail） */
function openDetail() {
  const rec = loaded.value
  if (!rec) return
  preview.openHtml(buildSkillPage({ name: skillName.value, ...rec }, isLight.value))
}

const OUT = OUT_LIMIT
</script>

<template>
  <!-- selector 用专属头部，其余三件套统一头部行（图标 + 主体名 + 所属技能灰字） -->
  <div v-if="tool === 'skill-selector'" class="sk-head">
    <i class="fas fa-list"></i>
    <span class="sk-name">{{ t('skill.availableList') }}</span>
  </div>
  <div v-else class="sk-head">
    <i class="fas" :class="tool === 'skill-loader' ? 'fa-shapes' : tool === 'skill-script-runner' ? 'fa-bolt' : 'fa-book-open'"></i>
    <span class="sk-name">{{ headName }}</span>
    <span v-if="srcName" class="sk-src">{{ srcName }}</span>
  </div>

  <!-- script-runner：入参行 -->
  <div v-if="argRow && (argRow.pairs.length || argRow.raw)" class="sk-args">
    <template v-if="argRow.pairs.length">
      <span v-for="p in argRow.pairs" :key="p.key"><b>{{ p.key }}</b>{{ p.value }}</span>
    </template>
    <template v-else><span>{{ argRow.raw }}</span></template>
  </div>

  <!-- 执行中 -->
  <div v-if="output === null" class="tc-running">
    <i class="fas fa-circle-notch fa-spin"></i><span>{{ t('common.running') }}</span>
  </div>

  <template v-else>
    <!-- loader：摘要（描述 + 资源清单 + 查看详情入口）；解析失败回退整段 -->
    <template v-if="tool === 'skill-loader'">
      <template v-if="loaded">
        <div v-if="loaded.desc" class="sk-desc">{{ loaded.desc }}</div>
        <div v-if="loaded.meta.length" class="sk-meta">
          <span v-for="m in loaded.meta" :key="m.name"><i class="fas" :class="m.icon"></i>{{ m.name }}</span>
        </div>
        <button class="sk-more" @click="openDetail">
          <i class="fas fa-angles-right"></i> {{ t('skill.viewDetail') }}
        </button>
      </template>
      <div v-else class="sk-body" :class="{ err: isErr }">{{ truncate(output, OUT) }}</div>
    </template>

    <!-- script-runner：折叠输出（出错时默认展开不遮错误） -->
    <details v-else-if="tool === 'skill-script-runner'" class="sk-fold" :open="isErr">
      <summary>
        <i class="fas fa-chevron-right"></i>{{ t('skill.runResult') }}<span v-if="isErr" class="sk-fold-err">{{ t('skill.errored') }}</span>
      </summary>
      <div class="sk-body" :class="{ err: isErr }">{{ truncate(output, OUT) }}</div>
    </details>

    <!-- selector：技能卡片列表；空/非 JSON 回退整段 -->
    <template v-else-if="tool === 'skill-selector'">
      <div v-if="selectorSkills" class="sk-skills">
        <div v-for="s in selectorSkills" :key="s.name" class="sk-skill-item">
          <span class="sk-skill-icon"><i class="fas fa-shapes"></i></span>
          <div class="sk-skill-info">
            <span class="sk-skill-name">{{ s.name }}</span>
            <span v-if="s.description" class="sk-skill-desc">{{ s.description }}</span>
          </div>
        </div>
      </div>
      <div v-else class="sk-body">{{ output || t('skill.empty') }}</div>
    </template>

    <!-- reference-reader：整段内容块 -->
    <div v-else class="sk-body" :class="{ err: isErr }">{{ truncate(output, OUT) }}</div>
  </template>
</template>
