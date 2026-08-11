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



const codeExample = ref(`// 创建 ChatModel
ChatModel model = new DashscopeChatModel(
    "your-api-key", "qwen-turbo"
);

// 创建助手并添加工具
ReActAgent agent = new ReActAgent(model);
agent.addTool(new BaiduSearchTool());
agent.addTool(new DuckDuckGoSearchTool());
agent.addTool(new WebFetchTool());
agent.setSystemPrompt("你是一个智能助手。");

AgentResponse response = agent.call(
    "session-1", "搜索最新的AI新闻"
);

System.out.println(response.getAnswer());`)

const highlightedCode = computed(() => {
  try {
    return hljs.highlight(codeExample.value, { language: 'java' }).value
  } catch {
    return codeExample.value
  }
})

const codeLineCount = computed(() => codeExample.value.split('\n').length)

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
  { value: '6+', label: '核心模块' },
  { value: '10+', label: '内置工具' },
  { value: '100%', label: '开源免费' }
]
</script>

<template>
  <div class="home" :class="{ visible: isVisible }">
    <!-- Hero Section -->
    <section class="hero">
      <!-- Background Effects -->
      <div class="hero-bg">
        <div class="hero-grid"></div>
        <div class="hero-glow"></div>
      </div>

      <div class="hero-content">
        <!-- Title -->
        <h1 class="hero-title">
          Next Framework
          <br>
          <span class="title-gradient">for Building AI Agents</span>
        </h1>

        <!-- Subtitle -->
        <p class="hero-subtitle">
          An enterprise-grade Java AI agent framework with ReAct pattern and MCP toolchain for building LLM-powered intelligent applications.
        </p>

        <!-- Actions -->
        <div class="hero-actions">
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

        <!-- Stats -->
        <div class="hero-stats">
          <div v-for="(stat, index) in stats" :key="index" class="stat-item">
            <span class="stat-value">{{ stat.value }}</span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
        </div>
      </div>

      <!-- Floating Elements -->
      <div class="hero-floaters">
        <div class="floater floater-1"></div>
        <div class="floater floater-2"></div>
        <div class="floater floater-3"></div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features">
      <div class="section-container">
        <div class="features-content">
          <h3 class="section-title">企业级 Java AI 智能体框架</h3>
          <p class="section-desc">
            Qualia 是一个<strong>开箱即用</strong>的 Java AI 智能体框架，支持 ReAct 推理模式、MCP 工具协议、多模型适配，帮助开发者快速构建<strong>生产级</strong> LLM 应用。
          </p>
          <div class="growth-stats">
            <div class="growth-stat">
