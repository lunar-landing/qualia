<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getSkillDetail, installMarketSkill, revealSkillFolder, searchMarketSkills } from '@/api/config'
import { useSkillsStore } from '@/stores/skills'
import { usePreviewStore } from '@/stores/preview'
import { enhanceCodeBlocks, renderMarkdown } from '@/utils/markdown'
import type { MarketSkillInfo, SkillDetail, SkillInfo } from '@/types'

/**
 * 技能管理中心（V1 卡网格，任务中心同源的整栏覆盖视图）：
 * 页头 = 大标题 + 启用计数 + 重新扫描（手工建目录后的显式刷新）+ 凹槽分段（已安装/市场）+ 搜索；
 * 已安装 290px 定宽卡：名称 + 常驻开关 / 描述 3 行 / scripts·refs chips / hover 动作（打开文件夹·卸载）；
 * 点卡进详情二级页（SKILL.md 正文 + 脚本/附属文档清单）；
 * 市场同款网格换卡尾（来源 + installs + 安装钮），未搜索时空态给热词直达。
 * 数据与开关在 skills store（与任务/资料视图互斥），市场态为组件级会话态。
 */
const skills = useSkillsStore()
const preview = usePreviewStore()
const { t } = useI18n()

const view = ref<'installed' | 'market'>('installed')

const totalCount = computed(() => skills.list.length)

// 已安装本地过滤：名称 + 描述，输入即滤（与任务卡搜索纪律一致）
const kw = ref('')
const filtered = computed(() => {
  const q = kw.value.trim().toLowerCase()
  if (!q) return skills.list
  return skills.list.filter((s) => `${s.name} ${s.description ?? ''}`.toLowerCase().includes(q))
})

const scriptsOf = (s: SkillInfo) => (s.scripts ?? []).filter(Boolean).length
const refsOf = (s: SkillInfo) => (s.references ?? []).filter(Boolean).length

// ===== 已安装动作 =====
async function onReveal(s: SkillInfo) {
  try {
    await revealSkillFolder(s.dir || s.name)
  } catch (e) {
    window.alert(t('settings.openFolderFailed', { msg: e instanceof Error ? e.message : String(e) }))
  }
}
async function onUninstall(s: SkillInfo) {
  if (!window.confirm(t('skills.uninstallConfirm', { name: s.name }))) return
  try {
    await skills.remove(s)
  } catch (e) {
    window.alert(t('settings.deleteFailed', { msg: e instanceof Error ? e.message : String(e) }))
  }
}

// ===== 详情二级页：点已安装卡进入，SKILL.md 正文渲染 + 脚本/附属文档清单，返回回列表 =====
const detail = ref<SkillDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const detailOpen = computed(() => detail.value !== null || detailLoading.value || detailError.value !== '')
const detailMd = computed(() => (detail.value ? renderMarkdown(detail.value.content) : ''))
const mdEl = ref<HTMLElement | null>(null)
/** 详情页开关/卸载动作复用已安装逻辑，按详情数据构造 SkillInfo 视图 */
const detailAsSkill = computed<SkillInfo | null>(() =>
  detail.value
    ? {
        name: detail.value.name,
        dir: detail.value.dir,
        description: detail.value.description,
        source: 'global',
        enabled: true,
        scripts: [],
        references: [],
      }
    : null,
)
/** frontmatter 除 name/description 外的其余键值（version/license 等）以 chip 展示 */
const extraMeta = computed(() =>
  Object.entries(detail.value?.meta ?? {}).filter(([k]) => k !== 'name' && k !== 'description'),
)

/** 过期响应守卫：连点/关闭后迟到的响应不再渲染 */
let detailSeq = 0
async function openDetail(s: SkillInfo) {
  const seq = ++detailSeq
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const d = await getSkillDetail(s.dir || s.name)
    if (seq === detailSeq) detail.value = d
  } catch (e) {
    if (seq === detailSeq) detailError.value = e instanceof Error ? e.message : String(e)
  } finally {
    if (seq === detailSeq) detailLoading.value = false
  }
  if (seq === detailSeq) await nextTick(enhanceMd)
}
function closeDetail() {
  detailSeq++
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
}
/** 代码块增强对齐聊天区（hljs 高亮 + 复制/HTML 预览工具条） */
function enhanceMd() {
  if (mdEl.value) enhanceCodeBlocks(mdEl.value, (html) => preview.openHtml(html))
}
async function uninstallFromDetail() {
  const s = detailAsSkill.value
  if (!s) return
  await onUninstall(s)
  if (!skills.list.some((x) => x.name === s.name)) closeDetail()
}

