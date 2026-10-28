<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSchedulesStore } from '@/stores/schedules'
import { updateSchedule } from '@/api/schedules'
import { buildCron, parseCronParts, previewNextRun, fmtRunTime, type SchedMode } from '@/utils/schedule'
import type { ScheduleDef } from '@/types'

/**
 * 新建/编辑定时任务弹窗（遮罩规格对齐工作区切换器）：
 * create = 标题 + 任务内容 + 周期；edit = 标题与周期可改（内容只读展示）。
 * 运行会话由后端每轮新建，弹窗不涉及会话选择。
 * 由任务中心「新建任务」/ 行内铅笔唤起。
 */
const emit = defineEmits<{ close: [] }>()
const { t, locale } = useI18n()
const sched = useSchedulesStore()

const dialog = sched.dialog!
/** 编辑模式的任务（列表中找不到视为已删除，直接关闭） */
const editing = computed<ScheduleDef | null>(() =>
  dialog.mode === 'edit' ? (sched.list.find((d) => d.id === dialog.defId) ?? null) : null,
)
if (dialog.mode === 'edit' && !editing.value) emit('close')

// ===== 表单状态（edit 打开时从任务回填标题与周期选择器） =====
const title = ref('')
const prompt = ref('')
const mode = ref<SchedMode>('daily')
const time = ref('09:30')
const weekday = ref(1)
const customCron = ref('')
const submitting = ref(false)

if (editing.value) {
  title.value = editing.value.title ?? ''
  const parts = parseCronParts(editing.value.cron)
  if (parts) {
    mode.value = parts.mode
    time.value = parts.time
    weekday.value = parts.weekday
  } else {
    mode.value = 'custom'
    customCron.value = editing.value.cron
  }
}

/** 周期展示文本（随模式/时间/周几实时变化；custom 直接用输入的 cron） */
const cronText = computed(() => {
  if (mode.value === 'daily') return t('sched.textDaily', { time: time.value })
  if (mode.value === 'weekday') return t('sched.textWeekday', { time: time.value })
  if (mode.value === 'weekly')
    return t('sched.textWeekly', {
      d: new Date(2024, 0, weekday.value).toLocaleDateString(locale.value, { weekday: 'short' }),
      time: time.value,
    })
  return customCron.value.trim()
})

/** 折算 cron（custom 直取输入） */
const resolvedCron = computed(() =>
  mode.value === 'custom' ? customCron.value.trim() : buildCron(mode.value, time.value, weekday.value),
)

/** 内置模式的下次运行预览（custom 无预览交后端） */
const preview = computed(() => previewNextRun(mode.value, time.value, weekday.value))

const canSubmit = computed(() => {
  if (!resolvedCron.value) return false
  if (editing.value) return !!title.value.trim()
  return !!title.value.trim() && !!prompt.value.trim()
})

