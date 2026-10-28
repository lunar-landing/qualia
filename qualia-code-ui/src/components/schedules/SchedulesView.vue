<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSchedulesStore } from '@/stores/schedules'
import { useSessionStore } from '@/stores/session'
import { fmtDuration, fmtRelNext, fmtRunTime } from '@/utils/schedule'
import type { ScheduleDef, ScheduleRun } from '@/types'

/**
 * 定时任务中心（聊天区 + 工作区面板之上的整栏覆盖视图）：定宽卡片网格（G2 仪表盘），
 * 卡面 = 状态行 + 提示词 + 执行脉搏条 + 倒计时底栏；脉搏块点击跳该轮运行会话，
 * 行内历史展开层由脉搏承接（块高映射耗时，tooltip 带摘要）。
 * 新建/修改周期走 ScheduleDialog 弹窗（右上角「新建任务」、卡面铅笔）。
 * 底栏历史钮打开「全部运行」弹窗：列出后端保留的全部记录（新在前），行点击跳运行会话。
 */
const sched = useSchedulesStore()
const sessionStore = useSessionStore()
const { t, locale } = useI18n()

/** 卡级状态：执行中 > 已暂停 > 上次失败 > 正常（色带/状态行/置灰共用此分类） */
function bandOf(def: ScheduleDef): 'busy' | 'idle' | 'fail' | 'ok' {
  if (def.lastStatus === 'running') return 'busy'
  if (!def.enabled) return 'idle'
  if (def.lastStatus === 'error') return 'fail'
  return 'ok'
}

const STATUS_ICON: Record<ReturnType<typeof bandOf>, string> = {
  busy: 'fa-circle-notch fa-spin',
  idle: 'fa-circle-pause',
  fail: 'fa-circle-xmark',
  ok: 'fa-circle-check',
}

function statusIcon(def: ScheduleDef): string {
  return STATUS_ICON[bandOf(def)]
}

function statusLabel(def: ScheduleDef): string {
  switch (bandOf(def)) {
    case 'busy':
      return t('sched.statusRunning')
    case 'idle':
      return t('sched.statusOff')
    case 'fail':
      return t('sched.stFail')
    default:
      return t('sched.statusOn')
  }
}

/** 脉搏：最近 8 条按旧→新排布（后端 runs 新在前）；无记录返回空，卡面以占位文案保持卡高一致 */
function pulseRuns(def: ScheduleDef): ScheduleRun[] {
  return def.runs.slice(0, 8).reverse()
}

/** 块高三档：按组内最长耗时比例（≥60% 高 / ≥25% 中 / 其余矮）；失败恒高强调，执行中恒高 */
function pulseHeight(run: ScheduleRun, list: ScheduleRun[]): 'h' | 'm' | 's' {
  if (run.status === 'error' || run.status === 'running') return 'h'
  const max = Math.max(1, ...list.map((r) => r.durationMs))
  const ratio = run.durationMs / max
  return ratio >= 0.6 ? 'h' : ratio >= 0.25 ? 'm' : 's'
}

/** 脉搏块 tooltip：时间 · 状态 · 耗时 · 摘要（跳会话前的信息兜底） */
function pulseTitle(run: ScheduleRun): string {
  const st =
    run.status === 'error' ? t('sched.runFail') : run.status === 'running' ? t('sched.statusRunning') : t('sched.runOk')
  const tail = run.error || run.summary || ''
  return `${fmtRunTime(run.runAt)} · ${st} · ${fmtDuration(run.durationMs)}${tail ? ` · ${tail}` : ''}`
}

/** 跳到某轮运行的会话查看执行轮次（会话已删除则不跳）；同时收掉全部运行弹窗 */
function openSession(sid?: string | null) {
  if (!sid || !sessionStore.sessions.some((s) => s.id === sid)) return
  closeHist()
  sessionStore.switchTo(sid)
  sched.closeView()
}

async function toggleEnabled(def: ScheduleDef) {
  await sched.setEnabled(def, !def.enabled).catch(() => undefined)
}

async function runNow(def: ScheduleDef) {
  await sched.runNow(def.id).catch((e: unknown) => alert(e instanceof Error ? e.message : String(e)))
}

async function remove(def: ScheduleDef) {
  if (!window.confirm(t('sched.removeConfirm', { title: def.title }))) return
  await sched.remove(def.id).catch(() => undefined)
}