<div data-component="stat-illustration" class="stat-illustration"><svg viewBox="0 0 120 150" fill="none" xmlns="http://www.w3.org/2000/svg"><g opacity="0.85"><path d="M10 75a50 50 0 0 1 100 0" stroke="#BCBBBB"/><path d="M24 75a36 36 0 0 1 72 0" stroke="#8E8B8B"/><path d="M38 75a22 22 0 0 1 44 0" stroke="#DAD9D9"/><path d="M10 75a50 50 0 0 0 100 0" stroke="#CFCECD" stroke-opacity="0.5"/><path d="M24 75a36 36 0 0 0 72 0" stroke="#CFCECD" stroke-opacity="0.5"/><path d="M38 75a22 22 0 0 0 44 0" stroke="#CFCECD" stroke-opacity="0.5"/><path d="M60 59L68 67L60 75L52 67Z" fill="#BCBBBB"/><circle cx="60" cy="75" r="3.5" fill="#F1F0F0"/><circle cx="10" cy="75" r="3" fill="#8E8B8B"/><circle cx="110" cy="75" r="3" fill="#8E8B8B"/><circle cx="24" cy="75" r="2.5" fill="#BCBBBB"/><circle cx="96" cy="75" r="2.5" fill="#BCBBBB"/><circle cx="60" cy="25" r="3" fill="#DAD9D9"/></g></svg></div>
              <span class="stat-label">ReAct 推理</span>
            </div>
            <div class="growth-stat">
              <div data-component="stat-illustration" class="stat-illustration"><svg viewBox="0 0 120 150" fill="none" xmlns="http://www.w3.org/2000/svg"><g opacity="0.85"><rect x="58" y="21" width="4" height="108" fill="#CFCECD" opacity="0.5"/><rect x="30" y="33" width="6" height="6" fill="#8E8B8B" opacity="0.9"/><rect x="84" y="33" width="6" height="6" fill="#8E8B8B" opacity="0.9"/><rect x="44" y="47" width="6" height="6" fill="#BCBBBB" opacity="0.7"/><rect x="70" y="47" width="6" height="6" fill="#BCBBBB" opacity="0.7"/><rect x="58" y="47" width="4" height="6" fill="#DAD9D9" opacity="0.8"/><rect x="30" y="61" width="6" height="6" fill="#BCBBBB" opacity="0.6"/><rect x="84" y="61" width="6" height="6" fill="#BCBBBB" opacity="0.6"/><rect x="44" y="61" width="6" height="6" fill="#CFCECD" opacity="0.9"/><rect x="70" y="61" width="6" height="6" fill="#CFCECD" opacity="0.9"/><rect x="16" y="75" width="6" height="6" fill="#CFCECD" opacity="0.5"/><rect x="98" y="75" width="6" height="6" fill="#CFCECD" opacity="0.5"/><rect x="30" y="75" width="6" height="6" fill="#DAD9D9" opacity="0.8"/><rect x="84" y="75" width="6" height="6" fill="#DAD9D9" opacity="0.8"/><rect x="44" y="75" width="6" height="6" fill="#8E8B8B" opacity="0.9"/><rect x="70" y="75" width="6" height="6" fill="#8E8B8B" opacity="0.9"/><rect x="58" y="73" width="4" height="10" fill="#F1F0F0"/><rect x="30" y="89" width="6" height="6" fill="#BCBBBB" opacity="0.6"/><rect x="84" y="89" width="6" height="6" fill="#BCBBBB" opacity="0.6"/><rect x="44" y="89" width="6" height="6" fill="#CFCECD" opacity="0.9"/><rect x="70" y="89" width="6" height="6" fill="#CFCECD" opacity="0.9"/><rect x="58" y="97" width="4" height="6" fill="#DAD9D9" opacity="0.8"/><rect x="44" y="103" width="6" height="6" fill="#BCBBBB" opacity="0.7"/><rect x="70" y="103" width="6" height="6" fill="#BCBBBB" opacity="0.7"/><rect x="30" y="117" width="6" height="6" fill="#8E8B8B" opacity="0.9"/><rect x="84" y="117" width="6" height="6" fill="#8E8B8B" opacity="0.9"/></g></svg></div>
              <span class="stat-label">MCP 协议</span>
            </div>
            <div class="growth-stat">
<div data-component="stat-illustration" class="stat-illustration"><svg viewBox="0 0 120 150" fill="none" xmlns="http://www.w3.org/2000/svg"><g opacity="0.85"><rect x="14" y="37" width="12" height="36" fill="#BCBBBB" opacity="0.5"/><rect x="94" y="37" width="12" height="36" fill="#BCBBBB" opacity="0.5"/><rect x="14" y="87" width="12" height="36" fill="#BCBBBB" opacity="0.5"/><rect x="94" y="87" width="12" height="36" fill="#BCBBBB" opacity="0.5"/><rect x="28" y="27" width="12" height="46" fill="#8E8B8B" opacity="0.8"/><rect x="80" y="27" width="12" height="46" fill="#8E8B8B" opacity="0.8"/><rect x="28" y="87" width="12" height="46" fill="#8E8B8B" opacity="0.8"/><rect x="80" y="87" width="12" height="46" fill="#8E8B8B" opacity="0.8"/><rect x="42" y="17" width="12" height="56" fill="#CFCECD" opacity="0.8"/><rect x="66" y="17" width="12" height="56" fill="#CFCECD" opacity="0.8"/><rect x="42" y="87" width="12" height="56" fill="#CFCECD" opacity="0.8"/><rect x="66" y="87" width="12" height="56" fill="#CFCECD" opacity="0.8"/><rect x="58" y="27" width="4" height="46" fill="#DAD9D9"/><rect x="58" y="87" width="4" height="46" fill="#DAD9D9"/></g></svg></div>
              <span class="stat-label">多模型适配</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Code Section -->
    <section class="code-section">
      <div class="section-container">
        <div class="code-content">
          <h2 class="section-title">Quick Start</h2>
          <p class="section-desc">
            Build a complete ReAct agent in just a few lines of Java code.
          </p>
          <div class="code-block">
            <div class="code-block-header">
              <div class="code-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <span class="code-lang">QualiaAgent.java</span>
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
              <div class="code-gutter">
                <span v-for="n in codeLineCount" :key="n">{{ n }}</span>
              </div>
              <pre><code class="language-java" v-html="highlightedCode"></code></pre>
            </div>
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
          <h2 class="cta-title">准备好开始了吗？</h2>
          <p class="cta-desc">
            几分钟内即可创建您的第一个 AI 智能体
          </p>
          <div class="cta-actions">
            <router-link to="/docs/qualia-docs" class="btn btn-white">
              <span>阅读文档</span>
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clip-rule="evenodd"/>
              </svg>
            </router-link>
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
.home {
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.6s cubic-bezier(0.2, 0, 0, 1);
}

