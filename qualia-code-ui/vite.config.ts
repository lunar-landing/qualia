import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// qualia-code 前端工程（位于项目根，与 qualia-code 后端目录平级）：
//   dev   —— Vite dev server，/api 代理到本机 Spring Boot（CLI 默认 9090，可用 QUALIA_BACKEND 覆盖）
//   build —— 产物直接输出到 Spring Boot 静态目录 target/classes/static，相对路径 base 兼容桌面版 WebView
export default defineConfig({
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5273,
    proxy: {
      '/api': {
        target: process.env.QUALIA_BACKEND || 'http://localhost:9090',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: fileURLToPath(new URL('./qualia-code/target/classes/static', import.meta.url)),
    emptyOutDir: true,
  },
})