// ===== 查看全部运行弹窗：脉搏条只展示最近 8 次，弹窗列后端保留的全部记录（新在前） =====
const histFor = ref<ScheduleDef | null>(null)
const histDef = computed(() =>
  histFor.value ? (sched.list.find((d) => d.id === histFor.value!.id) ?? null) : null,
)
function openHist(def: ScheduleDef) {
  histFor.value = def
  nextTick(() => document.addEventListener('keydown', onHistKey))
}
function closeHist() {
  histFor.value = null
  document.removeEventListener('keydown', onHistKey)
}
function onHistKey(e: KeyboardEvent) {
  if (e.key === 'Escape') closeHist()
}
// 视图以其他方式关闭（入口切换/关闭钮）时弹窗跟随收起，避免 fixed 遮罩浮在聊天区
watch(() => sched.open, (open) => { if (!open) closeHist() })
onBeforeUnmount(() => document.removeEventListener('keydown', onHistKey))

const totalCount = computed(() => sched.list.length)
const activeCount = computed(() => sched.list.filter((d) => d.enabled).length)
</script>

<template>
  <section v-if="sched.open" class="scheds-view">
    <!-- 页头：大标题式（对齐资料列表页 list-head 语言），覆盖视图关闭钮保留 -->
    <header class="sv-head">
      <div class="sv-title-row">
        <h1>{{ t('sched.title') }}</h1>
        <span class="sv-count">{{ activeCount }}/{{ totalCount }}</span>
        <span class="spring"></span>
        <button type="button" class="sv-new" :title="t('sched.newHint')" @click="sched.openDialog()">
          <i class="fas fa-plus"></i>
          <span>{{ t('sched.new') }}</span>
        </button>
        <button type="button" class="sv-close" :title="t('common.close')" @click="sched.closeView()">
          <i class="fas fa-times"></i>
        </button>
      </div>
    </header>

    <!-- 任务列表 -->
    <div class="sv-body">
      <div v-if="sched.loading && !sched.list.length" class="sv-empty">
        <i class="fas fa-circle-notch fa-spin"></i>
      </div>
      <div v-else-if="!sched.list.length" class="sv-empty">
        <i class="fas fa-clock"></i>
        <div class="sve-title">{{ t('sched.empty') }}</div>
        <div class="sve-hint">{{ t('sched.emptyHint') }}</div>
      </div>
      <ul v-else class="sv-grid">
        <li v-for="def in sched.list" :key="def.id" class="gv-card" :class="bandOf(def)">
          <!-- 状态行：左状态文本，右周期 -->
          <div class="gv-top">
            <span class="gv-st"><i class="fas" :class="statusIcon(def)"></i>{{ statusLabel(def) }}</span>
            <span class="gv-cron">{{ def.cronText }}</span>
          </div>
          <div class="gv-title" :title="def.title">{{ def.title }}</div>
          <div class="gv-prompt" :title="def.prompt">{{ def.prompt }}</div>

          <!-- 执行脉搏：最近 8 次旧→新，点击跳该轮运行会话 -->
          <div v-if="pulseRuns(def).length" class="gv-pulse">
            <button
              v-for="run in pulseRuns(def)"
              :key="run.runAt"
              type="button"
              class="gv-p"
              :class="[run.status === 'error' ? 'bad' : run.status === 'running' ? 'live' : 'ok', pulseHeight(run, pulseRuns(def))]"
              :disabled="!run.sessionId"
              :title="pulseTitle(run)"
              @click="openSession(run.sessionId)"
            ></button>
          </div>
          <div v-else class="gv-noruns">{{ t('sched.noRuns') }}</div>

          <!-- 倒计时底栏：下次运行主信息 + 操作组（hover 浮现，开关常驻） -->
          <div class="gv-foot">
            <div class="gv-nx" :class="{ none: !def.enabled }">
              <template v-if="def.enabled && def.nextRunAt">
                <div class="rel">{{ fmtRelNext(def.nextRunAt, locale !== 'zh-CN') }}</div>
                <div class="abs">{{ fmtRunTime(def.nextRunAt) }}</div>
              </template>
              <div v-else class="rel">{{ t('sched.statusOff') }}</div>
            </div>
            <div class="gv-acts">
              <button type="button" class="gv-btn" :title="t('sched.runNow')" @click="runNow(def)">
                <i class="fas fa-bolt"></i>
              </button>
              <button type="button" class="gv-btn" :title="t('sched.allRuns')" @click="openHist(def)">
                <i class="fas fa-clock-rotate-left"></i>
              </button>
              <button
                type="button"
                class="sv-switch"
                :class="{ on: def.enabled }"
                :title="def.enabled ? t('sched.pause') : t('sched.resume')"
                @click="toggleEnabled(def)"
              >
                <span class="knob"></span>
              </button>
              <button type="button" class="gv-btn" :title="t('sched.editCron')" @click="sched.openEditDialog(def.id)">
                <i class="fas fa-pen"></i>
              </button>
              <button type="button" class="gv-btn danger" :title="t('common.delete')" @click="remove(def)">
                <i class="fas fa-trash-can"></i>
              </button>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </section>

  <!-- 查看全部运行弹窗：列出后端保留的全部执行记录（新在前），行点击跳运行会话 -->
  <div v-if="histDef" class="swh-overlay" @click.self="closeHist">
    <div class="swh-dialog">
      <header class="swh-head">
        <i class="fas fa-clock-rotate-left"></i>
        <h2>{{ t('sched.allRuns') }}</h2>
        <span class="swh-cnt">{{ histDef.runs.length }}</span>
        <button type="button" class="swh-x" :title="t('common.close')" @click="closeHist">
          <i class="fas fa-times"></i>
        </button>
      </header>
      <div class="swh-body">
        <div v-if="!histDef.runs.length" class="swh-empty">{{ t('sched.noRuns') }}</div>
        <ul v-else class="swh-list">
          <li
            v-for="run in histDef.runs"
            :key="run.runAt"
            class="swh-row"
            :class="{ dead: !run.sessionId }"
            :title="run.sessionId ? '' : t('sched.sessionGone')"
            @click="openSession(run.sessionId)"
          >
            <i
              class="fas swh-st"
              :class="run.status === 'error' ? 'fa-circle-xmark bad' : run.status === 'running' ? 'fa-circle-notch fa-spin live' : 'fa-circle-check ok'"
            ></i>
            <span class="swh-time">{{ fmtRunTime(run.runAt) }}</span>
            <span class="swh-dur">{{ fmtDuration(run.durationMs) }}</span>
            <span class="swh-sum">{{ run.error || run.summary || '—' }}</span>
            <span v-if="run.sessionId" class="swh-go">{{ t('sched.viewTurn') }}<i class="fas fa-arrow-right"></i></span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scheds-view {
  position: absolute;
  inset: 0;
  z-index: 45;
  display: flex;
  flex-direction: column;
  /* 与资料列表页舞台同底（--mat-stage 全局定义），两个覆盖视图切换时底色一致 */
  background: var(--mat-stage, var(--bg-app));
}