// ===== 技能市场（skills.sh 代理搜索 + GitHub 技能包安装） =====
const marketQuery = ref('')
const marketResults = ref<MarketSkillInfo[]>([])
const marketLoading = ref(false)
const marketError = ref('')
const marketSearched = ref(false)
const installingId = ref('')
/** 市场空态快捷词，点击直接搜索 */
const HOT_KEYWORDS = ['pdf', 'docx', 'frontend', 'testing', 'writing']
/** 结果预演空态的静态示例卡（中间卡可点，以 pdf 试搜真实结果） */
const PREVIEW_PANES = [
  { icon: 'fas fa-globe', name: 'web-scrape', src: 'commontools/web-scrape', installs: '1.2k' },
  { icon: 'fab fa-github', name: 'pdf-processing', src: 'anthropics/skills · pdf', installs: '18.2k' },
  { icon: 'fas fa-globe', name: 'ocr-scan', src: 'commontools/ocr-scan', installs: '3.4k' },
]

async function searchMarket(word?: string) {
  if (word !== undefined) marketQuery.value = word
  const q = marketQuery.value.trim()
  if (!q || marketLoading.value) return
  marketLoading.value = true
  marketError.value = ''
  try {
    marketResults.value = await searchMarketSkills(q)
    marketSearched.value = true
  } catch (e) {
    marketError.value = e instanceof Error ? e.message : String(e)
  } finally {
    marketLoading.value = false
  }
}
async function onInstall(m: MarketSkillInfo) {
  if (!m.installable || m.installed || installingId.value) return
  installingId.value = m.id
  try {
    await installMarketSkill(m.id)
    m.installed = true
    await skills.rescan() // 安装落盘后刷新已安装列表
  } catch (e) {
    window.alert(t('settings.installFailed', { msg: e instanceof Error ? e.message : String(e) }))
  } finally {
    installingId.value = ''
  }
}
function formatInstalls(n: number): string {
  return n >= 1000 ? `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k` : String(n)
}
</script>