async function submit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    if (editing.value) {
      sched.applyUpdated(
        await updateSchedule(editing.value.id, {
          title: title.value.trim(),
          cron: resolvedCron.value,
          cronText: cronText.value,
        }),
      )
    } else {
      await sched.create(title.value.trim(), prompt.value.trim(), resolvedCron.value, cronText.value)
    }
    emit('close')
  } catch (e) {
    alert(e instanceof Error ? e.message : String(e))
  } finally {
    submitting.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="sd-overlay" @click.self="emit('close')">
    <div class="sd-dialog">
      <header class="sd-head">
        <i class="fas fa-clock"></i>
        <h2>{{ editing ? t('sched.editTitle') : t('sched.dialogCreate') }}</h2>
        <button type="button" class="sd-close" :title="t('common.close')" @click="emit('close')">
          <i class="fas fa-times"></i>
        </button>
      </header>

      <div class="sd-body">
        <!-- 任务标题：列表卡面与运行会话名的主信息 -->
        <div class="sd-field">
          <span class="sd-label">{{ t('sched.fieldTitle') }}</span>
          <input
            v-model="title"
            type="text"
            class="sd-input"
            maxlength="60"
            :placeholder="t('sched.titlePh')"
          />
        </div>

        <!-- 任务内容（提示词） -->
        <div class="sd-field">
          <span class="sd-label">{{ t('sched.fieldPrompt') }}</span>
          <textarea
            v-if="!editing"
            v-model="prompt"
            rows="3"
            class="sd-textarea"
            :placeholder="t('sched.promptPh')"
          ></textarea>
          <div v-else class="sd-static">{{ editing.prompt }}</div>
        </div>

        <!-- 执行周期 -->
        <div class="sd-field">
          <span class="sd-label">{{ t('sched.cronLabel') }}</span>
          <div class="sd-modes">
            <button
              v-for="m in (['daily', 'weekday', 'weekly', 'custom'] as SchedMode[])"
              :key="m"
              type="button"
              class="sd-mode"
              :class="{ on: mode === m }"
              @click="mode = m"
            >
              {{ t(`sched.mode.${m}`) }}
            </button>
          </div>
          <!-- 周期面板：控件行 + 摘要行收进同一卡片，行间虚线分隔 -->
          <div class="sd-period">
            <div class="sd-cron-row">
              <template v-if="mode !== 'custom'">
                <input v-model="time" type="time" class="sd-time" />
                <div v-if="mode === 'weekly'" class="sd-weekdays">
                  <button
                    v-for="w in 7"
                    :key="w"
                    type="button"
                    class="sd-wd"
                    :class="{ on: weekday === w }"
                    @click="weekday = w"
                  >
                    {{ new Date(2024, 0, w).toLocaleDateString(locale, { weekday: 'narrow' }) }}
                  </button>
                </div>
              </template>
              <input
                v-else
                v-model="customCron"
                type="text"
                class="sd-cron"
                placeholder="0 30 9 * * *"
                spellcheck="false"
              />
            </div>
            <div class="sd-preview">
              <i class="fas fa-clock"></i>
              <span class="sp-cron">{{ cronText || t('sched.customHint') }}</span>
              <span v-if="preview" class="sp-next">{{ t('sched.nextAt', { t: fmtRunTime(preview) }) }}</span>
            </div>
          </div>
        </div>
      </div>

      <footer class="sd-foot">
        <button type="button" class="sd-cancel" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button type="button" class="sd-submit" :disabled="!canSubmit || submitting" @click="submit">
          {{ editing ? t('sched.save') : t('sched.submitCreate') }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style>
/* 新建/修改定时任务弹窗（sd- 前缀全局，遮罩与卡片规格对齐工作区切换器） */
.sd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: sdFade 0.18s ease;
}
@keyframes sdFade {
  from { opacity: 0; }
  to { opacity: 1; }
}
.sd-dialog {
  width: min(460px, calc(100vw - 48px));
  max-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  box-shadow: var(--shadow);
  overflow: hidden;
  animation: sdPop 0.26s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes sdPop {
  from { opacity: 0; transform: translateY(12px) scale(0.985); }
  to { opacity: 1; transform: none; }
}

.sd-head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 18px 20px 0;
}
.sd-head > i {
  color: var(--success);
  font-size: 14px;
}
.sd-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}
.sd-close {
  margin-left: auto;
  width: 27px;
  height: 27px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.sd-close:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.sd-body {
  padding: 16px 20px 4px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.sd-field {
  display: flex;
  flex-direction: column;
}
.sd-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.2px;
  color: var(--text-muted);
  margin-bottom: 6px;
}
.sd-textarea,
.sd-input,
.sd-time,
.sd-cron {
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--bg-input);
  color: var(--text-primary);
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.sd-textarea:focus,
.sd-input:focus,
.sd-time:focus,
.sd-cron:focus {
  border-color: var(--border-active);
  box-shadow: var(--shadow-input);
}
.sd-input {
  height: 32px;
  padding: 0 11px;
  font-size: 12.5px;
}
.sd-input::placeholder {
  color: var(--text-muted);
}
.sd-textarea {
  padding: 9px 11px;
  font-size: 12.5px;
  line-height: 1.6;
  resize: vertical;
  min-height: 68px;
}
.sd-textarea::placeholder {
  color: var(--text-muted);
}
.sd-static {
  padding: 9px 11px;
  border: 1px dashed var(--border-color);
  border-radius: 9px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 12.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.sd-modes {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 5px;
  margin-bottom: 8px;
}
.sd-mode {
  height: 28px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.13s;
}
.sd-mode:hover {
  border-color: var(--text-muted);
}
.sd-mode.on {
  background: var(--bg-hover);
  border-color: var(--text-muted);
  color: var(--text-primary);
  font-weight: 600;
}
/* 周期面板：控件行与摘要行收进同一卡片，视觉成块 */
.sd-period {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-surface);
  padding: 8px 10px;
}
.sd-cron-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}
.sd-time {
  width: 96px;
  height: 30px;
  padding: 0 8px;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}
.sd-cron {
  flex: 1;
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
.sd-weekdays {
  flex: 1;
  display: flex;
  gap: 5px;
}
.sd-wd {
  flex: 1;
  min-width: 0;
  height: 28px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.13s;
}
.sd-wd:hover {
  border-color: var(--text-muted);
}
.sd-wd.on {
  background: var(--bg-hover);
  border-color: var(--text-muted);
  color: var(--text-primary);
  font-weight: 600;
}
/* 摘要行：面板内第二行，虚线与控件行分隔，两端对齐 */
.sd-preview {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--border-color);
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  line-height: 1;
}
.sd-preview > i {
  color: var(--success);
  font-size: 10px;
  flex-shrink: 0;
}
.sd-preview .sp-cron {
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sd-preview .sp-next {
  margin-left: auto;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: var(--text-muted);
  flex-shrink: 0;
}

.sd-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px 18px;
}
.sd-cancel {
  height: 32px;
  padding: 0 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 550;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}
.sd-cancel:hover {
  color: var(--text-primary);
  border-color: rgba(255, 255, 255, 0.12);
  background: var(--bg-hover);
}
.sd-submit {
  height: 32px;
  padding: 0 18px;
  border: none;
  border-radius: 8px;
  background: var(--success);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: filter 0.15s;
}
.sd-submit:hover:not(:disabled) {
  filter: brightness(1.08);
}
.sd-submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
