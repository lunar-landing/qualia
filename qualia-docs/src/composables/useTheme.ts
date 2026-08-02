import { ref, watch, onMounted } from 'vue'

type Theme = 'dark' | 'light'

const STORAGE_KEY = 'qualia-docs-theme'

const theme = ref<Theme>('dark')

function applyTheme(t: Theme) {
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem(STORAGE_KEY, t)
}

export function useTheme() {
  const toggleTheme = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  const setTheme = (t: Theme) => {
    theme.value = t
  }

  const isDark = ref(theme.value === 'dark')

  watch(theme, (val) => {
    applyTheme(val)
    isDark.value = val === 'dark'
  })

  onMounted(() => {
    // Initialize from localStorage or system preference
    const stored = localStorage.getItem(STORAGE_KEY) as Theme | null
    if (stored === 'dark' || stored === 'light') {
      theme.value = stored
    } else if (window.matchMedia('(prefers-color-scheme: light)').matches) {
      theme.value = 'light'
    } else {
      theme.value = 'dark'
    }
    applyTheme(theme.value)
    isDark.value = theme.value === 'dark'
  })

  return {
    theme,
    isDark,
    toggleTheme,
    setTheme
  }
}