<template>
  <section v-if="skills.open" class="skills-view">
    <!-- ===== 详情二级页：SKILL.md 正文 + 脚本/附属文档清单，返回回列表 ===== -->
    <template v-if="detailOpen">
      <header class="kv-head">
        <button type="button" class="kv-rescan" @click="closeDetail">
          <i class="fas fa-arrow-left"></i>
          <span>{{ t('skills.back') }}</span>
        </button>
        <template v-if="detail">
          <div class="kd-title-row">
            <span class="kv-tile"><i class="fas fa-shapes"></i></span>
            <h1 class="kd-name">{{ detail.name }}</h1>
            <span v-if="detail.meta.version" class="kv-chip kd-ver"><i class="fas fa-tag"></i>{{ detail.meta.version }}</span>
            <span class="spring"></span>
            <button
              type="button"
              class="sv-switch"
              :class="{ on: skills.isEnabled(detailAsSkill!) }"
              :title="skills.isEnabled(detailAsSkill!) ? t('settings.disableTip') : t('settings.enableTip')"
              @click="skills.setEnabled(detail.name, !skills.isEnabled(detailAsSkill!))"
            >
              <span class="knob"></span>
            </button>
            <button type="button" class="kv-ia kd-act" :title="t('settings.openFolderTitle')" @click="onReveal(detailAsSkill!)">
              <i class="far fa-folder-open"></i>
            </button>
            <button type="button" class="kv-ia danger kd-act" :title="t('settings.uninstallTitle')" @click="uninstallFromDetail">
              <i class="far fa-trash-alt"></i>
            </button>
          </div>
          <div v-if="detail.description" class="kd-desc">{{ detail.description }}</div>
          <div class="kd-meta">
            <span class="kd-path">~/.qualia/code/skills/{{ detail.dir }}</span>
            <span v-for="[k, v] in extraMeta" :key="k" class="kv-chip">{{ k }}: {{ v }}</span>
          </div>
        </template>
      </header>
      <div class="kv-body kd-body">
        <div v-if="detailLoading" class="kv-empty">
          <i class="fas fa-circle-notch fa-spin"></i>
        </div>
        <div v-else-if="detailError" class="kv-empty">
          <i class="fas fa-triangle-exclamation"></i>
          <div class="kve-title">{{ t('skills.loadFailed') }}</div>
          <div class="kve-hint">{{ detailError }}</div>
        </div>
        <div v-else-if="detail" class="kd-col">
          <div class="kd-card">
            <div class="kd-cap"><i class="far fa-file-lines"></i><span>SKILL.md</span></div>
            <!-- eslint-disable-next-line vue/no-v-html —— md 渲染经 renderMarkdown 统一出口（对齐聊天区） -->
            <div ref="mdEl" class="mat-md kd-md" v-html="detailMd"></div>
          </div>
          <div v-if="detail.scripts.length" class="kd-card">
            <div class="kd-cap"><i class="fas fa-code"></i><span>{{ t('settings.scripts') }} · {{ detail.scripts.length }}</span></div>
            <div v-for="sc in detail.scripts" :key="sc.name" class="kd-line">
              <span class="kd-file">{{ sc.file }}</span>
              <span class="kd-dsc">{{ sc.description }}</span>
            </div>
          </div>
          <div v-if="detail.references.length" class="kd-card">
            <div class="kd-cap"><i class="fas fa-book"></i><span>{{ t('settings.references') }} · {{ detail.references.length }}</span></div>
            <div v-for="rf in detail.references" :key="rf" class="kd-line">
              <span class="kd-file">{{ rf }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ===== 列表页：页头 + 卡网格 ===== -->
    <template v-else>
    <!-- 页头：大标题式（sv-head 语言），分段控件承担 已安装/市场 双子视图 -->
    <header class="kv-head">
      <div class="kv-title-row">
        <h1>{{ t('skills.title') }}</h1>
        <span class="kv-count">{{ skills.enabledCount }}/{{ totalCount }} {{ t('skills.enabled') }}</span>
        <span class="spring"></span>
        <button type="button" class="kv-rescan" :title="t('skills.rescanHint')" @click="skills.rescan()">
          <i class="fas" :class="skills.loading ? 'fa-circle-notch fa-spin' : 'fa-rotate'"></i>
          <span>{{ t('skills.rescan') }}</span>
        </button>
        <button type="button" class="kv-close" :title="t('common.close')" @click="skills.closeView()">
          <i class="fas fa-times"></i>
        </button>
      </div>
      <div class="kv-seg">
        <button type="button" :class="{ on: view === 'installed' }" @click="view = 'installed'">
          <i class="fas fa-puzzle-piece"></i>{{ t('settings.installedSkills') }}
          <span class="k">{{ totalCount }}</span>
        </button>
        <button type="button" :class="{ on: view === 'market' }" @click="view = 'market'">
          <i class="fas fa-store"></i>{{ t('settings.skillMarket') }}
        </button>
      </div>
      <div class="kv-tools">
        <!-- 一框两用语义：已安装=本地即时过滤；市场=远端搜索（回车触发） -->
        <label v-if="view === 'installed'" class="kv-search">
          <i class="fas fa-magnifying-glass"></i>
          <input v-model="kw" type="text" :placeholder="t('skills.searchInstalled')" />
        </label>
        <label v-else class="kv-search">
          <i class="fas fa-magnifying-glass"></i>
          <input
            v-model="marketQuery"
            type="text"
            :placeholder="t('settings.marketSearchPlaceholder')"
            @keydown.enter="searchMarket()"
          />
        </label>
      </div>
    </header>

    <div class="kv-body">
      <!-- ===== 已安装 ===== -->
      <template v-if="view === 'installed'">
        <div v-if="skills.loading && !totalCount" class="kv-empty">
          <i class="fas fa-circle-notch fa-spin"></i>
        </div>
        <div v-else-if="!totalCount" class="kv-empty">
          <i class="fas fa-shapes"></i>
          <div class="kve-title">{{ t('settings.noSkills') }}</div>
          <div class="kve-hint">{{ t('skills.emptyHint') }}</div>
        </div>
        <div v-else-if="!filtered.length" class="kv-empty">
          <i class="fas fa-magnifying-glass"></i>
          <div class="kve-title">{{ t('materials.filterEmpty') }}</div>
        </div>
        <ul v-else class="kv-grid">
          <li
            v-for="s in filtered"
            :key="s.name"
            class="kv-card kd-go"
            :class="{ off: !skills.isEnabled(s) }"
            @click="openDetail(s)"
          >
            <div class="kv-top">
              <span class="kv-tile"><i class="fas fa-shapes"></i></span>
              <span class="kv-nm" :title="s.dir ? `~/.qualia/skills/${s.dir}` : s.name">{{ s.name }}</span>
              <button
                type="button"
                class="sv-switch"
                :class="{ on: skills.isEnabled(s) }"
                :title="skills.isEnabled(s) ? t('settings.disableTip') : t('settings.enableTip')"
                @click.stop="skills.setEnabled(s.name, !skills.isEnabled(s))"
              >
                <span class="knob"></span>
              </button>
            </div>
            <div class="kv-desc" :title="s.description">{{ s.description || t('settings.noDesc') }}</div>
            <div class="kv-foot">
              <span class="kv-chip"><i class="fas fa-code"></i>{{ scriptsOf(s) }} {{ t('settings.scripts') }}</span>
              <span class="kv-chip"><i class="fas fa-book"></i>{{ refsOf(s) }} {{ t('settings.references') }}</span>
              <span class="kv-acts">
                <button type="button" class="kv-ia" :title="t('settings.openFolderTitle')" @click.stop="onReveal(s)">
                  <i class="far fa-folder-open"></i>
                </button>
                <button type="button" class="kv-ia danger" :title="t('settings.uninstallTitle')" @click.stop="onUninstall(s)">
                  <i class="far fa-trash-alt"></i>
                </button>
              </span>
            </div>
          </li>
        </ul>
      </template>

      <!-- ===== 技能市场 ===== -->
      <template v-else>
        <div v-if="marketLoading" class="kv-empty">
          <i class="fas fa-circle-notch fa-spin"></i>
        </div>
        <div v-else-if="marketError" class="kv-empty">
          <i class="fas fa-triangle-exclamation"></i>
          <div class="kve-title">{{ marketError }}</div>
        </div>
        <div v-else-if="!marketSearched" class="kv-empty kve-preview">
          <div class="kve-cap">{{ t('skills.previewCap') }}</div>
          <div class="kve-peek">
            <div
              v-for="(p, pi) in PREVIEW_PANES"
              :key="p.name"
              class="kv-card kve-pk"
              :class="pi === 1 ? 'mid' : 'ghost'"
              @click="pi === 1 && searchMarket('pdf')"
            >
              <div class="kv-top">
                <span class="kv-tile">
                  <i :class="p.icon"></i>
                </span>
                <span class="kv-nm">{{ p.name }}</span>
              </div>
              <div class="kv-src">{{ p.src }}</div>
              <div class="kv-foot">
                <span class="kv-chip"><i class="fas fa-download"></i>{{ p.installs }}</span>
                <!-- 中帧可点但执行的是试搜，按钮如实标为搜索；两侧纯展示帧保留安装外观 -->
                <span v-if="pi === 1" class="kv-inst"><i class="fas fa-magnifying-glass"></i>{{ t('skills.previewSearchBtn') }}</span>
                <span v-else class="kv-inst"><i class="fas fa-download"></i>{{ t('settings.installBtn') }}</span>
              </div>
            </div>
          </div>
          <div class="kve-hot">
            <button v-for="w in HOT_KEYWORDS" :key="w" type="button" class="kv-hot" @click="searchMarket(w)">
              {{ w }}
            </button>
          </div>
          <div class="kve-try">{{ t('skills.previewTry') }}</div>
        </div>
        <div v-else-if="!marketResults.length" class="kv-empty">
          <i class="fas fa-ghost"></i>
          <div class="kve-title">{{ t('settings.noMarketResults') }}</div>
        </div>
        <ul v-else class="kv-grid">
          <li v-for="m in marketResults" :key="m.id" class="kv-card">
            <div class="kv-top">
              <span class="kv-tile">
                <i :class="m.installable ? 'fab fa-github' : 'fas fa-globe'"></i>
              </span>
              <span class="kv-nm" :title="m.id">{{ m.name }}</span>
            </div>
            <div class="kv-src" :title="m.source">{{ m.source }}</div>
            <div class="kv-foot">
              <span class="kv-chip"><i class="fas fa-download"></i>{{ formatInstalls(m.installs) }}</span>
              <button
                v-if="m.installed"
                type="button"
                class="kv-inst done"
                disabled
              >
                <i class="fas fa-check"></i>{{ t('settings.installedTag') }}
              </button>
              <button
                v-else
                type="button"
                class="kv-inst"
                :class="{ off: !m.installable }"
                :disabled="!m.installable || installingId === m.id"
                :title="m.installable ? '' : t('settings.notInstallableTip')"
                @click="onInstall(m)"
              >
                <i :class="installingId === m.id ? 'fas fa-circle-notch fa-spin' : 'fas fa-download'"></i>
                {{ m.installable ? t('settings.installBtn') : t('settings.notInstallable') }}
              </button>
            </div>
          </li>
        </ul>
      </template>
    </div>
    </template>
  </section>
</template>

<style scoped>
/* ===== 整栏覆盖视图（与任务中心同底同层） ===== */
.skills-view {
  position: absolute;
  inset: 0;
  z-index: 45;
  display: flex;
  flex-direction: column;
  background: var(--mat-stage, var(--bg-app));
}

/* ===== 页头（大标题式，对齐 scheds-view sv-head 规格） ===== */
.kv-head {
  padding: 26px 30px 0;
  flex-shrink: 0;
}
.kv-title-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 16px;
}
.kv-title-row h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--text-primary);
}
.kv-count {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11.5px;
  color: var(--text-muted);
  margin-bottom: 5px;
}
.spring {
  flex: 1;
}
.kv-rescan {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 13px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.kv-rescan:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.kv-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.kv-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* 凹槽分段：已安装/市场（track 浅槽，active 浮起白片） */
.kv-seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border-radius: 10px;
  background: var(--mat-wash);
  margin-bottom: 14px;
}
.kv-seg button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12.5px;
  color: var(--text-secondary);
  padding: 6px 14px;
  border-radius: 8px;
  transition: background 0.15s ease, color 0.15s ease;
}
.kv-seg button:hover {
  color: var(--text-primary);
}
.kv-seg button.on {
  background: var(--bg-surface);
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.kv-seg .k {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 10.5px;
  color: var(--text-muted);
}

/* 搜索行 */
.kv-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}
.kv-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 30px;
  width: 100%;
  max-width: 320px;
  padding: 0 11px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-surface);
}
.kv-search i {
  font-size: 10.5px;
  color: var(--text-muted);
}
.kv-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 12px;
  color: var(--text-primary);
}
.kv-search input::placeholder {
  color: var(--text-muted);
}

