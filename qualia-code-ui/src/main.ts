import { createApp } from 'vue'
import { createPinia } from 'pinia'

import '@fortawesome/fontawesome-free/css/all.min.css'
// 代码块字体自托管 400/500 双字重：代码块 font-weight:500（墨线加重）需要 Medium 档，系统未装 JetBrains Mono 时兜底
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/500.css'
import '@/styles/index.css'

import App from './App.vue'
import { initTheme } from './composables/theme'
import { i18n } from './i18n'

const app = createApp(App)

app.use(createPinia())
app.use(i18n)
initTheme()
app.mount('#app')
