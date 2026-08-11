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

const codeExample = ref(`# Build Qualia Claw
mvn -pl qualia-claw -am clean package -DskipTests

# Start the multi-agent collaboration platform
java -jar qualia-claw/target/qualia-claw.jar

# Or build the desktop app
mvn -pl qualia-claw-desktop -am clean package -DskipTests`)

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
  { value: 'N', label: 'Parallel Agents' },
  { value: '100%', label: 'Open Source' },
  { value: '< 5min', label: 'Quick Start' }
]

const features = [
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z"/></svg>`,
    title: '多智能体协作',
    desc: '多个智能体并行对话，各自独立工位互不干扰'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"/></svg>`,
    title: '职能角色设定',
    desc: '为每个智能体定制角色定位与专属系统提示词'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z"/></svg>`,
    title: '独立工位',
    desc: '系统托管工作区 + 独立会话记忆，开箱即用'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11.42 15.17l-5.384 3.18.75-5.97L2.414 8.18l5.99-.87L11.42 2l2.98 5.31 5.99.87-4.372 4.2.75 5.97z"/></svg>`,
    title: 'MCP协议',
    desc: 'MCP服务器与全局技能按智能体白名单引用'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3.75 9.776c.112-.017.227-.026.344-.026h15.812c.117 0 .232.009.344.026m-16.5 0a2.25 2.25 0 00-1.883 2.542l.857 6a2.25 2.25 0 002.227 1.932H19.05a2.25 2.25 0 002.227-1.932l.857-6a2.25 2.25 0 00-1.883-2.542m-16.5 0V6A2.25 2.25 0 016 3.75h3.879a1.5 1.5 0 011.06.44l2.122 2.12a1.5 1.5 0 001.06.44H18A2.25 2.25 0 0120.25 9v.776"/></svg>`,
    title: '文件浏览',
    desc: '内置工作区文件浏览与预览，随时查看智能体产出'
  },
  {
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z"/></svg>`,
    title: '用量统计',
    desc: '会话token用量统计，成本一目了然'
  }
]

const useCases = [
  { title: '虚拟团队', desc: '组建多角色智能体团队协作', icon: '🤝' },
  { title: '任务分工', desc: '不同职能智能体各司其职', icon: '🗂️' },
  { title: '并行处理', desc: '多个会话同时推进不同任务', icon: '⚡' },
  { title: '知识沉淀', desc: '独立记忆持续积累领域经验', icon: '🧠' }
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
          <span>Qualia Claw</span>
        </div>

        <!-- Title -->
        <h1 class="hero-title">
          Your Multi-Agent
          <br>
          <span class="title-gradient">Collaboration Platform</span>
        </h1>

        <!-- Subtitle -->
        <p class="hero-subtitle">
          Native Java multi-agent product with independent workspaces, role definitions, and parallel conversations. Build your own AI team.
        </p>

        <!-- Actions -->
        <div class="hero-actions">
          <a href="#quickstart" class="btn btn-primary">
            <span>Get Started</span>
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clip-rule="evenodd"/>
            </svg>
          </a>
          <a href="https://github.com/lunar-landing/qualia" target="_blank" class="btn btn-secondary">
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
            构建并启动您的多智能体团队，支持 Web 与桌面两种部署模式。
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
        <p class="section-desc">为多智能体协作而设计的完整能力集</p>
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
        <p class="section-desc">让多个 AI 智能体为您的业务协同工作</p>
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
          <h2 class="cta-title">组建您的 AI 智能体团队</h2>
          <p class="cta-desc">
            几分钟内创建您的第一个多智能体协作空间
          </p>
          <div class="cta-actions">
            <a href="#quickstart" class="btn btn-white">
              <span>快速开始</span>
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clip-rule="evenodd"/>
              </svg>
            </a>
            <a href="https://github.com/lunar-landing/qualia" target="_blank" class="btn btn-github">
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
  background: rgba(167, 139, 250, 0.3);
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
  background: radial-gradient(circle, rgba(91, 74, 208, 0.3) 0%, transparent 70%);
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
  background: linear-gradient(135deg, #a78bfa, #5b4ad0);
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
  background: rgba(167, 139, 250, 0.1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  color: #a78bfa;
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
  background: linear-gradient(to top, rgba(91, 74, 208, 0.05), transparent);
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
  background: radial-gradient(circle, rgba(167, 139, 250, 0.08) 0%, transparent 70%);
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
  background: rgba(167, 139, 250, 0.08);
}

[data-theme="light"] .cta-gradient {
  background: linear-gradient(to top, rgba(91, 74, 208, 0.03), transparent);
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
