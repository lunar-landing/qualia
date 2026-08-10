<script setup lang="ts">
import { ref, onMounted } from 'vue'

const isVisible = ref(false)

onMounted(() => {
  setTimeout(() => {
    isVisible.value = true
  }, 100)
})

const features = [
  {
    title: 'ReAct 推理引擎',
    desc: '内置 ReAct（Reasoning + Acting）推理模式，让 AI 智能体能够自主思考、规划和执行任务。',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z"/></svg>`
  },
  {
    title: 'MCP 工具协议',
    desc: '支持 Model Context Protocol，可无缝集成各种外部工具和数据源，扩展智能体能力边界。',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11.42 15.17l-5.384 3.18.75-5.97L2.414 8.18l5.99-.87L11.42 2l2.98 5.31 5.99.87-4.372 4.2.75 5.97z"/></svg>`
  },
  {
    title: '多模型适配',
    desc: '支持通义千问、OpenAI、Claude 等主流 LLM，一套代码轻松切换不同模型。',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19 14.5M14.25 3.104c.251.023.501.05.75.082M19 14.5l-2.47 6.174a1.125 1.125 0 01-1.04.726H8.51a1.125 1.125 0 01-1.04-.726L5 14.5m14 0H5"/></svg>`
  },
  {
    title: '会话记忆管理',
    desc: '内置滑动窗口记忆和摘要压缩机制，支持长对话上下文管理，保持对话连贯性。',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M20.25 8.511c.884.284 1.5 1.128 1.5 2.097v4.286c0 1.136-.847 2.1-1.98 2.193-.34.027-.68.052-1.02.072v3.091l-3-3c-1.354 0-2.694-.055-4.02-.163a2.115 2.115 0 01-.825-.242m9.345-8.334a2.126 2.126 0 00-.476-.095 48.64 48.64 0 00-8.048 0c-1.131.094-1.976 1.057-1.976 2.192v4.286c0 .837.46 1.58 1.155 1.951m9.345-8.334V6.637c0-1.621-1.152-3.026-2.76-3.235A48.455 48.455 0 0011.25 3c-2.115 0-4.198.137-6.24.402-1.608.209-2.76 1.614-2.76 3.235v6.226c0 1.621 1.152 3.026 2.76 3.235.577.075 1.157.14 1.74.194V21l4.155-4.155"/></svg>`
  },
  {
    title: '内置工具集',
    desc: '提供搜索、文件读写、代码执行等 10+ 内置工具，开箱即用，无需额外配置。',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11.42 15.17l-5.384 3.18.75-5.97L2.414 8.18l5.99-.87L11.42 2l2.98 5.31 5.99.87-4.372 4.2.75 5.97z"/></svg>`
  },
  {
    title: 'Java 原生实现',
    desc: '纯 Java 实现，无缝融入企业级 Java 技术栈，享受完整的类型安全和 IDE 支持。',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5"/></svg>`
  }
]

const useCases = [
  { title: '智能客服', desc: '7x24 小时自动回复用户咨询' },
  { title: '数据分析', desc: '自然语言查询数据库并生成报告' },
  { title: '代码助手', desc: '辅助开发人员编写和审查代码' },
  { title: '内容创作', desc: '自动生成文章、邮件等文本内容' }
]
</script>