/* ===== 页头（大标题式，对齐资料列表页 list-head：留白分层，无分隔线） ===== */
.sv-head {
  padding: 26px 30px 0;
  flex-shrink: 0;
}
.sv-title-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 20px;
}
.sv-title-row h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--text-primary);
}
.sv-count {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11.5px;
  color: var(--text-muted);
  margin-bottom: 5px;
}
.spring {
  flex: 1;
}
.sv-new {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 13px;
  border: none;
  border-radius: 9px;
  background: var(--text-primary);
  color: var(--bg-app);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.16s ease;
}
.sv-new:hover {
  opacity: 0.86;
}
.sv-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.sv-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* ===== 列表（铺满 + 30px 留白，对齐资料页 list-pad） ===== */
.sv-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 30px 40px;
}
/* ===== 卡片网格（定宽列：卡宽不随分辨率伸缩，避免元素错位；只换列数适配） ===== */
.sv-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, 290px);
  gap: 12px;
}

/* 卡片骨架：左缘 3px 状态色带 + 纵排四段（状态行/提示词/脉搏/底栏） */
.gv-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-surface);
  padding: 14px 16px 12px 19px;
  overflow: hidden;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.gv-card:hover {
  border-color: color-mix(in srgb, var(--text-muted) 55%, var(--border-color));
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.06);
}
.gv-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  border-radius: 3px 0 0 3px;
  background: var(--success);
}
.gv-card.busy::before {
  background: var(--accent);
}
.gv-card.fail::before {
  background: var(--error);
}
.gv-card.idle::before {
  background: var(--text-muted);
}
.gv-card.idle .gv-title,
.gv-card.idle .gv-prompt {
  opacity: 0.55;
}

/* 状态行：左状态文本，右周期 */
.gv-top {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  color: var(--text-muted);
}
.gv-st {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 600;
}
.gv-st i {
  font-size: 10.5px;
}
.gv-card.ok .gv-st {
  color: var(--success);
}
.gv-card.fail .gv-st {
  color: var(--error);
}
.gv-card.busy .gv-st {
  color: var(--accent);
}
.gv-card.idle .gv-st {
  color: var(--text-muted);
  font-weight: 500;
}
.gv-cron {
  margin-left: auto;
  white-space: nowrap;
}

