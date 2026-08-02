<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'

hljs.registerLanguage('java', java)

const isVisible = ref(false)

onMounted(() => {
  setTimeout(() => {
    isVisible.value = true
  }, 100)
})

const codeExample = ref(`# Install Qualia Code
npm install -g qualia-code

# Start the AI programming assistant
qualia-code

# Or run with specific model
qualia-code --model qwen-turbo

# Execute a coding task
qualia-code "Add pagination to UserService"`)

const highlightedCode = computed(() => {
  try {
    return hljs.highlight(codeExample.value, { language: 'java' }).value
  } catch {
    return codeExample.value
  }
})

const copied = ref(false)

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(codeExample.value)
    copied.value = true
    setTimeout(() => copied.value = false, 2000)
  } catch (err) {
    console.error('Failed to copy:', err)
  }
}

const stats = [
  { value: '10+', label: 'Built-in Tools' },
  { value: '100%', label: 'Open Source' },
  { value: '< 5min', label: 'Quick Start' }
]

const features = [
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5"/></svg>`,
    title: '代码生成',
    desc: '理解需求并自动生成高质量代码'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z"/></svg>`,
    title: 'ReAct推理',
    desc: '基于ReAct模式自主思考、规划和执行任务'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14.25 9.75L16.5 12l-2.25 2.25m-4.5 0L7.5 12l2.25-2.25M6 20.25h12A2.25 2.25 0 0020.25 18V6A2.25 2.25 0 0018 3.75H6A2.25 2.25 0 003.75 6v12A2.25 2.25 0 006 20.25z"/></svg>`,
    title: '代码分析',
    desc: '理解代码库结构并提供重构建议'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11.42 15.17l-5.384 3.18.75-5.97L2.414 8.18l5.99-.87L11.42 2l2.98 5.31 5.99.87-4.372 4.2.75 5.97z"/></svg>`,
    title: 'MCP协议',
    desc: '无缝集成外部工具和数据源'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M20.25 8.511c.884.284 1.5 1.128 1.5 2.097v4.286c0 1.136-.847 2.1-1.98 2.193-.34.027-.68.052-1.02.072v3.091l-3-3c-1.354 0-2.694-.055-4.02-.163a2.115 2.115 0 01-.825-.242m9.345-8.334a2.126 2.126 0 00-.476-.095 48.64 48.64 0 00-8.048 0c-1.131.094-1.976 1.057-1.976 2.192v4.286c0 .837.46 1.58 1.155 1.951m9.345-8.334V6.637c0-1.621-1.152-3.026-2.76-3.235A48.455 48.455 0 0011.25 3c-2.115 0-4.198.137-6.24.402-1.608.209-2.76 1.614-2.76 3.235v6.226c0 1.621 1.152 3.026 2.76 3.235.577.075 1.157.14 1.74.194V21l4.155-4.155"/></svg>`,
    title: '上下文记忆',
    desc: '理解项目上下文并保持编码连续性'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19 14.5M14.25 3.104c.251.023.501.05.75.082M19 14.5l-2.47 6.174a1.125 1.125 0 01-1.04.726H8.51a1.125 1.125 0 01-1.04-.726L5 14.5m14 0H5"/></svg>`,
    title: '多模型适配',
    desc: '支持通义千问、OpenAI、Claude等多种大语言模型'
  }
]

const useCases = [
  { title: '代码补全', desc: '智能预测和补全代码', icon: '✨' },
  { title: 'Bug修复', desc: '自动定位和修复代码缺陷', icon: '🐛' },
  { title: '代码重构', desc: '分析代码结构并优化', icon: '🔧' },
  { title: '单元测试', desc: '生成测试用例和测试代码', icon: '🧪' }
]
</script>

