# qualia-code-ui

Vue 3 + Vite + TypeScript + Pinia 单页前端（qualia-code 的 UI 工程，位于项目根，与后端目录平级；替代旧 `src/main/resources/static` 原生 JS 实现）。

## 开发

```bash
npm install
npm run dev
```

- Vite dev server 带 `/api` 代理，默认转发到后端 `http://localhost:9090`（CLI 默认端口，如有出入改 `vite.config.ts` 的 `server.proxy`）
- 后端另起后浏览器打开 dev server 地址即可，热更新生效

## 构建

```bash
npm run build
```

- 产物输出到 `qualia-code/target/classes/static/`（`vite.config.ts` 的 `build.outDir`），直接被 Spring Boot 以 `classpath:/static/` 服务
- `base: './'` 相对路径，兼容桌面版（qualia-code-desktop WebView）file:// 加载
- 构建前自动跑 `type-check`（vue-tsc），类型错误会阻断构建
- FontAwesome / highlight.js 均为 npm 本地资源，无 CDN 依赖（桌面版离线可用）

## 结构速览

```
src/
  api/        # 与后端 Controller 一一对应（chat/stream/workspace/config/attachment）
  types/      # DTO 对齐的 TS 接口
  stores/     # Pinia：session / chat(per-session 消息流) / workspace / config / preview
  composables/# useChatStream(SSE 状态机) / useLightbox / theme 等
  components/ # UI 组件（ToolChip 族 / 工作区面板 / 设置弹窗 / TokenHeatmap 等）
  styles/     # index.css（CSS 变量双主题体系，自旧版原样搬入）
```
