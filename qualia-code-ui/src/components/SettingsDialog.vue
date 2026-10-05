<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getAgentMd, getConfig, installMarketSkill, listSkills, listTools, revealSkillFolder, saveAgentMd, searchMarketSkills } from '@/api/config'
import { renderMarkdown } from '@/utils/markdown'
import { useConfigStore } from '@/stores/config'
import type { MarketSkillInfo, ModelConfig, ModelType, McpTransport, SkillInfo, ToolInfo } from '@/types'

/**
 * 全局配置弹窗（~/.qualia/qualia-code.json，与工作区无关）。
 * 「模型 / MCP / 技能 / 工具」四个 Tab；模型 Tab 为卡片网格 + 编辑子弹窗，
 * 弹窗内保存仅写本地草稿，持久化统一由「保存配置」完成（旧 settings.js 平移）。
 * 组件由父级 v-if 控制挂载，每次打开重新拉取（apiKey 因此总以掩码回显）。
 */
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
const config = useConfigStore()

// ===== 预设（对齐旧 MODEL_TYPES / MODEL_PRESETS；label 存字典 key，渲染时翻译保证切语言同步）=====
const MODEL_TYPES: { id: ModelType; label: string }[] = [
  { id: 'pay-as-you-go', label: 'settings.typePayAsYouGo' },
  { id: 'token-plan', label: 'settings.typeTokenPlan' },
]
const MODEL_PRESETS: Record<string, { label: string; models: string[]; types: ModelType[] }> = {
  dashscope: {
    label: 'settings.presetDashscope',
    models: ['qwen3.7-plus', 'qwen-max-latest', 'qwen-plus-latest', 'qwen3-coder-plus'],
    types: ['pay-as-you-go'],
  },
  deepseek: { label: 'settings.presetDeepSeek', models: ['deepseek-chat', 'deepseek-reasoner'], types: ['pay-as-you-go'] },
  xiaomi: { label: 'settings.presetXiaomi', models: ['MiMo'], types: ['pay-as-you-go', 'token-plan'] },
  openai: { label: 'settings.presetOpenAI', models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo'], types: ['pay-as-you-go'] },
}
const TRANSPORTS: McpTransport[] = ['streamable-http', 'http-sse', 'stdio']
const PROVIDER_DOT_CLASS: Record<string, string> = {
  dashscope: 'mc-dot-dashscope',
  deepseek: 'mc-dot-deepseek',
  xiaomi: 'mc-dot-xiaomi',
  openai: 'mc-dot-openai',
}
/** 工具图标：双字母 mono 块（跨平台渲染一致），未知工具回退 TL */
const TOOL_ICONS: Record<string, string> = {
  read: 'RD', grep: 'GP', glob: 'GL', replace: 'RW', write: 'WR',
  delete_file: 'DF', bash: 'SH', web_fetch: 'WF', baidu_search: 'BS', http: 'HT',
}
const CATEGORY_LABELS: Record<string, string> = {
  file: 'settings.categoryFile',
  network: 'settings.categoryNetwork',
  other: 'settings.categoryOther',
}

const TABS = [
  { id: 'models', icon: 'fa-robot', label: 'settings.tabModels' },
  { id: 'mcp', icon: 'fa-server', label: 'settings.tabMcp' },
  { id: 'skills', icon: 'fa-shapes', label: 'settings.tabSkills' },
  { id: 'tools', icon: 'fa-wrench', label: 'settings.tabTools' },
  { id: 'app', icon: 'fa-gears', label: 'settings.tabApp' },
] as const
type TabId = (typeof TABS)[number]['id']

/** MCP 草稿：headers 平铺为 {k,v}[] 便于行内编辑 */
interface McpDraft {
  name: string
  transport: McpTransport
  url: string
  enabled: boolean
  headers: { k: string; v: string }[]
}

// ===== 草稿状态（保存前不落库）=====
const loading = ref(true)
const loadError = ref(false)
const activeTab = ref<TabId>('models')
const defaultIndex = ref(-1) // 指向默认模型，避免改名后丢失默认标记
const models = ref<ModelConfig[]>([])
const mcpServers = ref<McpDraft[]>([])
const disabledSkills = ref<string[]>([])
const disabledTools = ref<string[]>([])
const skills = ref<SkillInfo[]>([])
const tools = ref<ToolInfo[]>([])
// 技能市场（skills.sh 代理搜索 + GitHub 技能包安装）
const skillView = ref<'installed' | 'market'>('installed')
const marketQuery = ref('')
const marketResults = ref<MarketSkillInfo[]>([])
const marketLoading = ref(false)
const marketError = ref('')
const marketSearched = ref(false)
const installingId = ref('')
/** 市场空态快捷词，点击直接搜索 */
const HOT_KEYWORDS = ['pdf', 'docx', 'frontend', 'testing', 'writing']

// ===== 应用 Tab：AGENT.md 系统提示词在线编辑（保存即热生效） =====
const agentMd = ref('')
const agentMdExists = ref(false)
const agentMdDefault = ref('')
const agentMdSaving = ref(false)
const agentMdTip = ref('')
const agentMdEditing = ref(false)
const agentMdSaved = ref('')
/** 预览模式：markdown 渲染结果 */
const agentMdPreviewHtml = computed(() => renderMarkdown(agentMd.value || ''))
/** 进入编辑：快照当前内容，取消时可回滚 */
function startEditAgentMd() {
  agentMdSaved.value = agentMd.value
  agentMdEditing.value = true
}
/** 取消编辑：回滚快照并返回预览 */
function cancelAgentMdEdit() {
  agentMd.value = agentMdSaved.value
  agentMdTip.value = ''
  agentMdEditing.value = false
}

onMounted(async () => {
  try {
    const cfg = await getConfig()
    models.value = (cfg.models ?? []).map((m) => ({
      name: m.name ?? '',
      provider: m.provider || 'dashscope',
      // 仅开放按量付费，历史配置中的其他类型归一化
      type: MODEL_TYPES.some((x) => x.id === m.type) ? m.type : 'pay-as-you-go',
      model: m.model ?? '',
      baseUrl: m.baseUrl ?? '',
      apiKey: m.apiKey ?? '',
    }))
    let di = models.value.findIndex((m) => m.name === cfg.defaultModel)
    if (di < 0) di = models.value.length > 0 ? 0 : -1
    defaultIndex.value = di
    mcpServers.value = (cfg.mcpServers ?? []).map((s) => ({
      name: s.name ?? '',
      transport: s.transport ?? 'streamable-http',
      url: s.url ?? '',
      enabled: s.enabled !== false,
      headers: Object.entries(s.headers ?? {}).map(([k, v]) => ({ k, v })),
    }))
    disabledSkills.value = [...(cfg.disabledSkills ?? [])]
    disabledTools.value = [...(cfg.disabledTools ?? [])]
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
  snapshot.value = dumpDraft()
  // 技能与工具为独立接口，拉取失败不阻断配置展示
  try {
    skills.value = await listSkills()
  } catch { /* 忽略 */ }
  try {
    tools.value = await listTools()
  } catch { /* 忽略 */ }
  try {
    const md = await getAgentMd()
    agentMdExists.value = md.exists
    agentMdDefault.value = md.defaultPrompt
    agentMd.value = md.exists ? md.content : md.defaultPrompt
    agentMdSaved.value = agentMd.value
  } catch { /* 忽略 */ }
})

/** 保存系统提示词（空内容 = 删除文件回退默认） */
async function saveAgentMdNow(content = agentMd.value) {
  agentMdSaving.value = true
  try {
    await saveAgentMd(content)
    agentMd.value = content.trim() ? content : agentMdDefault.value
    agentMdExists.value = content.trim().length > 0
    agentMdTip.value = agentMdExists.value ? t('settings.appSaved') : t('settings.appResetDone')
    agentMdSaved.value = agentMd.value
    agentMdEditing.value = false
  } catch (err) {
    agentMdTip.value = err instanceof Error ? err.message : String(err)
  } finally {
    agentMdSaving.value = false
  }
}

// ===== 展示辅助 =====
function providerLabel(provider: string): string {
  const lbl = MODEL_PRESETS[provider]?.label
  return (lbl && t(lbl)) || provider || t('settings.unknownProvider')
}
function dotClass(provider: string): string {
  return PROVIDER_DOT_CLASS[provider] ?? 'mc-dot-custom'
}
function dotLetter(m: ModelConfig): string {
  const c = providerLabel(m.provider).trim()[0]
  return (c || '?').toUpperCase()
}
function typeLabel(ty: ModelType): string {
  const found = MODEL_TYPES.find((x) => x.id === ty)
  return found ? t(found.label) : ty
}
/** apiKey 脱敏：仅保留首 4 位与末 2 位（后端掩码值原样展示） */
function maskKey(k: string): string {
  const s = String(k || '')
  if (!s) return ''
  if (s.includes('*')) return s
  if (s.length <= 6) return s.slice(0, 2) + '****'
  return s.slice(0, 4) + '****' + s.slice(-2)
}

// ===== 模型列表操作 =====
function setDefaultModel(i: number) {
  defaultIndex.value = i
}
function delModel(i: number) {
  models.value.splice(i, 1)
  if (defaultIndex.value === i) defaultIndex.value = models.value.length > 0 ? 0 : -1
  else if (defaultIndex.value > i) defaultIndex.value--
}

// ===== 模型编辑子弹窗 =====
const editIndex = ref(-1) // -1 表示新增
const editDraft = ref<ModelConfig | null>(null)
const dlgKeyVisible = ref(false)
const dlgErr = ref(false)
const providerOpen = ref(false)
let errTimer: number | undefined

const providerIds = computed(() => {
  const ids = Object.keys(MODEL_PRESETS)
  const p = editDraft.value?.provider
  // 配置中出现预设外的厂商标识时附加为选项，避免回显丢失
  return p && !ids.includes(p) ? [...ids, p] : ids
})
const allowedTypes = computed<ModelType[] | null>(() => MODEL_PRESETS[editDraft.value?.provider ?? '']?.types ?? null)
const modelOptions = computed(() => MODEL_PRESETS[editDraft.value?.provider ?? '']?.models ?? [])
const dlgSaveText = computed(() => (dlgErr.value ? t('settings.nameRequired') : t('common.save')))

function openEditDialog(i: number) {
  editIndex.value = i
  const firstModel = MODEL_PRESETS.dashscope?.models[0] ?? ''
  editDraft.value = i >= 0 && models.value[i]
    ? { ...models.value[i]! }
    : { name: '', provider: 'dashscope', type: 'pay-as-you-go', model: firstModel, baseUrl: '', apiKey: '' }
  dlgKeyVisible.value = false
  providerOpen.value = false
}
function closeEditDialog() {
  editDraft.value = null
  providerOpen.value = false
}
/** 切换厂商：模型回显该厂商首个常用模型，类型越界时归一化 */
function selectProvider(pid: string) {
  const d = editDraft.value
  if (!d) return
  d.provider = pid
  const p = MODEL_PRESETS[pid]
  d.model = p && p.models.length ? p.models[0]! : ''
  if (p && p.types && !p.types.includes(d.type)) d.type = p.types[0]!
  providerOpen.value = false
}
function saveEditDialog() {
  const d = editDraft.value
  if (!d) return
  if (!String(d.name ?? '').trim()) {
    dlgErr.value = true
    window.clearTimeout(errTimer)
    errTimer = window.setTimeout(() => { dlgErr.value = false }, 1600)
    return
  }
  if (editIndex.value >= 0) models.value[editIndex.value] = d
  else {
    models.value.push(d)
    if (defaultIndex.value < 0) defaultIndex.value = models.value.length - 1
  }
  closeEditDialog()
}
function delInDialog() {
  const i = editIndex.value
  if (i < 0) return
  const m = models.value[i]
  if (!m) return
  if (!window.confirm(t('settings.deleteModelConfirm', { name: m.name || m.model }))) return
  delModel(i)
  closeEditDialog()
}

// ===== MCP =====
function addMcp() {
  mcpServers.value.push({ name: '', transport: 'streamable-http', url: '', headers: [], enabled: true })
}

// ===== 技能 / 工具开关 =====
function skillEnabled(s: SkillInfo): boolean {
  return s.enabled !== false && !disabledSkills.value.includes(s.name)
}
function skillScripts(s: SkillInfo): string[] {
  return (s.scripts ?? []).filter(Boolean)
}
function toggleSkill(name: string, enabled: boolean) {
  if (enabled) disabledSkills.value = disabledSkills.value.filter((n) => n !== name)
  else if (!disabledSkills.value.includes(name)) disabledSkills.value.push(name)
}
async function onDeleteSkill(s: SkillInfo) {
  if (!window.confirm(t('settings.uninstallConfirm', { name: s.name }))) return
  try {
    await config.deleteSkill(s.dir || s.name) // 按目录名删除 + 重新拉取技能列表
    skills.value = [...config.skills]
    disabledSkills.value = disabledSkills.value.filter((n) => n !== s.name)
  } catch (e) {
    window.alert(t('settings.deleteFailed', { msg: e instanceof Error ? e.message : String(e) }))
  }
}
// ===== 技能市场 =====
function formatInstalls(n: number): string {
  return n >= 1000 ? `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k` : String(n)
}
async function searchMarket() {
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
function quickSearch(word: string) {
  marketQuery.value = word
  searchMarket()
}
async function onInstallSkill(m: MarketSkillInfo) {
  if (!m.installable || m.installed || installingId.value) return
  installingId.value = m.id
  try {
    await installMarketSkill(m.id)
    m.installed = true
    skills.value = await listSkills() // 安装落盘后刷新已安装列表
  } catch (e) {
    window.alert(t('settings.installFailed', { msg: e instanceof Error ? e.message : String(e) }))
  } finally {
    installingId.value = ''
  }
}
async function onRevealSkill(s: SkillInfo) {
  try {
    await revealSkillFolder(s.dir || s.name)
  } catch (e) {
    window.alert(t('settings.openFolderFailed', { msg: e instanceof Error ? e.message : String(e) }))
  }
}
function toolEnabled(name: string): boolean {
  return !disabledTools.value.includes(name)
}
function toggleTool(name: string, enabled: boolean) {
  if (enabled) disabledTools.value = disabledTools.value.filter((n) => n !== name)
  else if (!disabledTools.value.includes(name)) disabledTools.value.push(name)
}
const toolGroups = computed(() => {
  const groups: { cat: string; label: string; tools: ToolInfo[] }[] = []
  const byCat = new Map<string, ToolInfo[]>()
  for (const tl of tools.value) {
    const cat = tl.category || 'other'
    const arr = byCat.get(cat)
    if (arr) arr.push(tl)
    else byCat.set(cat, [tl])
  }
  for (const [cat, arr] of byCat) {
    const key = CATEGORY_LABELS[cat]
    groups.push({ cat, label: key ? t(key) : cat, tools: arr })
  }
  return groups
})

// ===== D2 IDE 设置风：导航分组搜索 + 数量徽章 + 未保存指示 =====
const searchKw = ref('')
const NAV_GROUPS = [
  { key: 'svc', label: 'settings.groupService', items: ['models', 'mcp', 'app'] },
  { key: 'ext', label: 'settings.groupExtend', items: ['skills', 'tools'] },
] as const
const metaOf = (id: TabId) => TABS.find((x) => x.id === id)!
const navGroups = computed(() => {
  const kw = searchKw.value.trim().toLowerCase()
  return NAV_GROUPS.map((grp) => ({
    ...grp,
    items: grp.items
      .filter((id) => {
        if (!kw) return true
        return t(metaOf(id).label).toLowerCase().includes(kw) || id.includes(kw)
      })
      .map((id) => ({
        id,
        icon: metaOf(id).icon,
        label: metaOf(id).label,
      })),
  })).filter((grp) => grp.items.length > 0)
})
/** 搜索回车：跳到首个匹配 Tab */
function jumpSearch() {
  const first = navGroups.value[0]?.items[0]?.id
  if (first) activeTab.value = first
}

/** 内容区节标题行（标题 + 动态副标题） */
const secMeta = computed(() => {
  if (loading.value || loadError.value) return null
  switch (activeTab.value) {
    case 'models': {
      const d = models.value[defaultIndex.value]?.name || models.value[defaultIndex.value]?.model || '—'
      return {
        title: t('settings.tabModels'),
        sub: models.value.length ? t('settings.secModelsDyn', { n: models.value.length, d }) : t('settings.secModelsEmpty'),
      }
    }
    case 'mcp':
      return {
        title: t('settings.tabMcp'),
        sub: mcpServers.value.length ? t('settings.secMcpSub', { n: mcpServers.value.length }) : '',
      }
    case 'skills':
      return { title: t('settings.tabSkills'), sub: skills.value.length ? t('settings.secSkillsSub', { n: skills.value.length }) : '' }
    case 'app':
      return { title: t('settings.tabApp'), sub: t('settings.secAppSub') }
    default:
      return { title: t('settings.tabTools'), sub: tools.value.length ? t('settings.secToolsSub', { n: tools.value.length }) : '' }
  }
})

/** 未保存更改：与载入快照对比（草稿式保存的脏标记） */
const snapshot = ref('')
function dumpDraft(): string {
  return JSON.stringify({
    models: models.value,
    mcp: mcpServers.value,
    ds: disabledSkills.value,
    dt: disabledTools.value,
    di: defaultIndex.value,
  })
}
const isDirty = computed(() => snapshot.value !== '' && dumpDraft() !== snapshot.value)

// ===== 保存 =====
const saving = ref(false)
const saveState = ref<'idle' | 'busy' | 'ok' | 'err'>('idle')
const saveMsg = ref('')
let flashTimer: number | undefined
const saveBtnText = computed(() => {
  if (saveState.value === 'busy') return t('settings.saving')
  if (saveState.value === 'err') return saveMsg.value || t('settings.saveFailed')
  if (saveState.value === 'ok') return saveMsg.value || t('settings.saved')
  return t('settings.saveConfig')
})
function flash(text: string, ok: boolean) {
  saveState.value = ok ? 'ok' : 'err'
  saveMsg.value = text
  window.clearTimeout(flashTimer)
  flashTimer = window.setTimeout(() => {
    saveState.value = 'idle'
    saveMsg.value = ''
  }, 1800)
}

async function onSave() {
  // 校验失败时切到对应 Tab 提示
  if (models.value.some((m) => !String(m.name ?? '').trim())) {
    activeTab.value = 'models'
    flash(t('settings.modelNameRequired'), false)
    return
  }
  if (mcpServers.value.some((s) => !String(s.name ?? '').trim())) {
    activeTab.value = 'mcp'
    flash(t('settings.mcpNameRequired'), false)
    return
  }
  const payload = {
    defaultModel: defaultIndex.value >= 0 ? String(models.value[defaultIndex.value]?.name ?? '').trim() : '',
    // 各字段统一 String() 兜底，避免历史/新增对象缺字段时 trim 崩溃
    models: models.value.map((m) => ({
      name: String(m.name || '').trim(),
      provider: String(m.provider || '').trim(),
      type: m.type,
      model: String(m.model || '').trim(),
      baseUrl: String(m.baseUrl || '').trim(),
      apiKey: String(m.apiKey || '').trim(),
    })),
    mcpServers: mcpServers.value.map((s) => ({
      name: String(s.name || '').trim(),
      transport: s.transport,
      url: String(s.url || '').trim(),
      enabled: s.enabled !== false,
      headers: s.headers.reduce<Record<string, string>>((o, h) => {
        const k = String(h.k || '').trim()
        if (k) o[k] = String(h.v || '')
        return o
      }, {}),
    })),
    disabledSkills: [...disabledSkills.value],
    disabledTools: [...disabledTools.value],
  }
  saving.value = true
  saveState.value = 'busy'
  try {
    const res = await config.save(payload) // 成功后 store 回读并重置模型选择（等价旧 refreshModelSelector）
    if (res.success) {
      emit('close') // 组件卸载，下次打开重新拉取（apiKey 回显掩码）
    } else {
      flash(res.message || t('settings.saveFailed'), false)
    }
  } catch {
    flash(t('settings.saveFailed'), false)
  } finally {
    saving.value = false
  }
}

// ===== 全局监听：Esc 分层关闭 / 点击下拉外部收起 =====
function onDocKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  if (editDraft.value) {
    closeEditDialog()
    return
  }
  emit('close')
}
function onDocClick(e: MouseEvent) {
  if (!(e.target as HTMLElement | null)?.closest('.custom-select')) providerOpen.value = false
}
onMounted(() => {
  document.addEventListener('keydown', onDocKey)
  document.addEventListener('click', onDocClick)
})
onUnmounted(() => {
  document.removeEventListener('keydown', onDocKey)
  document.removeEventListener('click', onDocClick)
  window.clearTimeout(flashTimer)
  window.clearTimeout(errTimer)
})
</script>

<template>
  <div class="settings-overlay open" @click.self="emit('close')">
    <div class="settings-dialog">
      <div class="settings-dialog-head">
        <h4>{{ t('settings.title') }}</h4>
        <div class="set-search">
          <i class="fas fa-search"></i>
          <input
            v-model="searchKw"
            :placeholder="t('settings.searchPlaceholder')"
            spellcheck="false"
            @keydown.enter.prevent="jumpSearch"
          />
          <span class="set-search-kb">⌘K</span>
        </div>
        <button :title="t('common.close')" @click="emit('close')"><i class="fas fa-times"></i></button>
      </div>
      <div class="settings-main">
        <div class="settings-tabs">
          <template v-for="grp in navGroups" :key="grp.key">
            <div class="nav-group-label">{{ t(grp.label) }}</div>
            <button
              v-for="item in grp.items" :key="item.id" class="settings-tab" :class="{ active: activeTab === item.id }"
              @click="activeTab = item.id"
            >
              <i class="fas" :class="item.icon"></i>
              <span class="nav-txt">{{ t(item.label) }}</span>
            </button>
          </template>
          <div v-if="!navGroups.length" class="nav-empty">{{ t('settings.searchNoMatch') }}</div>
        </div>
        <div class="settings-content">
          <div class="settings-body">
            <div v-if="secMeta" class="sec-head">
              <div class="sec-title">{{ secMeta.title }}</div>
              <div class="sec-sub">{{ secMeta.sub }}</div>
            </div>
            <div v-if="loading" class="set-empty"><i class="fas fa-sliders-h"></i><span>{{ t('settings.loading') }}</span></div>
            <div v-else-if="loadError" class="set-empty">
              <i class="fas fa-exclamation-circle"></i><span>{{ t('settings.loadFailed') }}</span>
            </div>

            <!-- 模型 Tab：双列卡片网格（与工具 Tab 同规格） -->
            <template v-else-if="activeTab === 'models'">
              <div class="model-grid">
                <div
                  v-for="(m, i) in models" :key="i" class="model-card" :class="{ 'is-default': i === defaultIndex }"
                  :title="t('settings.clickToEdit')" @click="openEditDialog(i)"
                >
                  <div class="mc-head">
                    <span class="mc-provider">
                      <span class="dot" :class="dotClass(m.provider)">{{ dotLetter(m) }}</span>{{ providerLabel(m.provider) }}
                    </span>
                    <span v-if="i === defaultIndex" class="mc-badge-default">{{ t('settings.defaultBadge') }}</span>
                  </div>
                  <div class="mc-model-id">{{ m.model || t('settings.noModelSet') }}</div>
                  <div class="mc-name">{{ m.name || t('settings.unnamed') }}</div>
                  <div class="mc-meta">
                    <span class="mc-tag">{{ typeLabel(m.type) }}</span>
                    <span v-if="m.apiKey" class="mc-tag">{{ maskKey(m.apiKey) }}</span>
                    <span v-else class="mc-tag warn">{{ t('settings.apiKeyMissing') }}</span>
                  </div>
                  <div class="mc-actions" @click.stop>
                    <button class="mc-btn primary" @click="openEditDialog(i)">{{ t('settings.edit') }}</button>
                    <button v-if="i !== defaultIndex" class="mc-btn ghost" @click="setDefaultModel(i)">{{ t('settings.setDefault') }}</button>
                    <button class="mc-btn ghost danger" @click="delModel(i)">{{ t('common.delete') }}</button>
                  </div>
                </div>
                <div class="model-card add-card" :title="t('settings.addModel')" @click="openEditDialog(-1)">
                  <span class="plus"><i class="fas fa-plus"></i></span>
                  <p>{{ t('settings.addModel') }}</p>
                </div>
              </div>
              <div v-if="!models.length" class="set-env-item">{{ t('settings.noModels') }}</div>
            </template>

            <!-- MCP Tab -->
            <template v-else-if="activeTab === 'mcp'">
              <div>
                <div v-if="!mcpServers.length" class="set-env-item">{{ t('settings.noMcp') }}</div>
                <div v-for="(s, i) in mcpServers" :key="i" class="set-card" :class="{ disabled: !s.enabled }">
                  <div class="set-card-head">
                    <div class="set-card-info"><span class="mcp-name">{{ s.name || t('settings.mcpFallbackName') }}</span></div>
                  </div>
                  <div class="set-hrow">
                    <label>{{ t('settings.nameLabel') }}</label>
                    <input v-model="s.name" :placeholder="t('settings.namePlaceholder')" />
                  </div>
                  <div class="set-hrow">
                    <label>{{ t('settings.transport') }}</label>
                    <select v-model="s.transport">
                      <option v-for="tr in TRANSPORTS" :key="tr" :value="tr">{{ tr }}</option>
                    </select>
                  </div>
                  <div class="set-hrow">
                    <label>{{ t('settings.urlLabel') }}</label>
                    <input v-model="s.url" placeholder="http(s)://..." />
                  </div>
                  <div class="set-hrow top">
                    <label>Headers</label>
                    <div class="set-hrow-fields">
                      <div v-for="(h, j) in s.headers" :key="j" class="set-kv">
                        <input v-model="h.k" :placeholder="t('settings.headerName')" />
                        <input v-model="h.v" :placeholder="t('settings.headerValue')" />
                        <button class="set-kv-del" :title="t('common.delete')" @click="s.headers.splice(j, 1)">
                          <i class="fas fa-times"></i>
                        </button>
                      </div>
                      <button class="set-add-btn mini" @click="s.headers.push({ k: '', v: '' })">
                        <i class="fas fa-plus"></i> {{ t('settings.addHeader') }}
                      </button>
                    </div>
                  </div>
                  <div class="set-card-footer">
                    <button class="set-card-del" :title="t('common.delete')" @click="mcpServers.splice(i, 1)">
                      <i class="far fa-trash-alt"></i> {{ t('common.delete') }}
                    </button>
                    <label class="toggle-switch" :title="s.enabled ? t('settings.disableTip') : t('settings.enableTip')">
                      <input v-model="s.enabled" type="checkbox" />
                      <span class="toggle-slider"></span>
                    </label>
                  </div>
                </div>
                <button class="set-add-btn" @click="addMcp"><i class="fas fa-plus"></i> {{ t('settings.addMcp') }}</button>
              </div>
            </template>

            <!-- 技能 Tab：已安装列表 / 技能市场双子视图 -->
            <template v-else-if="activeTab === 'skills'">
              <div class="skill-view-switch">
                <button class="sv-btn" :class="{ active: skillView === 'installed' }" @click="skillView = 'installed'">
                  {{ t('settings.installedSkills') }}<span class="sv-count">{{ skills.length }}</span>
                </button>
                <button class="sv-btn" :class="{ active: skillView === 'market' }" @click="skillView = 'market'">
                  {{ t('settings.skillMarket') }}
                </button>
              </div>

              <!-- 已安装 -->
              <template v-if="skillView === 'installed'">
                <div v-if="!skills.length" class="set-empty">
                  <i class="fas fa-shapes"></i>
                  <span>{{ t('settings.noSkills') }}</span>
                  <span class="skill-hint">{{ t('settings.skillHint') }}</span>
                </div>
                <div v-else>
                  <div v-for="s in skills" :key="s.name" class="set-card" :class="{ disabled: !skillEnabled(s) }">
                    <div class="set-card-head">
                      <div class="set-card-info">
                        <span class="skill-name">{{ s.name }}</span>
                        <span class="skill-src">{{ t('settings.global') }}</span>
                      </div>
                    </div>
                    <div class="skill-desc">{{ s.description || t('settings.noDesc') }}</div>
                    <div v-if="skillScripts(s).length" class="set-row">
                      <div class="skill-sub">{{ t('settings.scripts') }}</div>
                      <ul class="skill-list">
                        <li v-for="x in skillScripts(s)" :key="x">{{ x }}</li>
                      </ul>
                    </div>
                    <div v-if="s.references?.length" class="set-row">
                      <div class="skill-sub">{{ t('settings.references') }}</div>
                      <div class="skill-refs">
                        <span v-for="x in s.references" :key="x" class="skill-ref">{{ x }}</span>
                      </div>
                    </div>
                    <div class="set-card-footer">
                      <div class="set-card-actions">
                        <button class="set-card-act" :title="t('settings.openFolderTitle')" @click="onRevealSkill(s)">
                          <i class="far fa-folder-open"></i> {{ t('settings.openFolder') }}
                        </button>
                        <button class="set-card-del" :title="t('settings.uninstallTitle')" @click="onDeleteSkill(s)">
                          <i class="far fa-trash-alt"></i> {{ t('settings.uninstall') }}
                        </button>
                      </div>
                      <label class="toggle-switch" :title="skillEnabled(s) ? t('settings.disableTip') : t('settings.enableTip')">
                        <input
                          type="checkbox" :checked="skillEnabled(s)"
                          @change="toggleSkill(s.name, ($event.target as HTMLInputElement).checked)"
                        />
                        <span class="toggle-slider"></span>
                      </label>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 技能市场 -->
              <template v-else>
                <div class="market-search">
                  <i class="fas fa-magnifying-glass"></i>
                  <input
                    v-model="marketQuery"
                    :placeholder="t('settings.marketSearchPlaceholder')"
                    @keydown.enter="searchMarket()"
                  />
                  <button class="market-go" :disabled="marketLoading || !marketQuery.trim()" @click="searchMarket()">
                    {{ t('settings.searchBtn') }}
                  </button>
                </div>
                <div v-if="marketLoading" class="set-empty">
                  <i class="fas fa-circle-notch fa-spin"></i>
                </div>
                <div v-else-if="marketError" class="set-empty market-err">
                  <i class="fas fa-triangle-exclamation"></i>
                  <span>{{ marketError }}</span>
                </div>
                <div v-else-if="!marketSearched" class="set-empty">
                  <i class="fas fa-store"></i>
                  <span>{{ t('settings.marketHint') }}</span>
                  <div class="hot-words">
                    <button v-for="w in HOT_KEYWORDS" :key="w" class="hot-word" @click="quickSearch(w)">{{ w }}</button>
                  </div>
                </div>
                <div v-else-if="!marketResults.length" class="set-empty">
                  <i class="fas fa-ghost"></i>
                  <span>{{ t('settings.noMarketResults') }}</span>
                </div>
                <div v-else class="market-list">
                  <div v-for="m in marketResults" :key="m.id" class="market-item">
                    <span class="mi-name" :title="m.id">{{ m.name }}</span>
                    <span class="mi-source" :title="m.source">{{ m.source }}</span>
                    <span class="mi-installs"><i class="fas fa-download"></i>{{ formatInstalls(m.installs) }}</span>
                    <button v-if="m.installed" class="mi-btn is-installed" disabled>
                      <i class="fas fa-check"></i> {{ t('settings.installedTag') }}
                    </button>
                    <button
                      v-else class="mi-btn"
                      :disabled="!m.installable || installingId === m.id"
                      :title="m.installable ? '' : t('settings.notInstallableTip')"
                      @click="onInstallSkill(m)"
                    >
                      <i :class="installingId === m.id ? 'fas fa-circle-notch fa-spin' : 'fas fa-download'"></i>
                      {{ m.installable ? t('settings.installBtn') : t('settings.notInstallable') }}
                    </button>
                  </div>
                </div>
              </template>
            </template>

            <!-- 应用 Tab：AGENT.md 预览/编辑双模式（保存即热生效） -->
            <template v-else-if="activeTab === 'app'">
              <div class="app-md-hint">
                <i class="fas fa-circle-info"></i>
                <span>{{ t('settings.appHint') }}<code>~/.qualia/code/AGENT.md</code></span>
                <span v-if="!agentMdExists" class="app-md-tag">{{ t('settings.appDefaultTag') }}</span>
                <span v-if="agentMdTip" class="app-md-tip">{{ agentMdTip }}</span>
                <span class="spring"></span>
                <button v-if="!agentMdEditing" class="sf-btn" @click="startEditAgentMd">
                  <i class="fas fa-pen"></i> {{ t('settings.appEdit') }}
                </button>
                <template v-else>
                  <button class="sf-btn" :disabled="agentMdSaving" @click="cancelAgentMdEdit">{{ t('settings.appCancel') }}</button>
                  <button class="sf-btn primary" :disabled="agentMdSaving" @click="saveAgentMdNow()">{{ t('settings.appSave') }}</button>
                </template>
              </div>
              <!-- 预览模式 -->
              <div v-if="!agentMdEditing" class="app-md-preview" v-html="agentMdPreviewHtml"></div>
              <!-- 编辑模式 -->
              <textarea v-else v-model="agentMd" class="app-md-editor" spellcheck="false"></textarea>
            </template>

            <!-- 工具 Tab：双列卡片网格，整卡可点切换 -->
            <template v-else>
              <div v-if="!toolGroups.length" class="set-empty">
                <i class="fas fa-wrench"></i><span>{{ t('settings.noTools') }}</span>
              </div>
              <template v-else>
                <div v-for="g in toolGroups" :key="g.cat" class="tool-cat">
                  <div class="tool-cat-head">{{ g.label }} · {{ g.tools.length }}</div>
                  <div class="tool-grid">
                    <div
                      v-for="tl in g.tools" :key="tl.name"
                      class="tool-card" :class="{ off: !toolEnabled(tl.name) }"
                      :title="toolEnabled(tl.name) ? t('settings.disableTip') : t('settings.enableTip')"
                      @click="toggleTool(tl.name, !toolEnabled(tl.name))"
                    >
                      <span class="tool-ic">{{ TOOL_ICONS[tl.name] ?? 'TL' }}</span>
                      <span class="tool-txt">
                        <span class="tool-name">
                          {{ tl.name }}
                          <span v-if="!toolEnabled(tl.name)" class="tool-off-tag">{{ t('settings.disabled') }}</span>
                        </span>
                        <span class="tool-desc">{{ tl.description }}</span>
                      </span>
                      <label class="toggle-switch" @click.stop.prevent>
                        <input type="checkbox" :checked="toolEnabled(tl.name)" tabindex="-1" @click.stop.prevent />
                        <span class="toggle-slider"></span>
                      </label>
                    </div>
                  </div>
                </div>
                <div class="tool-note"><i class="fas fa-info-circle"></i><span><strong>{{ t('settings.toolsTipLabel') }}</strong>{{ t('settings.toolsTipBody') }}</span></div>
              </template>
            </template>
          </div>
          <div class="settings-footer">
            <span v-if="isDirty && saveState === 'idle'" class="dirty"><span class="pt"></span>{{ t('settings.dirty') }}</span>
            <span class="spring"></span>
            <button class="sf-btn" @click="emit('close')">{{ t('common.cancel') }}</button>
            <button class="sf-btn primary" :class="{ saved: saveState === 'ok', err: saveState === 'err' }" :disabled="saving" @click="onSave">
              {{ saveBtnText }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 模型编辑子弹窗（叠于设置弹窗之上） -->
    <div v-if="editDraft" class="model-edit-overlay open" @click.self="closeEditDialog">
      <div class="model-edit-dialog">
        <div class="med-head">
          <h4>
            <i class="fas" :class="editIndex < 0 ? 'fa-plus' : 'fa-pen'"></i>
            {{ editIndex < 0 ? t('settings.addModel') : t('settings.editModel') }}
          </h4>
          <button class="med-close" :title="t('common.close')" @click="closeEditDialog"><i class="fas fa-times"></i></button>
        </div>
        <div class="med-body">
          <div class="med-row">
            <div class="med-field">
              <label>{{ t('settings.nameLabel') }}</label>
              <input v-model="editDraft.name" :placeholder="t('settings.nameCustom')" />
            </div>
            <div class="med-field">
              <label>{{ t('settings.serviceType') }}</label>
              <div class="med-seg">
                <button
                  v-for="mt in MODEL_TYPES" :key="mt.id" type="button"
                  :class="{ on: editDraft.type === mt.id }"
                  :disabled="allowedTypes ? !allowedTypes.includes(mt.id) : false"
                  @click="editDraft.type = mt.id"
                >
                  {{ t(mt.label) }}
                </button>
              </div>
            </div>
          </div>
          <div class="med-row">
            <div class="med-field">
              <label>{{ t('settings.provider') }}</label>
              <div class="custom-select" :class="{ open: providerOpen }">
                <div class="custom-select-trigger" @click="providerOpen = !providerOpen">
                  <span class="trigger-text">{{ providerLabel(editDraft.provider) }}</span>
                  <i class="fas fa-chevron-down trigger-arrow"></i>
                </div>
                <div class="custom-select-dropdown">
                  <div
                    v-for="pid in providerIds" :key="pid" class="custom-option"
                    :class="{ active: editDraft.provider === pid }" @click="selectProvider(pid)"
                  >
                    <span class="option-text">{{ providerLabel(pid) }}</span>
                    <i class="fas fa-check check-icon"></i>
                  </div>
                </div>
              </div>
            </div>
            <div class="med-field">
              <label>{{ t('settings.modelId') }}</label>
              <input v-model="editDraft.model" class="mono" list="dlg-model-opts" :placeholder="t('settings.modelIdPlaceholder')" />
              <datalist id="dlg-model-opts">
                <option v-for="v in modelOptions" :key="v" :value="v"></option>
              </datalist>
            </div>
          </div>
          <div class="med-field">
            <label>API Key<span class="hint">{{ t('settings.apiKeyHint') }}</span></label>
            <div class="med-key-wrap">
              <input
                v-model="editDraft.apiKey" class="mono" :type="dlgKeyVisible ? 'text' : 'password'"
                :placeholder="t('settings.apiKeyPlaceholder')"
              />
              <button type="button" class="med-key-toggle" @click="dlgKeyVisible = !dlgKeyVisible">
                {{ dlgKeyVisible ? t('settings.hide') : t('settings.show') }}
              </button>
            </div>
          </div>
          <div class="med-baseurl-note">
            <i class="fas fa-cog"></i> {{ t('settings.baseUrlNote') }}
            <span v-if="editDraft.baseUrl" class="mono">{{ editDraft.baseUrl }}</span>
          </div>
        </div>
        <div class="med-foot">
          <button v-if="editIndex >= 0" type="button" class="mc-btn ghost danger med-del" @click="delInDialog">{{ t('common.delete') }}</button>
          <button type="button" class="mc-btn ghost" @click="closeEditDialog">{{ t('common.cancel') }}</button>
          <button type="button" class="mc-btn primary med-save" :class="{ err: dlgErr }" @click="saveEditDialog">
            {{ dlgSaveText }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
/* ===== 设置弹窗（全局配置，与工作区无关）===== */
.settings-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    z-index: 1000;
    display: flex;
    align-items: center;
    justify-content: center;
}
.settings-dialog {
    width: min(1040px, calc(100vw - 48px));
    height: min(82vh, 774px);
    background: var(--bg-surface);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    border: 1px solid var(--border-color);
    border-radius: 14px;
    box-shadow: var(--shadow);
    display: flex;
    flex-direction: column;
    overflow: hidden;
}
/* 日间模式：弹窗底改浅灰，与白色输入/卡片形成层次（纯白过于扁平） */
body.light-theme .settings-dialog {
    background: #f6f8fa;
}
body.light-theme .model-edit-dialog {
    background: #f6f8fa;
}
.settings-dialog-head {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 14px 18px 12px;
    border-bottom: 1px solid var(--border-color);
}
.settings-dialog-head h4 {
    font-size: 13.5px;
    font-weight: 650;
    color: var(--text-primary);
}
/* D2 头部搜索：过滤左侧导航项，回车跳首个匹配 Tab */
.set-search {
    margin-left: auto;
    width: 280px;
    display: flex;
    align-items: center;
    gap: 8px;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 9px;
    padding: 6px 11px;
}
.set-search:focus-within {
    border-color: var(--accent);
}
.set-search > i {
    color: var(--text-muted);
    font-size: 11px;
}
.set-search input {
    flex: 1;
    min-width: 0;
    background: none;
    border: none;
    outline: none;
    color: var(--text-primary);
    font-size: 12px;
    font-family: inherit;
}
.set-search input::placeholder {
    color: var(--text-muted);
}
.set-search-kb {
    flex-shrink: 0;
    font-size: 10px;
    color: var(--text-muted);
    border: 1px solid var(--border-color);
    border-radius: 5px;
    padding: 1px 6px;
}
.settings-dialog-head button {
    background: transparent;
    border: none;
    color: var(--text-muted);
    font-size: 12.5px;
    padding: 4px 7px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.15s ease;
}
.settings-dialog-head button:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
}
.settings-main {
    flex: 1;
    min-height: 0;
    display: flex;
}
.settings-tabs {
    flex-shrink: 0;
    width: 216px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 12px 10px;
    border-right: 1px solid var(--border-color);
    overflow-y: auto;
}
/* D2 导航分组标题 */
.nav-group-label {
    font-size: 10px;
    font-weight: 650;
    letter-spacing: 1.2px;
    color: var(--text-muted);
    text-transform: uppercase;
    padding: 10px 10px 5px;
}
.nav-group-label:first-child {
    padding-top: 2px;
}
.settings-tab {
    background: transparent;
    border: none;
    border-radius: 8px;
    padding: 8px 10px;
    font-size: 12.5px;
    font-weight: 500;
    font-family: inherit;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 9px;
    text-align: left;
    cursor: pointer;
    transition: all 0.15s ease;
}
.settings-tab i {
    width: 13px;
    text-align: center;
    font-size: 11px;
    color: var(--text-muted);
}
.settings-tab .nav-txt {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.nav-empty {
    font-size: 11px;
    color: var(--text-muted);
    padding: 10px;
}
.settings-tab:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
}
.settings-tab.active {
    background: var(--bg-hover);
    color: var(--text-primary);
    font-weight: 600;
}
.settings-tab.active i {
    color: var(--accent);
}
.settings-content {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
}
.settings-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 15px 16px 13px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}
/* D2 节标题行：标题 + 动态副标题 */
.sec-head {
    margin-bottom: -6px;
}
.sec-title {
    font-size: 14.5px;
    font-weight: 650;
    color: var(--text-primary);
}
.sec-sub {
    font-size: 11.5px;
    color: var(--text-secondary);
    margin-top: 3px;
}
.set-empty {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 9px;
    color: var(--text-muted);
    font-size: 11.5px;
}
.set-empty i {
    font-size: 20px;
    opacity: 0.6;
}
.set-card {
    border: 1px solid var(--border-color);
    border-radius: 9px;
    background: var(--bg-input);
    padding: 11px;
    display: flex;
    flex-direction: column;
    gap: 7px;
    margin-bottom: 9px;
}
.set-card.is-default {
    border-color: var(--accent);
}
.set-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
}
.set-card-info {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex: 1;
}
.set-card-actions {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
}
.set-card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 8px;
    margin-top: 4px;
    border-top: 1px solid var(--border-color);
}
.set-card-del {
    background: transparent;
    border: none;
    color: var(--text-muted);
    font-size: 10.5px;
    padding: 4px 5px;
    border-radius: 5px;
    cursor: pointer;
    transition: color 0.15s ease, background 0.15s ease;
    opacity: 0.6;
}
.set-card-del:hover {
    opacity: 1;
    background: rgba(239, 68, 68, 0.12);
    color: var(--error);
}
.set-card-actions {
    display: flex;
    align-items: center;
    gap: 2px;
}
.set-card-act {
    background: transparent;
    border: none;
    color: var(--text-muted);
    font-size: 10.5px;
    padding: 4px 5px;
    border-radius: 5px;
    cursor: pointer;
    transition: color 0.15s ease, background 0.15s ease;
    opacity: 0.6;
}
.set-card-act:hover {
    opacity: 1;
    background: rgba(128, 128, 128, 0.15);
    color: var(--text-primary);
}
/* ===== 技能市场双子视图 ===== */
.skill-view-switch {
    display: flex;
    gap: 6px;
    margin-bottom: 10px;
}
.sv-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-family: inherit;
    font-size: 11.5px;
    font-weight: 500;
    padding: 5px 12px;
    border-radius: 7px;
    border: 1px solid var(--border-color);
    background: transparent;
    color: var(--text-muted);
    cursor: pointer;
    transition: all 0.15s ease;
}
.sv-btn:hover {
    color: var(--text-primary);
    border-color: var(--accent);
}
.sv-btn.active {
    background: var(--bg-input);
    border-color: var(--accent);
    color: var(--text-primary);
}
.sv-count {
    font-size: 10px;
    color: var(--text-muted);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    padding: 0 5px;
}
/* 应用 Tab：AGENT.md 编辑器 */
.app-md-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11.5px;
    color: var(--text-secondary);
    margin-bottom: 10px;
}
.app-md-hint code {
    font-family: 'JetBrains Mono', monospace;
    font-size: 10.5px;
    color: var(--text-muted);
    background: var(--bg-hover);
    border-radius: 4px;
    padding: 1px 6px;
}
.app-md-tag {
    font-size: 10px;
    color: var(--accent);
    border: 1px solid var(--accent);
    border-radius: 4px;
    padding: 0 5px;
}
.app-md-hint .spring {
    margin-left: auto;
}
/* 预览模式：markdown 渲染视图（与编辑器同框规格） */
.app-md-preview {
    height: 520px;
    overflow: auto;
    padding: 14px 16px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--text-primary);
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 8px;
}
.app-md-preview h1, .app-md-preview h2, .app-md-preview h3, .app-md-preview h4 {
    margin: 14px 0 8px;
    font-weight: 600;
}
.app-md-preview h1:first-child, .app-md-preview h2:first-child, .app-md-preview h3:first-child {
    margin-top: 0;
}
.app-md-preview h1 { font-size: 17px; }
.app-md-preview h2 { font-size: 15.5px; }
.app-md-preview h3 { font-size: 14px; }
.app-md-preview h4 { font-size: 13px; }
.app-md-preview p { margin: 6px 0; }
.app-md-preview ul, .app-md-preview ol {
    margin: 6px 0;
    padding-left: 22px;
}
.app-md-preview li { margin: 3px 0; }
.app-md-preview code {
    font-family: 'JetBrains Mono', monospace;
    font-size: 11.5px;
    background: var(--bg-hover);
    border-radius: 4px;
    padding: 1px 5px;
}
.app-md-preview pre {
    background: var(--bg-hover);
    border-radius: 6px;
    padding: 10px 12px;
    overflow: auto;
}
.app-md-preview pre code {
    background: none;
    padding: 0;
}
.app-md-preview blockquote {
    margin: 8px 0;
    padding: 2px 12px;
    border-left: 3px solid var(--border-color);
    color: var(--text-secondary);
}
.app-md-preview a {
    color: var(--accent);
}
.app-md-preview hr {
    border: none;
    border-top: 1px solid var(--border-color);
    margin: 12px 0;
}
.app-md-editor {
    width: 100%;
    height: 520px;
    padding: 12px 14px;
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    line-height: 1.6;
    color: var(--text-primary);
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    outline: none;
}
.app-md-editor:focus {
    border-color: var(--accent);
}
.app-md-tip {
    font-size: 11.5px;
    color: var(--text-secondary);
}
.market-search {
    display: flex;
    align-items: center;
    gap: 8px;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 9px;
    padding: 6px 8px 6px 11px;
    margin-bottom: 10px;
}
.market-search:focus-within {
    border-color: var(--accent);
}
.market-search > i {
    color: var(--text-muted);
    font-size: 11px;
}
.market-search input {
    flex: 1;
    min-width: 0;
    background: none;
    border: none;
    outline: none;
    color: var(--text-primary);
    font-size: 12px;
    font-family: inherit;
}
.market-search input::placeholder {
    color: var(--text-muted);
}
.market-go {
    flex-shrink: 0;
    font-family: inherit;
    font-size: 11px;
    font-weight: 500;
    padding: 4px 12px;
    border-radius: 6px;
    border: 1px solid var(--border-color);
    background: transparent;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all 0.15s ease;
}
.market-go:hover:not(:disabled) {
    color: var(--text-primary);
    border-color: var(--accent);
}
.market-go:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}
.hot-words {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 6px;
    margin-top: 2px;
}
.hot-word {
    font-family: inherit;
    font-size: 10.5px;
    padding: 3px 10px;
    border-radius: 20px;
    border: 1px solid var(--border-color);
    background: transparent;
    color: var(--text-muted);
    cursor: pointer;
    transition: all 0.15s ease;
}
.hot-word:hover {
    color: var(--text-primary);
    border-color: var(--accent);
}
.market-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
}
.market-item {
    display: flex;
    align-items: center;
    gap: 9px;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 9px;
    padding: 9px 11px;
    transition: border-color 0.15s;
}
.market-item:hover {
    border-color: var(--text-muted);
}
.mi-name {
    font-size: 12px;
    font-weight: 500;
    color: var(--text-primary);
    min-width: 0;
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.mi-source {
    flex: 1;
    min-width: 0;
    font-size: 10.5px;
    color: var(--text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.mi-installs {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 10.5px;
    color: var(--text-muted);
}
.mi-installs i {
    font-size: 9px;
}
.mi-btn {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-family: inherit;
    font-size: 11px;
    font-weight: 500;
    padding: 4px 12px;
    border-radius: 6px;
    border: 1px solid var(--border-color);
    background: transparent;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all 0.15s ease;
}
.mi-btn:hover:not(:disabled) {
    color: var(--text-primary);
    border-color: var(--accent);
}
.mi-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
.mi-btn.is-installed {
    border-color: transparent;
    color: var(--text-muted);
}
.market-err span {
    color: var(--error);
}
/* ===== 模型卡片双列网格（与工具 Tab 同规格） ===== */
.model-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
}
@media (max-width: 900px) {
    .model-grid {
        grid-template-columns: 1fr;
    }
}
.model-card {
    position: relative;
    display: flex;
    flex-direction: column;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 10px;
    padding: 12px 13px 11px;
    cursor: pointer;
    overflow: hidden;
    transition: border-color 0.15s;
}
.model-card:hover {
    border-color: var(--text-muted);
}
.mc-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
}
.mc-provider {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    font-weight: 500;
    color: var(--text-secondary);
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.mc-provider .dot {
    width: 18px;
    height: 18px;
    border-radius: 5px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 9px;
    font-weight: 700;
    color: var(--white);
    flex-shrink: 0;
}
.mc-dot-dashscope { background: linear-gradient(145deg, #615ced, #8a7cf0); }
.mc-dot-deepseek { background: linear-gradient(145deg, #2f6bff, #5c9bff); }
.mc-dot-xiaomi { background: linear-gradient(145deg, #ff8a00, #ffb340); }
.mc-dot-openai { background: linear-gradient(145deg, #30333e, #5a5f70); }
.mc-dot-custom { background: var(--text-muted); }
.mc-badge-default {
    display: inline-flex;
    align-items: center;
    font-size: 10px;
    font-weight: 600;
    line-height: 1;
    color: var(--accent-ink);
    background: var(--accent);
    border-radius: 4px;
    padding: 3px 7px;
    flex-shrink: 0;
}
.mc-model-id {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 14.5px;
    font-weight: 650;
    letter-spacing: -0.2px;
    color: var(--text-primary);
    margin-bottom: 3px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.mc-name {
    font-size: 11px;
    color: var(--text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.mc-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: auto;
    padding-top: 10px;
}
.mc-tag {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 10px;
    color: var(--text-secondary);
    border: 1px solid var(--border-color);
    border-radius: 5px;
    padding: 2px 7px;
}
.mc-tag.warn {
    font-family: inherit;
    color: var(--warning);
    border-color: transparent;
    background: color-mix(in srgb, var(--warning) 14%, transparent);
}
/* 操作条：底部滑出（不再用全卡遮罩） */
.mc-actions {
    display: flex;
    gap: 6px;
    margin-top: 11px;
    padding-top: 10px;
    border-top: 1px solid var(--border-color);
    opacity: 0;
    transform: translateY(4px);
    pointer-events: none;
    transition: opacity 0.18s ease, transform 0.18s ease;
}
.model-card:hover .mc-actions {
    opacity: 1;
    transform: none;
    pointer-events: auto;
}
.mc-btn {
    font-size: 11px;
    font-weight: 500;
    font-family: inherit;
    padding: 5px 11px;
    border-radius: 7px;
    border: 1px solid var(--border-color);
    background: var(--bg-surface);
    color: var(--text-primary);
    cursor: pointer;
    transition: all 0.15s ease;
}
.mc-btn:hover {
    border-color: var(--accent);
}
.mc-btn.primary {
    background: var(--accent);
    border-color: var(--accent);
    color: var(--accent-ink);
}
.mc-btn.ghost {
    background: transparent;
    color: var(--text-secondary);
}
.mc-btn.ghost:hover {
    color: var(--text-primary);
}
.mc-btn.danger {
    color: var(--error);
}
.mc-btn.danger:hover {
    border-color: var(--error);
}
.model-card.add-card {
    align-items: center;
    justify-content: center;
    gap: 7px;
    min-height: 122px;
    border-style: dashed;
    background: transparent;
    color: var(--text-muted);
    font-family: inherit;
}
.model-card.add-card:hover {
    color: var(--accent-light);
    border-color: var(--accent);
    box-shadow: none;
}
.model-card.add-card .plus {
    font-size: 18px;
    line-height: 1;
}
.model-card.add-card p {
    font-size: 11.5px;
}
.set-row {
    display: flex;
    flex-direction: column;
    gap: 4px;
}
.set-row label {
    font-size: 10.5px;
    font-weight: 500;
    color: var(--text-muted);
    letter-spacing: 0.3px;
}
/* D2 横向表单行：label 左、输入右（宽幅下密度更合理） */
.set-hrow {
    display: flex;
    align-items: center;
    gap: 12px;
}
.set-hrow.top {
    align-items: flex-start;
}
.set-hrow label {
    width: 88px;
    flex-shrink: 0;
    font-size: 11.5px;
    font-weight: 500;
    color: var(--text-muted);
    letter-spacing: 0.3px;
}
.set-hrow.top label {
    padding-top: 6px;
}
.set-hrow input,
.set-hrow select {
    flex: 1;
    min-width: 0;
}
.set-hrow-fields {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
}
.set-hrow-fields .set-add-btn.mini {
    align-self: flex-start;
    padding: 5px 9px;
}
.set-row input, .set-row select, .set-hrow input, .set-hrow select {
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    padding: 6px 8px;
    font-size: 11.5px;
    color: var(--text-primary);
    outline: none;
    transition: border-color 0.15s ease;
    font-family: inherit;
}
.set-row input:focus, .set-row select:focus, .set-hrow input:focus, .set-hrow select:focus {
    border-color: var(--accent);
}
/* 自定义下拉选择器 */
.custom-select {
    position: relative;
    flex: 1;
    min-width: 0;
}
.custom-select-trigger {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    padding: 6px 8px;
    font-size: 11.5px;
    color: var(--text-primary);
    cursor: pointer;
    transition: border-color 0.15s ease;
    gap: 6px;
}
.custom-select-trigger:hover {
    border-color: var(--accent);
}
.custom-select.open .custom-select-trigger {
    border-color: var(--accent);
}
.custom-select-trigger .trigger-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
    flex: 1;
}
.custom-select-trigger .trigger-arrow {
    font-size: 8px;
    color: var(--text-muted);
    transition: transform 0.2s ease;
    flex-shrink: 0;
}
.custom-select.open .trigger-arrow {
    transform: rotate(180deg);
}
.custom-select-dropdown {
    position: absolute;
    top: calc(100% + 4px);
    left: 0;
    right: 0;
    background: var(--bg-surface);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    padding: 4px;
    box-shadow: var(--shadow);
    opacity: 0;
    visibility: hidden;
    transform: translateY(-4px);
    transition: opacity 0.15s ease, transform 0.15s ease, visibility 0.15s ease;
    z-index: 1000;
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-height: 200px;
    overflow-y: auto;
}
.custom-select.open .custom-select-dropdown {
    opacity: 1;
    visibility: visible;
    transform: translateY(0);
}
.custom-option {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 10px;
    border-radius: 6px;
    font-size: 12px;
    color: var(--text-secondary);
    cursor: pointer;
    transition: background 0.15s ease, color 0.15s ease;
    gap: 8px;
}
.custom-option:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
}
.custom-option.active {
    color: var(--text-primary);
    background: var(--bg-active);
}
.custom-option .option-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
    flex: 1;
}
.custom-option .check-icon {
    font-size: 10px;
    color: var(--success);
    opacity: 0;
    flex-shrink: 0;
}
.custom-option.active .check-icon {
    opacity: 1;
}

.set-kv {
    display: flex;
    gap: 5px;
    align-items: center;
}
.set-kv input {
    flex: 1;
    min-width: 0;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    padding: 5px 7px;
    font-size: 11px;
    color: var(--text-primary);
    outline: none;
}
.set-kv-del {
    background: transparent;
    border: none;
    color: var(--text-muted);
    cursor: pointer;
    padding: 4px 5px;
    border-radius: 5px;
    font-size: 10.5px;
}
.set-kv-del:hover {
    color: var(--error);
}
.set-add-btn {
    width: 100%;
    background: transparent;
    border: 1px dashed var(--border-color);
    border-radius: 8px;
    color: var(--text-secondary);
    font-size: 11.5px;
    padding: 7px;
    cursor: pointer;
    transition: all 0.15s ease;
}
.set-add-btn:hover {
    border-color: var(--accent);
    color: var(--accent-light);
}
.set-add-btn.mini {
    padding: 5px;
    font-size: 10.5px;
    border-radius: 6px;
}
.set-env-item {
    font-size: 11px;
    color: var(--text-secondary);
    word-break: break-all;
    line-height: 1.6;
    margin-bottom: 5px;
}
.settings-footer {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 11px 14px;
    border-top: 1px solid var(--border-color);
}
.settings-footer .spring {
    flex: 1;
}
.dirty {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 11.5px;
    color: var(--text-secondary);
}
.dirty .pt {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--warning);
}
.sf-btn {
    font-family: inherit;
    font-size: 12px;
    font-weight: 500;
    padding: 7px 16px;
    border-radius: 8px;
    border: 1px solid var(--border-color);
    background: transparent;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all 0.15s ease;
}
.sf-btn:hover {
    color: var(--text-primary);
    border-color: var(--accent);
}
.sf-btn.primary {
    background: var(--accent);
    border-color: var(--accent);
    color: var(--accent-ink);
    font-weight: 600;
}
.sf-btn.primary:hover {
    background: var(--accent-light);
}
.sf-btn.primary.saved {
    background: var(--success);
    border-color: var(--success);
    color: #ffffff;
}
/* 保存/校验失败：背景平滑过渡到错误色，与 .med-save.err 同款反馈 */
.sf-btn.primary.err {
    background: var(--error);
    border-color: var(--error);
    color: #ffffff;
}
.sf-btn.primary:disabled {
    opacity: 0.6;
    cursor: default;
}
/* ===== 模型编辑弹窗 ===== */
.model-edit-overlay {
    position: fixed;
    inset: 0;
    z-index: 1100;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.25);
}
.model-edit-dialog {
    width: min(480px, calc(100vw - 64px));
    max-height: calc(100vh - 80px);
    overflow-y: auto;
    background: var(--bg-surface);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    box-shadow: var(--shadow);
    animation: med-pop 0.18s ease;
}
@keyframes med-pop {
    from { transform: scale(0.96) translateY(6px); opacity: 0; }
}
.med-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 13px 16px 10px;
    border-bottom: 1px solid var(--border-color);
}
.med-head h4 {
    font-size: 12.5px;
    font-weight: 600;
    color: var(--text-primary);
    display: flex;
    align-items: center;
    gap: 7px;
}
.med-head h4 i {
    color: var(--text-muted);
    font-size: 11.5px;
}
.med-close {
    background: transparent;
    border: none;
    color: var(--text-muted);
    font-size: 12px;
    padding: 4px 7px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.15s ease;
}
.med-close:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
}
.med-body {
    display: flex;
    flex-direction: column;
    gap: 13px;
    padding: 14px 16px 4px;
}
.med-field {
    display: flex;
    flex-direction: column;
    gap: 5px;
}
.med-field > label {
    font-size: 11px;
    font-weight: 500;
    color: var(--text-muted);
    letter-spacing: 0.3px;
}
.med-field > label .hint {
    color: var(--text-muted);
    font-weight: 400;
    opacity: 0.75;
    margin-left: 5px;
}
.med-field input {
    width: 100%;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 7px;
    padding: 7px 9px;
    font-size: 12px;
    color: var(--text-primary);
    outline: none;
    font-family: inherit;
    transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.med-field input.mono {
    font-size: 11.5px;
    letter-spacing: 0.02em;
}
.med-field input:focus {
    border-color: var(--accent);
    box-shadow: 0 0 0 3px rgba(124, 108, 240, 0.15);
}
body.light-theme .med-field input:focus {
    box-shadow: 0 0 0 3px rgba(31, 35, 40, 0.1);
}
.med-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
}
.med-row .custom-select {
    flex: none;
    width: 100%;
}
.med-seg {
    display: flex;
    padding: 3px;
    gap: 3px;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 7px;
}
.med-seg button {
    flex: 1;
    border: none;
    cursor: pointer;
    background: transparent;
    color: var(--text-secondary);
    font-size: 11.5px;
    font-weight: 500;
    font-family: inherit;
    padding: 6px 8px;
    border-radius: 5px;
    transition: all 0.15s ease;
}
.med-seg button:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}
.med-seg button.on {
    background: var(--accent);
    color: var(--accent-ink);
}
.med-key-wrap {
    position: relative;
}
.med-key-wrap input {
    padding-right: 52px;
}
.med-key-toggle {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    border: none;
    background: transparent;
    cursor: pointer;
    font-size: 10.5px;
    color: var(--text-muted);
    font-family: inherit;
}
.med-key-toggle:hover {
    color: var(--accent);
}
.med-baseurl-note {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 10.5px;
    color: var(--text-muted);
    background: var(--bg-input);
    border: 1px dashed var(--border-color);
    border-radius: 7px;
    padding: 7px 9px;
    line-height: 1.5;
}
.med-baseurl-note .mono {
    font-size: 10px;
    color: var(--text-secondary);
    word-break: break-all;
}
.med-foot {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    padding: 13px 16px 15px;
}
.med-foot .med-del {
    margin-right: auto;
}
.med-save {
    background: var(--accent);
    border-color: var(--accent);
    color: var(--accent-ink);
}
.med-save:hover {
    background: var(--accent-light);
    border-color: var(--accent-light);
}
.med-save.err {
    background: var(--error);
    border-color: var(--error);
}
/* ===== 技能 Tab（只读展示全局技能）===== */
.skill-name, .mcp-name {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.skill-src {
    font-size: 10.5px;
    font-weight: 600;
    color: var(--accent);
    background: var(--bg-hover);
    border-radius: 5px;
    padding: 2px 6px;
    flex-shrink: 0;
}
.skill-desc {
    font-size: 11px;
    color: var(--text-secondary);
    line-height: 1.6;
}
.skill-sub {
    font-size: 10.5px;
    font-weight: 500;
    color: var(--text-muted);
    letter-spacing: 0.3px;
    margin-bottom: 3px;
}
.skill-list {
    margin: 0;
    padding-left: 14px;
    font-size: 11px;
    color: var(--text-secondary);
    line-height: 1.7;
}
.skill-refs {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
}
.skill-ref {
    font-size: 10.5px;
    color: var(--text-secondary);
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 5px;
    padding: 2px 7px;
}
/* 启用/禁用开关 */
.toggle-switch {
    position: relative;
    width: 40px;
    height: 22px;
    flex-shrink: 0;
}
.toggle-switch input {
    opacity: 0;
    width: 0;
    height: 0;
}
.toggle-slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: var(--text-muted);
    transition: background-color 0.2s;
    border-radius: 8px;
}
.toggle-slider:before {
    position: absolute;
    content: "";
    height: 18px;
    width: 18px;
    left: 2px;
    bottom: 2px;
    background-color: var(--white);
    transition: transform 0.2s;
    border-radius: 6px;
}
/* 亮绿选中态：深浅主题统一，不做深绿 */
.toggle-switch input:checked + .toggle-slider {
    background-color: #3fb950;
}
.toggle-switch input:checked + .toggle-slider:before {
    transform: translateX(18px);
}
.set-card.disabled {
    opacity: 0.5;
}
/* ===== 工具管理 Tab ===== */
.tool-cat {
    margin-bottom: 16px;
}
.tool-cat:last-child {
    margin-bottom: 0;
}
.tool-cat-head {
    font-size: 11px;
    font-weight: 650;
    color: var(--text-muted);
    letter-spacing: 0.5px;
    margin-bottom: 8px;
}
.tool-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
}
@media (max-width: 900px) {
    .tool-grid {
        grid-template-columns: 1fr;
    }
}
.tool-card {
    display: flex;
    align-items: center;
    gap: 10px;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    border-radius: 10px;
    padding: 10px 12px;
    cursor: pointer;
    transition: border-color 0.15s;
}
.tool-card:hover {
    border-color: var(--text-muted);
}
.tool-card.off {
    opacity: 0.62;
}
.tool-ic {
    width: 28px;
    height: 28px;
    border-radius: 7px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--bg-hover);
    border: 1px solid var(--border-color);
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: var(--text-secondary);
    flex-shrink: 0;
}
.tool-txt {
    flex: 1;
    min-width: 0;
}
.tool-name {
    display: flex;
    align-items: center;
    gap: 6px;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-primary);
}
.tool-off-tag {
    font-family: inherit;
    font-size: 10px;
    font-weight: 500;
    line-height: 1;
    padding: 2px 6px;
    border-radius: 4px;
    color: var(--warning);
    background: color-mix(in srgb, var(--warning) 14%, transparent);
}
.tool-desc {
    font-size: 10.5px;
    color: var(--text-muted);
    line-height: 1.4;
    margin-top: 3px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.tool-note {
    display: flex;
    align-items: flex-start;
    gap: 9px;
    margin-top: 14px;
    padding: 10px 13px;
    border-radius: 9px;
    background: var(--bg-input);
    border: 1px solid var(--border-color);
    font-size: 11px;
    color: var(--text-muted);
    line-height: 1.6;
}
.tool-note i {
    font-size: 11px;
    margin-top: 2px;
}
.tool-note strong {
    color: var(--text-secondary);
    font-weight: 600;
}
</style>