<template>
  <div class="product" :class="{ visible: isVisible }">
    <!-- Hero Section -->
    <section class="product-hero">
      <div class="product-hero-content">
        <div class="product-badge">
          <span class="badge-dot"></span>
          <span>Qualia Code</span>
        </div>
        <h1 class="product-title">
          企业级
          <br>
          <span class="title-highlight">Java AI 智能体框架</span>
        </h1>
        <p class="product-subtitle">
          用 Java 构建生产级 LLM 应用，支持 ReAct 推理、MCP 工具协议、多模型适配，让 AI 智能体开发变得简单可靠。
        </p>
        <div class="product-actions">
          <router-link to="/docs/qualia-docs" class="btn btn-primary">
            <span>快速开始</span>
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clip-rule="evenodd"/>
            </svg>
          </router-link>
          <a href="https://github.com/lunar-landing/qualia" target="_blank" class="btn btn-secondary">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            <span>GitHub</span>
          </a>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="product-features">
      <div class="section-container">
        <h2 class="section-title">核心能力</h2>
        <p class="section-desc">为生产环境设计的 AI 智能体框架</p>
        <div class="features-grid">
          <div v-for="(feature, index) in features" :key="index" class="feature-card">
            <div class="feature-icon" v-html="feature.icon"></div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Use Cases Section -->
    <section class="product-usecases">
      <div class="section-container">
        <h2 class="section-title">应用场景</h2>
        <p class="section-desc">适用于多种业务场景的智能解决方案</p>
        <div class="usecases-grid">
          <div v-for="(usecase, index) in useCases" :key="index" class="usecase-card">
            <h3 class="usecase-title">{{ usecase.title }}</h3>
            <p class="usecase-desc">{{ usecase.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="product-cta">
      <div class="section-container">
        <div class="cta-content">
          <h2 class="cta-title">开始构建您的 AI 智能体</h2>
          <p class="cta-desc">几分钟内即可创建您的第一个 ReAct 智能体</p>
          <router-link to="/docs/qualia-docs" class="btn btn-primary">
            <span>阅读文档</span>
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clip-rule="evenodd"/>
            </svg>
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.product {
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.6s cubic-bezier(0.2, 0, 0, 1);
}

.product.visible {
  opacity: 1;
  transform: translateY(0);
}

/* Selection color */
.product ::selection {
  background: rgba(0, 122, 255, 0.3);
  color: #fdfcfc;
}

/* ============================================
   Product Hero
   ============================================ */
.product-hero {
  padding: 120px 24px 80px;
  background: #201d1d;
  text-align: center;
}

.product-hero-content {
  max-width: 800px;
  margin: 0 auto;
  animation: fadeInUp 0.6s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.product-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: #302c2c;
  border: 1px solid rgba(100, 98, 98, 0.4);
  border-radius: 6px;
  color: #fdfcfc;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 32px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.badge-dot {
  width: 8px;
  height: 8px;
  background: #30d158;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.product-title {
  font-size: 48px;
  font-weight: 700;
  color: #fdfcfc;
  line-height: 1.2;
  margin-bottom: 24px;
  letter-spacing: -0.02em;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.title-highlight {
  background: linear-gradient(135deg, #007aff, #5856d6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.product-subtitle {
  font-size: 18px;
  color: #9a9898;
  line-height: 1.6;
  max-width: 600px;
  margin: 0 auto 40px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.product-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  font-size: 15px;
  font-weight: 500;
  border-radius: 6px;
  text-decoration: none;
  transition: all 200ms cubic-bezier(0.2, 0, 0, 1);
  position: relative;
  overflow: hidden;
  cursor: pointer;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
  letter-spacing: 0.02em;
}

.btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.06) 0%, transparent 100%);
  opacity: 0;
  transition: opacity 200ms ease;
}

.btn:hover::before {
  opacity: 1;
}

.btn svg {
  width: 16px;
  height: 16px;
  transition: transform 200ms cubic-bezier(0.2, 0, 0, 1);
}

.btn:hover svg {
  transform: translateX(3px);
}

.btn-primary {
  background: #fdfcfc;
  color: #201d1d;
  border: 1px solid transparent;
  font-weight: 600;
}

.btn-primary:hover {
  background: #f1eeee;
  color: #201d1d;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(253, 252, 252, 0.15);
}

.btn-secondary {
  background: transparent;
  color: #fdfcfc;
  border: 1px solid rgba(100, 98, 98, 0.6);
}

.btn-secondary:hover {
  background: rgba(48, 44, 44, 0.8);
  color: #fdfcfc;
  border-color: #9a9898;
}

/* ============================================
   Features Section
   ============================================ */
.product-features {
  padding: 96px 0;
  background: #1c1919;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.section-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
  text-align: center;
}

.section-title {
  font-size: 28px;
  font-weight: 700;
  color: #fdfcfc;
  margin-bottom: 16px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.section-desc {
  font-size: 16px;
  color: #9a9898;
  margin-bottom: 48px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.feature-card {
  background: #302c2c;
  border: 1px solid rgba(100, 98, 98, 0.3);
  border-radius: 8px;
  padding: 32px 24px;
  text-align: left;
  transition: all 200ms ease;
}

.feature-card:hover {
  background: #3a3636;
  border-color: rgba(100, 98, 98, 0.5);
  transform: translateY(-4px);
}

.feature-icon {
  width: 48px;
  height: 48px;
  background: rgba(0, 122, 255, 0.1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  color: #007aff;
}

.feature-icon svg {
  width: 24px;
  height: 24px;
}

.feature-title {
  font-size: 18px;
  font-weight: 600;
  color: #fdfcfc;
  margin-bottom: 12px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.feature-desc {
  font-size: 14px;
  color: #9a9898;
  line-height: 1.6;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   Use Cases Section
   ============================================ */
.product-usecases {
  padding: 96px 0;
  background: #201d1d;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.usecases-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.usecase-card {
  background: #302c2c;
  border: 1px solid rgba(100, 98, 98, 0.3);
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  transition: all 200ms ease;
}

.usecase-card:hover {
  background: #3a3636;
  transform: translateY(-4px);
}

.usecase-title {
  font-size: 16px;
  font-weight: 600;
  color: #fdfcfc;
  margin-bottom: 8px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.usecase-desc {
  font-size: 13px;
  color: #9a9898;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   CTA Section
   ============================================ */
.product-cta {
  padding: 80px 0;
  background: #1c1919;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.cta-content {
  text-align: center;
}

.cta-title {
  font-size: 28px;
  font-weight: 700;
  color: #fdfcfc;
  margin-bottom: 16px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.cta-desc {
  font-size: 16px;
  color: #9a9898;
  margin-bottom: 32px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   Responsive Design
   ============================================ */
@media (max-width: 1024px) {
  .product-title {
    font-size: 36px;
  }
  
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .usecases-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .product-hero {
    padding: 100px 16px 64px;
  }
  
  .product-title {
    font-size: 28px;
  }
  
  .product-subtitle {
    font-size: 16px;
  }
  
  .product-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .btn {
    width: 100%;
    max-width: 280px;
    justify-content: center;
  }
  
  .features-grid,
  .usecases-grid {
    grid-template-columns: 1fr;
  }
  
  .product-features,
  .product-usecases {
    padding: 64px 0;
  }
}

/* ============================================
   Light Theme
   ============================================ */
[data-theme="light"] .product-hero,
[data-theme="light"] .product-usecases {
  background: #f8f7f7;
}

[data-theme="light"] .product-features,
[data-theme="light"] .product-cta {
  background: #f1eeee;
}

[data-theme="light"] .product-badge {
  background: #ffffff;
  border-color: rgba(15, 0, 0, 0.12);
  color: #201d1d;
}

[data-theme="light"] .product-title,
[data-theme="light"] .section-title,
[data-theme="light"] .feature-title,
[data-theme="light"] .usecase-title,
[data-theme="light"] .cta-title {
  color: #201d1d;
}

[data-theme="light"] .product-subtitle,
[data-theme="light"] .section-desc,
[data-theme="light"] .feature-desc,
[data-theme="light"] .usecase-desc,
[data-theme="light"] .cta-desc {
  color: #6e6e73;
}

[data-theme="light"] .btn-primary {
  background: #201d1d;
  color: #f8f7f7;
}

[data-theme="light"] .btn-primary:hover {
  background: #302c2c;
}

[data-theme="light"] .btn-secondary {
  color: #201d1d;
  border-color: rgba(100, 98, 98, 0.4);
}

[data-theme="light"] .btn-secondary:hover {
  background: rgba(241, 238, 238, 0.8);
  border-color: #646262;
}

[data-theme="light"] .feature-card,
[data-theme="light"] .usecase-card {
  background: #ffffff;
  border-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .feature-card:hover,
[data-theme="light"] .usecase-card:hover {
  background: #ffffff;
  border-color: rgba(15, 0, 0, 0.2);
}

[data-theme="light"] .feature-icon {
  background: rgba(0, 122, 255, 0.08);
}
</style>