<template>
  <div class="landing" :class="{ visible: isVisible }">
    <!-- Hero Section -->
    <section class="hero">
      <!-- Background Effects -->
      <div class="hero-bg">
        <div class="hero-grid"></div>
        <div class="hero-glow"></div>
      </div>

      <div class="hero-content">
        <!-- Badge -->
        <div class="hero-badge">
          <span class="badge-pulse"></span>
          <span>Qualia Code</span>
        </div>

        <!-- Title -->
        <h1 class="hero-title">
          Your AI-Powered
          <br>
          <span class="title-gradient">Programming Assistant</span>
        </h1>

        <!-- Subtitle -->
        <p class="hero-subtitle">
          Native Java programming assistant with ReAct reasoning, MCP toolchain, and multi-model support. Let AI become your coding partner.
        </p>

        <!-- Actions -->
        <div class="hero-actions">
          <a href="#quickstart" class="btn btn-primary">
            <span>Get Started</span>
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clip-rule="evenodd"/>
            </svg>
          </a>
          <a href="https://github.com" target="_blank" class="btn btn-secondary">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            <span>GitHub</span>
          </a>
        </div>

        <!-- Stats -->
        <div class="hero-stats">
          <div v-for="(stat, index) in stats" :key="index" class="stat-item">
            <span class="stat-value">{{ stat.value }}</span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Code Section -->
    <section id="quickstart" class="code-section">
      <div class="section-container">
        <div class="code-content">
          <h2 class="section-title">快速开始</h2>
          <p class="section-desc">
            秒级安装运行，无需编写代码。
          </p>
          <div class="code-block">
            <div class="code-block-header">
              <div class="code-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <span class="code-lang">Terminal</span>
              <button class="copy-btn" @click="copyCode">
                <svg v-if="!copied" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                  <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                <span>{{ copied ? '已复制' : '复制' }}</span>
              </button>
            </div>
            <div class="code-body">
              <pre><code class="language-java" v-html="highlightedCode"></code></pre>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features">
      <div class="section-container">
        <h2 class="section-title">核心能力</h2>
        <p class="section-desc">专为编程场景设计的AI助手能力</p>
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
    <section class="usecases">
      <div class="section-container">
        <h2 class="section-title">应用场景</h2>
        <p class="section-desc">AI编程助手，提升您的生产力</p>
        <div class="usecases-grid">
          <div v-for="(usecase, index) in useCases" :key="index" class="usecase-card">
            <div class="usecase-icon">{{ usecase.icon }}</div>
            <h3 class="usecase-title">{{ usecase.title }}</h3>
            <p class="usecase-desc">{{ usecase.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta">
      <div class="cta-bg">
        <div class="cta-gradient"></div>
      </div>
      <div class="section-container">
        <div class="cta-content">
          <h2 class="cta-title">让AI成为您的编程伙伴</h2>
          <p class="cta-desc">
            几分钟内创建您的编程助手
          </p>
          <div class="cta-actions">
            <a href="#quickstart" class="btn btn-white">
              <span>快速开始</span>
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clip-rule="evenodd"/>
              </svg>
            </a>
            <a href="https://github.com" target="_blank" class="btn btn-github">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
              <span>GitHub</span>
            </a>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.landing {
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.6s cubic-bezier(0.2, 0, 0, 1);
}

.landing.visible {
  opacity: 1;
  transform: translateY(0);
}

/* Selection color */
.landing ::selection {
  background: rgba(0, 122, 255, 0.3);
  color: #fdfcfc;
}

/* ============================================
   Hero Section
   ============================================ */
.hero {
  position: relative;
  padding: 96px var(--spacing-6) 80px;
  background: #201d1d;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(15, 0, 0, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 0, 0, 0.08) 1px, transparent 1px);
  background-size: 48px 48px;
}

.hero-glow {
  position: absolute;
  top: 30%;
  left: 50%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(15, 0, 0, 0.3) 0%, transparent 70%);
  transform: translate(-50%, -50%);
}

.hero-content {
  position: relative;
  max-width: 880px;
  margin: 0 auto;
  text-align: center;
  z-index: 1;
}

/* Badge */
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: #302c2c;
  border: 1px solid rgba(15, 0, 0, 0.12);
  border-radius: 4px;
  color: #fdfcfc;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 32px;
  transition: all 150ms cubic-bezier(0.2, 0, 0, 1);
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.badge-pulse {
  width: 8px;
  height: 8px;
  background: #30d158;
  border-radius: 50%;
  position: relative;
}

.badge-pulse::after {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  background: #30d158;
  animation: ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;
  opacity: 0.5;
}

@keyframes ping {
  75%, 100% {
    transform: scale(2);
    opacity: 0;
  }
}

/* Title */
.hero-title {
  font-size: 38px;
  font-weight: 700;
  color: #fdfcfc;
  line-height: 1.50;
  margin-bottom: 24px;
  letter-spacing: 0;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.title-gradient {
  background: #007aff;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* Subtitle */
.hero-subtitle {
  font-size: 16px;
  color: #9a9898;
  line-height: 1.50;
  max-width: 600px;
  margin: 0 auto 40px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* Actions */
.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 64px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 4px;
  text-decoration: none;
  transition: all 150ms cubic-bezier(0.2, 0, 0, 1);
  position: relative;
  overflow: hidden;
  cursor: pointer;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.btn svg {
  width: 18px;
  height: 18px;
  transition: transform 150ms cubic-bezier(0.2, 0, 0, 1);
}

.btn:hover svg {
  transform: translateX(4px);
}

.btn-primary {
  background: #201d1d;
  color: #fdfcfc;
  border: 1px solid #646262;
}

.btn-primary:hover {
  background: #302c2c;
  color: #fdfcfc;
}

.btn-secondary {
  background: transparent;
  color: #fdfcfc;
  border: 1px solid #646262;
}

.btn-secondary:hover {
  background: #302c2c;
  color: #fdfcfc;
}

.btn-white {
  background: #201d1d;
  color: #fdfcfc;
  border: 1px solid #646262;
}

.btn-white:hover {
  background: #302c2c;
  color: #fdfcfc;
}

.btn-github {
  background: transparent;
  color: #fdfcfc;
  border: 1px solid #646262;
}

.btn-github:hover {
  background: #302c2c;
  color: #fdfcfc;
}

/* Stats */
.hero-stats {
  display: flex;
  justify-content: center;
  align-items: center;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 0 40px;
  position: relative;
}

.stat-item:not(:last-child)::after {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 1px;
  height: 60%;
  background: rgba(15, 0, 0, 0.12);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #fdfcfc;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.stat-label {
  font-size: 14px;
  color: #9a9898;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   Sections Common
   ============================================ */
.section-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 24px;
}

.section-title {
  font-size: 28px;
  font-weight: 700;
  color: #fdfcfc;
  text-align: center;
  margin-bottom: 16px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.section-desc {
  font-size: 16px;
  color: #9a9898;
  text-align: center;
  margin-bottom: 48px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   Code Section
   ============================================ */
.code-section {
  padding: 96px 0;
  background: #1c1919;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.code-content {
  max-width: 680px;
  margin: 0 auto;
}

.code-block {
  background: #1a1717;
  border: 1px solid rgba(15, 0, 0, 0.12);
  border-radius: 8px;
  overflow: hidden;
}

.code-block-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #252222;
  border-bottom: 1px solid rgba(15, 0, 0, 0.12);
}

.code-dots {
  display: flex;
  gap: 6px;
}

.code-dots span {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: rgba(15, 0, 0, 0.12);
}

.code-dots span:first-child {
  background: #ff5f57;
}

.code-dots span:nth-child(2) {
  background: #febc2e;
}

.code-dots span:nth-child(3) {
  background: #28c840;
}

.code-lang {
  flex: 1;
  font-size: 13px;
  color: #9a9898;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.copy-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: transparent;
  border: 1px solid rgba(15, 0, 0, 0.12);
  border-radius: 4px;
  color: #9a9898;
  font-size: 12px;
  cursor: pointer;
  transition: all 150ms cubic-bezier(0.2, 0, 0, 1);
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.copy-btn:hover {
  background: #302c2c;
  color: #fdfcfc;
  border-color: #646262;
}

.copy-btn svg {
  width: 14px;
  height: 14px;
}

.code-body {
  padding: 20px;
  overflow-x: auto;
}

.code-body pre {
  margin: 0;
}

.code-body code {
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #fdfcfc;
}

/* ============================================
   Features Section
   ============================================ */
.features {
  padding: 96px 0;
  background: #201d1d;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.feature-card {
  background: #302c2c;
  border: 1px solid rgba(15, 0, 0, 0.12);
  border-radius: 8px;
  padding: 24px;
  transition: all 200ms cubic-bezier(0.2, 0, 0, 1);
}

.feature-card:hover {
  background: #3a3636;
  transform: translateY(-2px);
}

.feature-icon {
  width: 40px;
  height: 40px;
  background: rgba(0, 122, 255, 0.1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  color: #007aff;
}

.feature-icon svg {
  width: 20px;
  height: 20px;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: #fdfcfc;
  margin-bottom: 8px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.feature-desc {
  font-size: 13px;
  color: #9a9898;
  line-height: 1.5;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   Use Cases Section
   ============================================ */
.usecases {
  padding: 96px 0;
  background: #1c1919;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.usecases-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.usecase-card {
  background: #302c2c;
  border: 1px solid rgba(15, 0, 0, 0.12);
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  transition: all 200ms cubic-bezier(0.2, 0, 0, 1);
}

.usecase-card:hover {
  background: #3a3636;
  transform: translateY(-2px);
}

.usecase-icon {
  font-size: 28px;
  margin-bottom: 12px;
}

.usecase-title {
  font-size: 15px;
  font-weight: 600;
  color: #fdfcfc;
  margin-bottom: 8px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.usecase-desc {
  font-size: 12px;
  color: #9a9898;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   CTA Section
   ============================================ */
.cta {
  position: relative;
  padding: 80px 0;
  background: #201d1d;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
  overflow: hidden;
}

.cta-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.cta-gradient {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(to top, rgba(0, 122, 255, 0.05), transparent);
}

.cta-content {
  position: relative;
  text-align: center;
  z-index: 1;
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

.cta-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* ============================================
   Responsive Design
   ============================================ */
@media (max-width: 1024px) {
  .hero-title {
    font-size: 32px;
  }

  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .usecases-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .hero {
    padding: 80px 16px 64px;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-subtitle {
    font-size: 14px;
  }

  .hero-actions,
  .cta-actions {
    flex-direction: column;
    align-items: center;
  }

  .btn {
    width: 100%;
    max-width: 280px;
    justify-content: center;
  }

  .hero-stats {
    flex-direction: column;
    gap: 24px;
  }

  .stat-item:not(:last-child)::after {
    display: none;
  }

  .features-grid,
  .usecases-grid {
    grid-template-columns: 1fr;
  }

  .features,
  .usecases {
    padding: 64px 0;
  }
}

/* ============================================
   Light Theme
   ============================================ */
[data-theme="light"] .hero,
[data-theme="light"] .usecases {
  background: #f8f7f7;
}

[data-theme="light"] .hero-grid {
  background-image:
    linear-gradient(rgba(15, 0, 0, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 0, 0, 0.04) 1px, transparent 1px);
}

[data-theme="light"] .hero-glow {
  background: radial-gradient(circle, rgba(0, 122, 255, 0.08) 0%, transparent 70%);
}

[data-theme="light"] .code-section,
[data-theme="light"] .features,
[data-theme="light"] .cta {
  background: #f1eeee;
}

[data-theme="light"] .hero-badge {
  background: #ffffff;
  border-color: rgba(15, 0, 0, 0.12);
  color: #201d1d;
}

[data-theme="light"] .hero-title,
[data-theme="light"] .section-title,
[data-theme="light"] .feature-title,
[data-theme="light"] .usecase-title,
[data-theme="light"] .cta-title {
  color: #201d1d;
}

[data-theme="light"] .hero-subtitle,
[data-theme="light"] .section-desc,
[data-theme="light"] .feature-desc,
[data-theme="light"] .usecase-desc,
[data-theme="light"] .cta-desc {
  color: #6e6e73;
}

[data-theme="light"] .btn-primary,
[data-theme="light"] .btn-white {
  background: #201d1d;
  color: #fdfcfc;
  border-color: transparent;
}

[data-theme="light"] .btn-primary:hover,
[data-theme="light"] .btn-white:hover {
  background: #302c2c;
}

[data-theme="light"] .btn-secondary,
[data-theme="light"] .btn-github {
  color: #201d1d;
  border-color: rgba(100, 98, 98, 0.4);
}

[data-theme="light"] .btn-secondary:hover,
[data-theme="light"] .btn-github:hover {
  background: rgba(241, 238, 238, 0.8);
  border-color: #646262;
}

[data-theme="light"] .code-block {
  background: #f8fafc;
  border-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .code-block-header {
  background: #f1f5f9;
  border-bottom-color: rgba(15, 0, 0, 0.06);
}

[data-theme="light"] .code-lang {
  color: #6e6e73;
}

[data-theme="light"] .copy-btn {
  border-color: rgba(15, 0, 0, 0.12);
  color: #6e6e73;
}

[data-theme="light"] .copy-btn:hover {
  background: #ffffff;
  color: #201d1d;
  border-color: rgba(100, 98, 98, 0.4);
}

[data-theme="light"] .code-body code {
  color: #201d1d;
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

[data-theme="light"] .cta-gradient {
  background: linear-gradient(to top, rgba(0, 122, 255, 0.03), transparent);
}

[data-theme="light"] .stat-item:not(:last-child)::after {
  background: rgba(15, 0, 0, 0.06);
}

[data-theme="light"] .stat-value {
  color: #201d1d;
}

[data-theme="light"] .stat-label {
  color: #6e6e73;
}
</style>