/* ===== 主体与网格（定宽列，任务卡同规） ===== */
.kv-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 30px 40px;
}
.kv-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, 290px);
  gap: 12px;
}

/* 卡骨架：与 gv-card 同规格（边框/圆角/hover 提亮），停用整卡降透明 */
.kv-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-surface);
  padding: 14px 16px 12px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.kv-card:hover {
  border-color: color-mix(in srgb, var(--text-muted) 55%, var(--border-color));
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.06);
}
.kv-card.off {
  opacity: 0.55;
}
.kv-top {
  display: flex;
  align-items: center;
  gap: 9px;
}
.kv-tile {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--mat-accent-wash);
  color: var(--mat-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11.5px;
  flex-shrink: 0;
}
.kv-nm {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kv-desc {
  font-size: 11.5px;
  color: var(--text-secondary);
  line-height: 1.65;
  margin: 9px 0 10px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 57px;
}
/* 市场卡中段：来源仓库（描述字段市场不提供，占位等高保持网格整齐） */
.kv-src {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: var(--text-muted);
  margin: 9px 0 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: 18px;
}
.kv-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  border-top: 1px dashed var(--border-color);
  padding-top: 9px;
  margin-top: auto;
}
.kv-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 10px;
  color: var(--text-muted);
  background: var(--mat-wash);
  border-radius: 5px;
  padding: 2px 7px;
}
.kv-chip i {
  font-size: 9px;
}
.kv-acts {
  margin-left: auto;
  display: flex;
  gap: 3px;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.kv-card:hover .kv-acts {
  opacity: 1;
}
.kv-ia {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 10.5px;
  cursor: pointer;
}
.kv-ia:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.kv-ia.danger:hover {
  background: rgba(209, 73, 91, 0.12);
  color: var(--error);
}

/* 安装钮：市场卡尾右对齐 */
.kv-inst {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  cursor: pointer;
  font-size: 11px;
  padding: 5px 12px;
  border-radius: 7px;
  background: var(--text-primary);
  color: var(--bg-app);
  flex-shrink: 0;
}
.kv-inst:hover {
  opacity: 0.86;
}
.kv-inst.done,
.kv-inst:disabled {
  background: var(--mat-wash);
  color: var(--text-muted);
  cursor: default;
  opacity: 1;
}

/* 开关：沿用任务卡 sv-switch 视觉（30×17 胶囊 + knob），scoped 不复用类故本地补齐 */
.sv-switch {
  margin-left: auto;
  width: 30px;
  height: 17px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-hover);
  position: relative;
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
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

/* ===== 空态 ===== */
.kv-empty {
  padding: 70px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.9;
}
.kv-empty > i {
  font-size: 20px;
  opacity: 0.55;
  margin-bottom: 8px;
}
.kve-title {
  color: var(--text-secondary);
}
.kve-hint {
  font-size: 11px;
  max-width: 380px;
  margin: 0 auto;
}
.kve-hot {
  display: flex;
  justify-content: center;
  gap: 7px;
  margin-top: 12px;
}
.kv-hot {
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  padding: 4px 12px;
  border-radius: 20px;
  cursor: pointer;
}
.kv-hot:hover {
  border-color: var(--mat-accent);
  color: var(--mat-accent);
}

/* ===== 市场空态 · 结果预演（I）：中帧实、两侧退晕，演示搜索后会得到什么 ===== */
.kve-preview {
  padding: 44px 0;
}
.kve-cap {
  color: var(--text-secondary);
  margin-bottom: 16px;
}
.kve-peek {
  display: flex;
  justify-content: center;
  align-items: stretch;
  gap: 10px;
}
.kve-pk {
  width: 232px;
  padding: 12px 14px 10px;
  cursor: default;
  transition: transform 0.18s ease, opacity 0.18s ease, box-shadow 0.18s ease;
}
.kve-pk.ghost {
  opacity: 0.45;
  transform: scale(0.94);
  pointer-events: none;
}
.kve-pk.mid {
  cursor: pointer;
}
.kve-pk.mid:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.1);
}
.kve-try {
  margin-top: 12px;
  font-size: 10.5px;
  color: var(--text-muted);
}