.home.visible {
  opacity: 1;
  transform: translateY(0);
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

.badge-arrow {
  color: #9a9898;
  transition: transform 150ms cubic-bezier(0.2, 0, 0, 1);
}

.hero-badge:hover .badge-arrow {
  transform: translateX(4px);
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

/* Floating Elements - removed for OpenCode flat style */
.hero-floaters {
  display: none;
}

/* ============================================
   Sections Common
   ============================================ */
.section-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 24px;
}

.section-header {
  text-align: center;
  margin-bottom: 64px;
}

.section-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 16px;
  background: #302c2c;
  color: #fdfcfc;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  margin-bottom: 16px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #fdfcfc;
  margin-bottom: 16px;
  letter-spacing: 0;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.section-desc {
  font-size: 16px;
  color: #9a9898;
  max-width: 600px;
  margin: 0 auto;
  line-height: 1.50;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* ============================================
   Features Section
   ============================================ */
.features {
  padding: 96px 0;
  background: #201d1d;
}

.features-content {
  text-align: center;
  width: 100%;
}

.growth-stats {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-top: 60px;
  width: 100%;
}

.growth-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.stat-illustration {
  width: 100%;
  max-width: 120px;
  height: 150px;
}

.stat-illustration svg {
  width: 100%;
  height: 100%;
}

.stat-label {
  font-size: 16px;
  font-weight: 700;
  color: #fdfcfc;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
  white-space: nowrap;
}

.section-desc strong {
  color: #fdfcfc;
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
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
}

.code-content .section-desc {
  margin-bottom: 48px;
}

.code-block {
  position: relative;
  background: #262323;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #443f3f;
  text-align: left;
}

.code-block-header {
  display: flex;
  align-items: center;
  padding: 14px 12px 14px 0;
  background: #201d1d;
  border-bottom: 1px solid rgba(15, 0, 0, 0.12);
}

.code-dots {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
}

.code-dots span {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.code-dots span:first-child { background: #ff3b30; }
.code-dots span:nth-child(2) { background: #ff9f0a; }
.code-dots span:last-child { background: #30d158; }

.code-lang {
  display: flex;
  align-items: center;
  padding-left: 4px;
  color: #9a9898;
  font-size: 12px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.copy-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  align-self: center;
  margin-left: auto;
  margin-right: 12px;
  padding: 6px 12px;
  background: transparent;
  color: #9a9898;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  transition: all 150ms cubic-bezier(0.2, 0, 0, 1);
  border: 1px solid #443f3f;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.copy-btn:hover {
  background: #302c2c;
  border-color: #fdfcfc;
  color: #fdfcfc;
}

.copy-btn svg {
  width: 14px;
  height: 14px;
}

.code-body {
  display: flex;
  padding: 20px 0;
  overflow-x: auto;
}

.code-gutter {
  display: flex;
  flex-direction: column;
  padding: 0 16px 0 20px;
  text-align: right;
  user-select: none;
  flex-shrink: 0;
  border-right: 1px solid rgba(15, 0, 0, 0.12);
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
  font-size: 14px;
  line-height: 1.70;
  color: #524f4f;
}

.code-body pre {
  margin: 0;
  padding: 0 24px 0 16px;
  flex: 1;
  min-width: 0;
}

.code-body code {
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
  font-size: 14px;
  line-height: 1.70;
  color: #e8e6e3;
  font-feature-settings: 'liga' 1, 'calt' 1;
}

/* Syntax Highlighting - Soft Blue Palette */
.code-body :deep(.hljs-keyword),
.code-body :deep(.hljs-selector-tag) {
  color: #7ab0ff;
}

.code-body :deep(.hljs-string),
.code-body :deep(.hljs-template-variable) {
  color: #7ee787;
}

.code-body :deep(.hljs-comment),
.code-body :deep(.hljs-doctag) {
  color: #6e6e73;
  font-style: italic;
}

.code-body :deep(.hljs-type),
.code-body :deep(.hljs-class .hljs-title),
.code-body :deep(.hljs-title.class_) {
  color: #9ecbff;
}

.code-body :deep(.hljs-number),
.code-body :deep(.hljs-literal) {
  color: #f0a45d;
}

.code-body :deep(.hljs-function),
.code-body :deep(.hljs-title.function_) {
  color: #7ab0ff;
}

.code-body :deep(.hljs-params) {
  color: #c8c6c4;
}

.code-body :deep(.hljs-built_in) {
  color: #4cc2d6;
}

.code-body :deep(.hljs-meta),
.code-body :deep(.hljs-meta .hljs-keyword) {
  color: #4cc2d6;
}

.code-body :deep(.hljs-variable),
.code-body :deep(.hljs-attr) {
  color: #b39dfa;
}

.code-body :deep(.hljs-symbol),
.code-body :deep(.hljs-bullet) {
  color: #f0a45d;
}

.code-body :deep(.hljs-addition) {
  color: #7ee787;
}

.code-body :deep(.hljs-deletion) {
  color: #ff6b60;
}


/* ============================================
   CTA Section
   ============================================ */
.cta {
  position: relative;
  padding: 80px 0;
  background: #201d1d;
  overflow: hidden;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.cta-bg {
  position: absolute;
  inset: 0;
}

.cta-gradient {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(15, 0, 0, 0.3) 0%, transparent 70%);
  transform: translate(-50%, -50%);
}

.cta-content {
  position: relative;
  text-align: center;
  z-index: 1;
}

.cta-title {
  font-size: 22px;
  font-weight: 700;
  color: #fdfcfc;
  margin-bottom: 16px;
  letter-spacing: 0;
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
  .hero {
    padding: 80px 24px 64px;
  }

  .hero-title {
    font-size: 28px;
  }
}

@media (max-width: 768px) {
  .hero {
    padding: 64px 16px 48px;
  }

  .hero-title {
    font-size: 24px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .hero-subtitle br {
    display: none;
  }

  .hero-actions {
    flex-direction: column;
    align-items: center;
  }

  .btn {
    width: 100%;
    max-width: 280px;
    justify-content: center;
  }

  .hero-stats {
    gap: 24px;
  }

  .stat-item {
    padding: 0 24px;
  }

  .stat-value {
    font-size: 22px;
  }

  .section-title {
    font-size: 18px;
  }

  .cta-title {
    font-size: 18px;
  }

  .code-section {
    padding: 64px 0;
  }

  .features {
    padding: 64px 0;
  }

  .features-list li {
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .hero-title {
    font-size: 20px;
  }

  .hero-stats {
    flex-wrap: wrap;
    gap: 16px;
  }

  .stat-item {
    flex: 1;
    min-width: 80px;
  }
}

/* ============================================
   Light Theme Overrides - OpenCode Style
   ============================================ */
/* Hero - Light */
[data-theme="light"] .hero {
  background: #f8f7f7;
}

[data-theme="light"] .hero-grid {
  background-image:
    linear-gradient(rgba(15, 0, 0, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 0, 0, 0.04) 1px, transparent 1px);
}

[data-theme="light"] .hero-glow {
  background: radial-gradient(circle, rgba(15, 0, 0, 0.08) 0%, transparent 70%);
}

[data-theme="light"] .hero-title {
  color: #1c1919;
}

[data-theme="light"] .title-gradient {
  background: #007aff;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

[data-theme="light"] .hero-subtitle {
  color: #6e6e73;
}

/* Buttons - Light */
[data-theme="light"] .btn-primary {
  background: #1c1919;
  color: #f8f7f7;
  border-color: #646262;
}

[data-theme="light"] .btn-primary:hover {
  background: #302c2c;
  color: #f8f7f7;
}

[data-theme="light"] .btn-secondary {
  background: transparent;
  color: #1c1919;
  border-color: #646262;
}

[data-theme="light"] .btn-secondary:hover {
  background: #f1eeee;
  color: #1c1919;
}

[data-theme="light"] .btn-white {
  background: #1c1919;
  color: #f8f7f7;
  border-color: #646262;
}

[data-theme="light"] .btn-white:hover {
  background: #302c2c;
  color: #f8f7f7;
}

[data-theme="light"] .btn-github {
  background: transparent;
  color: #1c1919;
  border-color: #646262;
}

[data-theme="light"] .btn-github:hover {
  background: #f1eeee;
  color: #1c1919;
}

/* Stats - Light */
[data-theme="light"] .stat-value {
  color: #1c1919;
}

[data-theme="light"] .stat-item:not(:last-child)::after {
  background: rgba(15, 0, 0, 0.12);
}

/* Features - Light */
[data-theme="light"] .features {
  background: #f8f7f7;
}

[data-theme="light"] .section-title {
  color: #1c1919;
}

[data-theme="light"] .stat-label {
  color: #1c1919;
}

[data-theme="light"] .section-desc strong {
  color: #1c1919;
}

/* Code Section - Light */
[data-theme="light"] .code-section {
  background: #f1eeee;
  border-top-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .code-block {
  background: #ffffff;
  border-color: #d5d0d0;
}

[data-theme="light"] .code-block-header {
  background: #f1eeee;
  border-bottom-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .code-lang {
  color: #6e6e73;
}

[data-theme="light"] .code-body code {
  color: #333030;
}

[data-theme="light"] .code-body :deep(.hljs-keyword),
[data-theme="light"] .code-body :deep(.hljs-selector-tag) {
  color: #0b63ce;
}

[data-theme="light"] .code-body :deep(.hljs-string),
[data-theme="light"] .code-body :deep(.hljs-template-variable) {
  color: #1a7f37;
}

[data-theme="light"] .code-body :deep(.hljs-comment),
[data-theme="light"] .code-body :deep(.hljs-doctag) {
  color: #9a9898;
  font-style: italic;
}

[data-theme="light"] .code-body :deep(.hljs-type),
[data-theme="light"] .code-body :deep(.hljs-class .hljs-title),
[data-theme="light"] .code-body :deep(.hljs-title.class_) {
  color: #0550ae;
}

[data-theme="light"] .code-body :deep(.hljs-number),
[data-theme="light"] .code-body :deep(.hljs-literal) {
  color: #b35c00;
}

[data-theme="light"] .code-body :deep(.hljs-function),
[data-theme="light"] .code-body :deep(.hljs-title.function_) {
  color: #0b63ce;
}

[data-theme="light"] .code-body :deep(.hljs-params) {
  color: #6e6e73;
}

[data-theme="light"] .code-body :deep(.hljs-built_in) {
  color: #0e7c86;
}

[data-theme="light"] .code-body :deep(.hljs-meta),
[data-theme="light"] .code-body :deep(.hljs-meta .hljs-keyword) {
  color: #0e7c86;
}

[data-theme="light"] .code-body :deep(.hljs-variable),
[data-theme="light"] .code-body :deep(.hljs-attr) {
  color: #6f42c1;
}

[data-theme="light"] .code-body :deep(.hljs-symbol),
[data-theme="light"] .code-body :deep(.hljs-bullet) {
  color: #b35c00;
}

[data-theme="light"] .code-body :deep(.hljs-addition) {
  color: #1a7f37;
}

[data-theme="light"] .code-body :deep(.hljs-deletion) {
  color: #cf222e;
}

[data-theme="light"] .copy-btn {
  background: transparent;
  color: #6e6e73;
  border-color: #d5d0d0;
}

[data-theme="light"] .copy-btn:hover {
  background: #f1eeee;
  border-color: #1c1919;
  color: #1c1919;
}

/* Code Gutter - Light */
[data-theme="light"] .code-gutter {
  color: #b3aeae;
  border-right-color: rgba(15, 0, 0, 0.12);
}

/* CTA - Light */
[data-theme="light"] .cta {
  background: #f8f7f7;
  border-top-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .cta-gradient {
  background: radial-gradient(circle, rgba(15, 0, 0, 0.08) 0%, transparent 70%);
}

[data-theme="light"] .cta-title {
  color: #1c1919;
}

[data-theme="light"] .cta-desc {
  color: #6e6e73;
}

</style>