/* 标题：卡面主信息，单行省略；内容提示词降为 2 行截断的次级描述 */
.gv-title {
  font-size: 13.5px;
  line-height: 1.4;
  margin: 8px 0 3px;
  color: var(--text-primary);
  font-weight: 650;
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gv-prompt {
  font-size: 11.5px;
  line-height: 1.55;
  margin: 0 0 6px;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 执行脉搏：最近 8 次旧→新，块高映射耗时；点击跳该轮运行会话 */
.gv-pulse {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 18px;
  margin-top: auto;
  padding-top: 10px;
}
.gv-p {
  width: 7px;
  border: none;
  padding: 0;
  border-radius: 2px;
  background: var(--success);
  opacity: 0.5;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.gv-p:hover {
  opacity: 1;
}
.gv-p.h {
  height: 17px;
}
.gv-p.m {
  height: 12px;
}
.gv-p.s {
  height: 7px;
}
.gv-p.bad {
  background: var(--error);
  opacity: 0.9;
}
.gv-p.live {
  background: var(--accent);
  animation: gv-blink 1s ease infinite;
}
.gv-p:disabled {
  cursor: default;
  opacity: 0.3;
}
.gv-p:disabled:hover {
  opacity: 0.3;
}
@keyframes gv-blink {
  50% {
    opacity: 0.25;
  }
}
.gv-noruns {
  margin-top: auto;
  padding-top: 10px;
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.7;
}

/* 倒计时底栏：负 margin 抵掉卡片内边距实现通栏浅底 */
.gv-foot {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin: 11px -16px -12px -19px;
  padding: 9px 16px 10px 19px;
  border-top: 1px dashed var(--border-color);
  background: var(--bg-hover);
}
.gv-nx {
  min-width: 0;
}
.gv-nx .rel {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--text-primary);
  line-height: 1.2;
}
.gv-nx .abs {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 10px;
  color: var(--text-muted);
  margin-top: 2px;
}
.gv-nx.none .rel {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
}
.gv-acts {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 2px;
}
.gv-btn {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
  transform: translateX(-3px);
  transition: opacity 0.18s ease, transform 0.18s ease, background 0.16s ease, color 0.16s ease;
}
.gv-card:hover .gv-btn {
  opacity: 1;
  transform: none;
}
.gv-btn:hover {
  background: var(--bg-surface);
  color: var(--text-primary);
}
.gv-btn.danger:hover {
  color: var(--error);
  background: color-mix(in srgb, var(--error) 8%, transparent);
}
.sv-switch {
  width: 30px;
  height: 17px;
  border: none;
  border-radius: 999px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  position: relative;
  cursor: pointer;
  padding: 0;
}
.sv-switch .knob {
  position: absolute;
  top: 1px;
  left: 1px;
  width: 13px;
  height: 13px;
  border-radius: 50%;
  background: var(--text-muted);
  transition: transform 0.18s ease, background 0.18s ease;
}
.sv-switch.on {
  background: color-mix(in srgb, var(--success) 22%, transparent);
  border-color: color-mix(in srgb, var(--success) 45%, transparent);
}
.sv-switch.on .knob {
  transform: translateX(13px);
  background: var(--success);
}

/* ===== 查看全部运行弹窗（遮罩/卡片规格对齐 ScheduleDialog 的 sd- 弹窗） ===== */
.swh-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  animation: swh-fade 0.18s ease;
}
@keyframes swh-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.swh-dialog {
  width: min(520px, calc(100vw - 48px));
  max-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  box-shadow: var(--shadow);
  overflow: hidden;
  animation: swh-pop 0.26s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes swh-pop {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.swh-head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 16px 20px 12px;
}
.swh-head > i {
  color: var(--success);
  font-size: 14px;
}
.swh-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}
.swh-cnt {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: var(--text-muted);
}
.swh-x {
  margin-left: auto;
  width: 27px;
  height: 27px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.swh-x:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.swh-body {
  overflow-y: auto;
  padding: 0 8px 10px;
}
.swh-empty {
  padding: 34px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}
.swh-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.swh-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 9px;
  font-size: 11.5px;
  cursor: pointer;
  transition: background 0.14s ease;
}
.swh-row:hover {
  background: var(--bg-hover);
}
.swh-row.dead {
  cursor: default;
  opacity: 0.5;
}
.swh-row.dead:hover {
  background: transparent;
}
.swh-st {
  font-size: 11px;
  flex-shrink: 0;
}
.swh-st.ok {
  color: var(--success);
}
.swh-st.bad {
  color: var(--error);
}
.swh-st.live {
  color: var(--accent);
}
.swh-time,
.swh-dur {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.swh-time {
  width: 82px;
}
.swh-dur {
  width: 54px;
  text-align: right;
}
.swh-sum {
  flex: 1;
  min-width: 0;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.swh-go {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  color: var(--accent);
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.14s ease;
}
.swh-row:hover .swh-go {
  opacity: 1;
}

/* ===== 空态 / 加载 ===== */
.sv-empty {
  margin: 0 auto;
  padding: 110px 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
}
.sv-empty > i {
  font-size: 34px;
  opacity: 0.45;
}
.sve-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
}
.sve-hint {
  font-size: 12px;
}
</style>
