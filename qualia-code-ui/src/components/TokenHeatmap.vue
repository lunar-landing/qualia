<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getTokenStats } from '@/api/chat'
import { useChatStore } from '@/stores/chat'
import type { TokenStat } from '@/types'

/**
 * 侧边栏「近 30 日 Token 用量」热力矩阵（平移旧 js/token-map.js）：
 * 按当期最大值动态分 4 档渲染 10×3 方格；hover 气泡展示日期与用量；
 * 接口失败或无数据时整块隐藏，不破坏侧边栏布局；
 * 点击矩阵弹出统计详情面板：总量/日均/峰值/活跃天数四张卡片 + SVG 折线趋势图。
 */
const chat = useChatStore()
const { t } = useI18n()

/** 本地化日期提示（中文 M月D日 / 英文 M/D） */
function dayTip(date: string, tokens: string): string {
  const dt = new Date(date)
  return t('heatmap.dayTip', { m: dt.getMonth() + 1, d: dt.getDate(), tokens })
}

const stats = ref<TokenStat[]>([])

function formatTokens(n: number): string {
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M'
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'k'
  return String(n)
}

async function refresh() {
  try {
    const list = await getTokenStats(30)
    stats.value = Array.isArray(list) ? list : []
  } catch {
    /* 静默失败：保持隐藏，不影响主界面 */
  }
}

// 任一会话流收尾后刷新（对齐旧版 sendMessage 收尾后 TokenHeatmap.refresh()，后台会话收尾也触发）
const activeCount = computed(
  () => Object.values(chat.streamBySession).filter((s) => s.status !== 'idle' && s.status !== 'error').length,
)
watch(activeCount, (n, o) => {
  if (n < o) void refresh()
})

// ===== 热力矩阵 =====
interface Cell {
  lv: number
  tip: string
}
const cells = computed<Cell[]>(() => {
  const list = stats.value
  const max = Math.max(0, ...list.map((d) => d.tokens))
  return list.map((d) => {
    // 0 为 lv0，其余按最大值比例分 4 档
    let lv = 0
    if (d.tokens > 0 && max > 0) {
      const r = d.tokens / max
      lv = r > 0.75 ? 4 : r > 0.5 ? 3 : r > 0.25 ? 2 : 1
    }
    return { lv, tip: dayTip(d.date, formatTokens(d.tokens)) }
  })
})

// ===== 统计详情面板 =====
// 折线图 viewBox 逻辑尺寸（实际宽度自适应，等比缩放）
const VB_W = 640
const VB_H = 230
const PAD = { l: 48, r: 18, t: 16, b: 28 }

const detailOpen = ref(false)
const detailStats = ref<TokenStat[]>([])
const detailEmpty = ref(false)

async function openDetail() {
  detailOpen.value = true
  await renderDetail()
}

async function renderDetail() {
  detailEmpty.value = false
  detailStats.value = []
  try {
    const list = await getTokenStats(30)
    detailStats.value = Array.isArray(list) ? list : []
  } catch {
    /* 静默失败，下方统一走空态 */
  }
  if (!detailStats.value.length) detailEmpty.value = true
}

function closeDetail() {
  detailOpen.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && detailOpen.value) closeDetail()
}

