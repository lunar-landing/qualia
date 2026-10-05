import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

// 全局界面语言（沿用主题约定：localStorage['codex-locale']，默认中文）
export type Locale = 'zh-CN' | 'en'

const LOCALE_KEY = 'codex-locale'

function initialLocale(): Locale {
  return localStorage.getItem(LOCALE_KEY) === 'en' ? 'en' : 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, en },
})

/** 切换界面语言并持久化（即时生效，全部响应式文本跟随刷新） */
export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_KEY, locale)
  document.documentElement.lang = locale
}