/* ===== 详情二级页（kd-）：可点已安装卡 → SKILL.md 正文 + 脚本/附属文档清单 ===== */
.kd-go {
  cursor: pointer;
}
.kd-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 0 6px;
}
.kd-title-row .kv-tile {
  width: 30px;
  height: 30px;
  font-size: 13px;
  border-radius: 8px;
}
.kd-name {
  margin: 0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
/* 版本徽章：frontmatter version 有值才出现，强调色底与卡内灰 chip 区分 */
.kd-ver {
  background: var(--mat-accent-wash);
  color: var(--mat-accent);
  font-size: 10.5px;
  padding: 3px 9px;
}
.kd-title-row .sv-switch {
  margin-left: 0;
}
.kd-act {
  width: 28px;
  height: 28px;
  font-size: 12px;
}
.kd-desc {
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kd-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  padding: 10px 0 16px;
  border-bottom: 1px solid var(--border-color);
}
.kd-path {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: var(--text-muted);
  margin-right: 4px;
}
.kd-body {
  padding-top: 22px;
}
.kd-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.kd-card {
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-surface);
  padding: 18px 24px 20px;
}
.kd-cap {
  display: flex;
  align-items: center;
  gap: 7px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 14px;
}
.kd-cap i {
  font-size: 10px;
}
.kd-md > :deep(*:first-child) {
  margin-top: 0;
}
.kd-md > :deep(*:last-child) {
  margin-bottom: 0;
}
.kd-line {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding: 9px 0;
}
.kd-line + .kd-line {
  border-top: 1px dashed var(--border-color);
}
.kd-file {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  flex-shrink: 0;
}
.kd-dsc {
  font-size: 11.5px;
  color: var(--text-secondary);
  line-height: 1.65;
  min-width: 0;
}
</style>