onMounted(() => {
  void refresh()
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

// 汇总卡片
const cards = computed(() => {
  const list = detailStats.value
  const first = list[0]
  if (!first) return []
  const total = list.reduce((s, d) => s + d.tokens, 0)
  const active = list.filter((d) => d.tokens > 0).length
  const peak = list.reduce((a, b) => (b.tokens > a.tokens ? b : a), first)
  const dt = new Date(peak.date)
  return [
    { num: formatTokens(total), lbl: t('heatmap.total') },
    { num: formatTokens(Math.round(total / list.length)), lbl: t('heatmap.dailyAvg') },
    { num: formatTokens(peak.tokens), lbl: t('heatmap.peakDay', { date: `${dt.getMonth() + 1}/${dt.getDate()}` }) },
    { num: `${active}/${list.length}`, lbl: t('heatmap.activeDays') },
  ]
})

/** y 轴最大值取整到 1/2/5×10^n，免得刻度出现碎数 */
function niceMax(v: number): number {
  if (v <= 0) return 1
  const pow = Math.pow(10, Math.floor(Math.log10(v)))
  const r = v / pow
  return (r <= 1 ? 1 : r <= 2 ? 2 : r <= 5 ? 5 : 10) * pow
}

interface ChartPt {
  x: number
  y: number
  date: string
  tokens: number
}

const chart = computed(() => {
  const list = detailStats.value
  if (!list.length) return null
  const innerW = VB_W - PAD.l - PAD.r
  const innerH = VB_H - PAD.t - PAD.b
  const maxV = niceMax(Math.max(...list.map((d) => d.tokens)))
  const n = list.length
  const pts: ChartPt[] = list.map((d, i) => ({
    x: PAD.l + (n > 1 ? (i * innerW) / (n - 1) : innerW / 2),
    y: PAD.t + (1 - d.tokens / maxV) * innerH,
    date: d.date,
    tokens: d.tokens,
  }))
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join('')
  const baseY = PAD.t + innerH
  const last = pts[n - 1]
  const first = pts[0]
  if (!last || !first) return null
  const area = `${line}L${last.x.toFixed(1)},${baseY}L${first.x.toFixed(1)},${baseY}Z`
  // 横向网格线 + y 轴刻度（0 到最大值均分 4 段）
  const yTicks = [0, 1, 2, 3, 4].map((i) => ({
    y: PAD.t + (innerH * i) / 4,
    label: formatTokens((maxV * (4 - i)) / 4),
  }))
  // x 轴日期刻度：首尾 + 中间均匀 3 个（去重防短周期重叠）
  const xIdx = [...new Set([0, (n - 1) >> 2, (n - 1) >> 1, Math.round(((n - 1) * 3) / 4), n - 1])]
  const xTicks = xIdx.flatMap((i) => {
    const p = pts[i]
    if (!p) return []
    const d = new Date(p.date)
    return [{ x: p.x, label: `${d.getMonth() + 1}/${d.getDate()}` }]
  })
  return { pts, line, area, baseY, yTicks, xTicks }
})

// hover 十字寻点：虚线游标 + 定位圆点 + 日期/用量气泡（气泡贴边限幅防溢出）
const svgEl = ref<SVGSVGElement | null>(null)
const hoverIdx = ref<number | null>(null)

function onChartMove(e: MouseEvent) {
  const pts = chart.value?.pts
  const svg = svgEl.value
  if (!pts?.length || !svg) return
  const rect = svg.getBoundingClientRect()
  const vx = ((e.clientX - rect.left) / rect.width) * VB_W
  let best = 0
  let bd = Infinity
  pts.forEach((p, i) => {
    const d = Math.abs(p.x - vx)
    if (d < bd) {
      bd = d
      best = i
    }
  })
  hoverIdx.value = best
}

function onChartLeave() {
  hoverIdx.value = null
}

const hoverPt = computed<ChartPt | null>(() => {
  if (hoverIdx.value === null) return null
  return chart.value?.pts[hoverIdx.value] ?? null
})

const hoverTip = computed(() => {
  const p = hoverPt.value
  if (!p) return ''
  return dayTip(p.date, formatTokens(p.tokens))
})

const tipStyle = computed(() => {
  const p = hoverPt.value
  if (!p) return {}
  return { left: (p.x / VB_W) * 100 + '%', top: (p.y / VB_H) * 100 + '%' }
})
</script>

<template>
  <!-- 无数据/接口失败整块隐藏，不破坏侧边栏布局 -->
  <div v-if="cells.length" class="token-heatmap" @click="openDetail">
    <div class="th-title"><i class="fas fa-fire"></i> {{ t('heatmap.title') }}</div>
    <div class="th-grid">
      <span v-for="(c, i) in cells" :key="i" class="th-cell" :class="'lv' + c.lv" :data-tip="c.tip"></span>
    </div>
  </div>

  <!-- 统计详情面板（Teleport 到 body，等价旧 document.body.appendChild） -->
  <Teleport to="body">
    <div v-if="detailOpen" class="thd-overlay open" @click.self="closeDetail">
      <div class="thd-dialog">
        <div class="thd-head">
          <h4><i class="fas fa-chart-line"></i> {{ t('heatmap.statsTitle') }}</h4>
          <button class="thd-close" :title="t('common.close')" @click="closeDetail"><i class="fas fa-times"></i></button>
        </div>
        <div v-if="detailEmpty" class="thd-empty">{{ t('heatmap.empty') }}</div>
        <template v-else>
          <div class="thd-cards">
            <div v-for="c in cards" :key="c.lbl" class="thd-card">
              <div class="num">{{ c.num }}</div>
              <div class="lbl">{{ c.lbl }}</div>
            </div>
          </div>
          <div class="thd-chart">
            <svg
              ref="svgEl"
              class="thd-svg"
              :viewBox="`0 0 ${VB_W} ${VB_H}`"
              @mousemove="onChartMove"
              @mouseleave="onChartLeave"
            >
              <defs>
                <linearGradient id="thdFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="var(--th-lv3, #3fb950)" stop-opacity="0.3" />
                  <stop offset="100%" stop-color="var(--th-lv3, #3fb950)" stop-opacity="0" />
                </linearGradient>
              </defs>
              <template v-if="chart">
                <line
                  v-for="(t, i) in chart.yTicks"
                  :key="'g' + i"
                  :x1="PAD.l"
                  :y1="t.y"
                  :x2="VB_W - PAD.r"
                  :y2="t.y"
                  class="thd-gridline"
                />
                <text
                  v-for="(t, i) in chart.yTicks"
                  :key="'y' + i"
                  :x="PAD.l - 8"
                  :y="t.y + 3.5"
                  class="thd-ytick"
                >{{ t.label }}</text>
                <text v-for="(t, i) in chart.xTicks" :key="'x' + i" :x="t.x" :y="VB_H - 8" class="thd-xtick">
                  {{ t.label }}
                </text>
                <path :d="chart.area" fill="url(#thdFill)" />
                <path
                  :d="chart.line"
                  fill="none"
                  stroke="var(--th-lv3, #3fb950)"
                  stroke-width="2"
                  stroke-linejoin="round"
                  stroke-linecap="round"
                />
                <line
                  v-if="hoverPt"
                  class="thd-cursor"
                  :x1="hoverPt.x"
                  :x2="hoverPt.x"
                  :y1="PAD.t"
                  :y2="chart.baseY"
                />
                <circle v-if="hoverPt" class="thd-dot" :cx="hoverPt.x" :cy="hoverPt.y" r="3.5" />
              </template>
            </svg>
            <div v-if="hoverPt" class="thd-tip" :style="tipStyle">{{ hoverTip }}</div>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<style>
/* ===== 热力矩阵（旧 token-map.js 自包含样式平移；th-/thd- 前缀全局防冲突） ===== */
.token-heatmap {
  flex-shrink: 0;
  padding: 11px 4px 2px;
  border-top: 1px solid var(--border-color);
  margin-bottom: 9px;
  /* 整块可点击打开统计详情 */
  cursor: pointer;
}
.th-title {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 7px;
  display: flex;
  align-items: center;
  gap: 5px;
}
.th-title i {
  font-size: 9.5px;
  color: var(--accent-light);
}
.th-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 4px;
}
.th-cell {
  aspect-ratio: 1;
  width: 100%;
  border-radius: 3px;
  background: var(--bg-hover);
  position: relative;
  transition:
    transform 0.12s,
    background 0.3s;
}
.th-grid .th-cell:hover {
  transform: scale(1.25);
  outline: 1px solid var(--accent-light);
  z-index: 2;
}
.th-cell.lv4 {
  box-shadow: 0 0 6px var(--th-glow, rgba(124, 108, 240, 0.45));
}
.th-cell.lv1 {
  background: var(--th-lv1, rgba(124, 108, 240, 0.28));
}
.th-cell.lv2 {
  background: var(--th-lv2, rgba(124, 108, 240, 0.5));
}
.th-cell.lv3 {
  background: var(--th-lv3, rgba(124, 108, 240, 0.75));
}
.th-cell.lv4 {
  background: var(--th-lv4, var(--accent));
}
/* hover 气泡：日期 + token 数 */
.th-grid .th-cell::after {
  content: attr(data-tip);
  position: absolute;
  bottom: calc(100% + 5px);
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 7px;
  border-radius: 5px;
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  font-size: 10.5px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s;
  z-index: 10;
}
.th-grid .th-cell:hover::after {
  opacity: 1;
}
/* 前两列的气泡左对齐、末两列右对齐，避免溢出侧边栏 */
.th-grid .th-cell:nth-child(10n + 1)::after,
.th-grid .th-cell:nth-child(10n + 2)::after {
  left: 0;
  transform: none;
}
.th-grid .th-cell:nth-child(10n)::after,
.th-grid .th-cell:nth-child(10n + 9)::after {
  left: auto;
  right: 0;
  transform: none;
}

/* ===== 统计详情面板（遮罩 + 卡片 + 折线图，尺寸规格对齐设置弹窗）===== */
.thd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  z-index: 1000;
  display: none;
  align-items: center;
  justify-content: center;
}
.thd-overlay.open {
  display: flex;
}
.thd-dialog {
  width: min(684px, calc(100vw - 48px));
  background: var(--bg-surface);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 14px 18px 18px;
}
.thd-head {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 13px;
}
.thd-head h4 {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 7px;
  margin-right: auto;
}
.thd-head h4 i {
  color: var(--accent-light);
  font-size: 11.5px;
}
.thd-close {
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 12.5px;
  padding: 4px 7px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.thd-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
/* 汇总卡片 */
.thd-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 9px;
  margin-bottom: 13px;
}
.thd-card {
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 9px;
  padding: 9px 11px;
}
.thd-card .num {
  font-family: 'JetBrains Mono', monospace;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}
.thd-card .lbl {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 3px;
}
/* 折线图 */
.thd-chart {
  position: relative;
}
.thd-svg {
  display: block;
  width: 100%;
  height: auto;
}
.thd-gridline {
  stroke: var(--border-color);
  stroke-width: 1;
}
.thd-ytick {
  fill: var(--text-muted);
  font-size: 10px;
  text-anchor: end;
}
.thd-xtick {
  fill: var(--text-muted);
  font-size: 10px;
  text-anchor: middle;
}
.thd-cursor {
  stroke: var(--border-active);
  stroke-dasharray: 3 3;
}
.thd-dot {
  fill: var(--th-lv3, #3fb950);
  stroke: var(--bg-surface);
  stroke-width: 2;
}
.thd-tip {
  position: absolute;
  transform: translate(-50%, -140%);
  padding: 4px 7px;
  border-radius: 5px;
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  font-size: 10.5px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 5;
}
.thd-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 11px;
  padding: 43px 0;
}
</style>
