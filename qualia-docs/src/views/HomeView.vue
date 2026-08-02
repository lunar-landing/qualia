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

const codeSteps = [
  {
    step: '01',
    title: '创建模型',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z"/></svg>`,
    color: 'primary'
  },
  {
    step: '02',
    title: '注册工具',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11.42 15.17l-5.384 3.18.75-5.97L2.414 8.18l5.99-.87L11.42 2l2.98 5.31 5.99.87-4.372 4.2.75 5.97z"/></svg>`,
    color: 'violet'
  },
  {
    step: '03',
    title: '创建助手',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19 14.5M14.25 3.104c.251.023.501.05.75.082M19 14.5l-2.47 6.174a1.125 1.125 0 01-1.04.726H8.51a1.125 1.125 0 01-1.04-.726L5 14.5m14 0H5"/></svg>`,
    color: 'cyan'
  },
  {
    step: '04',
    title: '执行推理',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M5.25 14.25h13.5m-13.5 0a3 3 0 01-3-3m3 3a3 3 0 100 6h13.5a3 3 0 100-6m-16.5-3a3 3 0 013-3h13.5a3 3 0 013 3m-19.5 0a4.5 4.5 0 01.9-2.7L5.737 5.1a3.375 3.375 0 012.7-1.35h7.126c1.062 0 2.062.5 2.7 1.35l2.587 3.45a4.5 4.5 0 01.9 2.7m0 0h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008zm-3 6h.008v.008h-.008v-.008zm-3 0h.008v.008h-.008v-.008z"/></svg>`,
    color: 'emerald'
  }
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
<div data-component="stat-illustration"><svg width="205" height="264" viewBox="0 0 205 264" fill="none" xmlns="http://www.w3.org/2000/svg"><g opacity="0.5" clip-path="url(#clip0_236_15902)"><mask id="mask0_236_15902" style="mask-type:alpha" maskUnits="userSpaceOnUse" x="0" y="0" width="205" height="264"><path d="M27.2119 253.122L0 264H205V0L192.109 17.8482L175.297 43.8089L152.877 59.95L137.902 77.6701L126.989 87.3251L118.603 106.449L103.114 123.643L93.359 141.714L84.2883 160.311L78.7262 177.329L67.773 193.997L62.8098 212.068L57.3332 231.191L42.5292 243.824L27.2119 253.122Z" fill="url(#paint0_linear_236_15902)"></path></mask><g mask="url(#mask0_236_15902)"><path d="M150.932 -135.014L-251.766 267.684M154.115 -131.832L-248.582 270.865M157.295 -128.65L-245.402 274.047M160.479 -125.469L-242.219 277.229M163.662 -122.287L-239.035 280.41M166.842 -119.105L-235.855 283.592M170.025 -115.924L-232.672 286.773M173.205 -112.742L-229.492 289.955M176.385 -109.561L-226.312 293.137M179.568 -106.377L-223.129 296.32M182.752 -103.193L-219.945 299.504M185.936 -100.012L-216.762 302.686M189.119 -96.8301L-213.578 305.867M192.295 -93.6484L-210.402 309.049M195.479 -90.4668L-207.219 312.23M198.662 -87.2852L-204.035 315.412M201.842 -84.1035L-200.855 318.594M205.025 -80.9219L-197.672 321.775M208.209 -77.7383L-194.488 324.959M211.389 -74.5586L-191.309 328.139M214.568 -71.375L-188.129 331.322M217.752 -68.1934L-184.945 334.504M220.936 -65.0117L-181.762 337.686M224.119 -61.8281L-178.578 340.869M227.303 -58.6465L-175.395 344.051M230.482 -55.4668L-172.215 347.23M233.662 -52.2832L-169.035 350.414M236.846 -49.0996L-165.852 353.598M240.025 -45.9199L-162.672 356.777M243.209 -42.7383L-159.488 359.959M246.393 -39.5547L-156.305 363.143M249.572 -36.375L-153.125 366.322M252.756 -33.1934L-149.941 369.504M255.936 -30.0098L-146.762 372.688M259.119 -26.8281L-143.578 375.869M262.303 -23.6465L-140.395 379.051M265.486 -20.4609L-137.211 382.236M268.666 -17.2812L-134.031 385.416M271.85 -14.0996L-130.848 388.598M275.029 -10.918L-127.668 391.779M278.209 -7.73633L-124.488 394.961M281.393 -4.55469L-121.305 398.143M284.576 -1.37305L-118.121 401.324M287.756 1.80859L-114.941 404.506M290.94 4.99023L-111.758 407.688M294.119 8.17383L-108.578 410.871M297.303 11.3574L-105.395 414.055M300.486 14.5391L-102.211 417.236M303.67 17.7207L-99.0273 420.418M306.85 20.9023L-95.8477 423.6M310.033 24.084L-92.6641 426.781M313.213 27.2656L-89.4844 429.963M316.393 30.4473L-86.3047 433.145M319.576 33.6289L-83.1211 436.326M322.76 36.8125L-79.9375 439.51M325.94 39.9941L-76.7578 442.691M329.123 43.1758L-73.5742 445.873M332.307 46.3574L-70.3906 449.055M335.486 49.541L-67.2109 452.238M338.67 52.7227L-64.0273 455.42M341.854 55.9043L-60.8438 458.602M345.033 59.0859L-57.6641 461.783M348.217 62.2676L-54.4805 464.965M351.397 65.4512L-51.3008 468.148M354.576 68.6328L-48.1211 471.33M357.76 71.8145L-44.9375 474.512M360.943 74.9961L-41.7539 477.693M364.123 78.1777L-38.5742 480.875M367.307 81.3594L-35.3906 484.057M370.49 84.541L-32.207 487.238M373.67 87.7246L-29.0273 490.422M376.854 90.9062L-25.8438 493.604M380.033 94.0859L-22.6641 496.783M383.217 97.2695L-19.4805 499.967M386.4 100.453L-16.2969 503.15M389.58 103.633L-13.1172 506.33M392.76 106.816L-9.9375 509.514" stroke="#8E8B8B"></path></g><path d="M0 264L27.2119 253.122L42.5292 243.824L57.3332 231.191L62.8098 212.068L67.773 193.997L78.7262 177.329L84.2883 160.311L93.359 141.714L103.114 123.643L118.603 106.449L126.989 87.3251L137.902 77.6701L152.877 59.95L175.297 43.8089L192.109 17.8482L205 0" stroke="#BCBBBB"></path></g><defs><linearGradient id="paint0_linear_236_15902" x1="102.5" y1="-34.8571" x2="102.5" y2="264" gradientUnits="userSpaceOnUse"><stop stop-color="#565656"></stop><stop offset="1" stop-color="#F1F0F0" stop-opacity="0"></stop></linearGradient><clipPath id="clip0_236_15902"><rect width="205" height="264" fill="white"></rect></clipPath></defs></svg></div>
              <span class="stat-label">ReAct 推理</span>
            </div>
            <div class="growth-stat">
              <div data-component="stat-illustration"><svg width="205" height="264" viewBox="0 0 205 264" fill="none" xmlns="http://www.w3.org/2000/svg"><g opacity="0.5" clip-path="url(#clip0_236_15557)"><g clip-path="url(#clip1_236_15557)"><rect opacity="0.81" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.46" x="14" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.86" x="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.08" x="42" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.23" x="56" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.9" x="70" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.59" x="84" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.8" x="98" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.21" x="112" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.22" x="126" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.62" x="140" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.41" x="154" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.22" x="168" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.25" x="182" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.34" x="196" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.84" y="14" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.79" x="14" y="14" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.49" x="28" y="14" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.49" x="42" y="14" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.05" x="56" y="14" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.59" x="70" y="14" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.44" x="84" y="14" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.21" x="98" y="14" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.53" x="112" y="14" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.81" x="126" y="14" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.24" x="140" y="14" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.61" x="154" y="14" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.14" x="168" y="14" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.26" x="182" y="14" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.8" x="196" y="14" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.02" y="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.69" x="14" y="28" width="6" height="6" fill="#CFCECD"></rect><rect x="28" y="28" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.4" x="42" y="28" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.88" x="56" y="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.38" x="70" y="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.38" x="84" y="28" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.78" x="98" y="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.49" x="112" y="28" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.13" x="126" y="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.76" x="140" y="28" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.35" x="154" y="28" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.59" x="168" y="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.34" x="182" y="28" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.3" x="196" y="28" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.6" y="42" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.3" x="14" y="42" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.65" x="28" y="42" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.41" x="42" y="42" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.84" x="56" y="42" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.33" x="70" y="42" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.81" x="84" y="42" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.78" x="98" y="42" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.72" x="112" y="42" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.71" x="126" y="42" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.46" x="140" y="42" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.06" x="154" y="42" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.05" x="168" y="42" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.44" x="182" y="42" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.09" x="196" y="42" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.03" y="56" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.58" x="14" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.24" x="28" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.1" x="42" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.09" x="56" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.3" x="70" y="56" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.6" x="84" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.39" x="98" y="56" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.53" x="112" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.83" x="126" y="56" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.25" x="140" y="56" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.87" x="154" y="56" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.38" x="168" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.19" x="182" y="56" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.89" x="196" y="56" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.98" y="70" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.26" x="14" y="70" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.79" x="28" y="70" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.67" x="56" y="70" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.48" x="70" y="70" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.76" x="84" y="70" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.72" x="98" y="70" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.01" x="112" y="70" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.46" x="126" y="70" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.27" x="140" y="70" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.78" x="154" y="70" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.16" x="168" y="70" width="6" height="6" fill="#CFCECD"></rect><rect x="182" y="70" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.86" x="196" y="70" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.18" y="84" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.04" x="14" y="84" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.61" x="28" y="84" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.47" x="42" y="84" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.81" x="56" y="84" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.98" x="70" y="84" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.3" x="84" y="84" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.1" x="98" y="84" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.42" x="112" y="84" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.66" x="126" y="84" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.68" x="140" y="84" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.35" x="154" y="84" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.6" x="168" y="84" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.95" x="182" y="84" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.05" x="196" y="84" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.77" y="98" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.06" x="14" y="98" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.45" x="28" y="98" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.73" x="42" y="98" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.21" x="70" y="98" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.18" x="84" y="98" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.92" x="98" y="98" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.26" x="112" y="98" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.21" x="126" y="98" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.27" x="140" y="98" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.84" x="154" y="98" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.74" x="168" y="98" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.53" x="182" y="98" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.9" x="196" y="98" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.32" y="112" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.75" x="14" y="112" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.69" x="28" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.66" x="42" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.93" x="56" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.32" x="70" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.52" x="84" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.02" x="98" y="112" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.88" x="126" y="112" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.12" x="140" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.93" x="154" y="112" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.79" x="168" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.24" x="182" y="112" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.64" x="196" y="112" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.57" y="126" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.6" x="14" y="126" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.05" x="28" y="126" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.28" x="42" y="126" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.21" x="56" y="126" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.93" x="70" y="126" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.63" x="84" y="126" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.58" x="98" y="126" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.64" x="112" y="126" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.74" x="126" y="126" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.74" x="140" y="126" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.1" x="154" y="126" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.93" x="168" y="126" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.43" x="182" y="126" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.45" x="196" y="126" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.77" y="140" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.78" x="14" y="140" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.18" x="28" y="140" width="6" height="6" fill="#DAD9D9"></rect><rect x="42" y="140" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.39" x="56" y="140" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.53" x="70" y="140" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.06" x="84" y="140" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.81" x="98" y="140" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.49" x="112" y="140" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.45" x="126" y="140" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.37" x="140" y="140" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.58" x="154" y="140" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.8" x="168" y="140" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.35" x="182" y="140" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.73" x="196" y="140" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.92" y="154" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.32" x="14" y="154" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.3" x="28" y="154" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.03" x="42" y="154" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.65" x="56" y="154" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.66" x="70" y="154" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.83" x="84" y="154" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.52" x="98" y="154" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.82" x="112" y="154" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.95" x="126" y="154" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.89" x="140" y="154" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.2" x="154" y="154" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.61" x="168" y="154" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.34" x="196" y="154" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.9" y="168" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.99" x="14" y="168" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.49" x="28" y="168" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.84" x="42" y="168" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.67" x="56" y="168" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.92" x="70" y="168" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.79" x="84" y="168" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.8" x="98" y="168" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.74" x="112" y="168" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.38" x="126" y="168" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.56" x="140" y="168" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.7" x="154" y="168" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.47" x="168" y="168" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.92" x="182" y="168" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.19" x="196" y="168" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.12" y="182" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.16" x="14" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.98" x="28" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.6" x="42" y="182" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.15" x="56" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.17" x="70" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.26" x="84" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.3" x="98" y="182" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.12" x="112" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.31" x="126" y="182" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.62" x="140" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.74" x="154" y="182" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.8" x="168" y="182" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.89" x="182" y="182" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.75" x="196" y="182" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.1" y="196" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.11" x="14" y="196" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.79" x="28" y="196" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.69" x="42" y="196" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.39" x="56" y="196" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.31" x="70" y="196" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.33" x="84" y="196" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.2" x="98" y="196" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.21" x="112" y="196" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.02" x="126" y="196" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.82" x="140" y="196" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.28" x="154" y="196" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.19" x="168" y="196" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.97" x="182" y="196" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.45" x="196" y="196" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.88" y="210" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.58" x="14" y="210" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.53" x="28" y="210" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.89" x="42" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.38" x="56" y="210" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.73" x="70" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.87" x="84" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.35" x="98" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.61" x="112" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.8" x="126" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.87" x="140" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.77" x="154" y="210" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.94" x="168" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.59" x="182" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.37" x="196" y="210" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.7" y="224" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.72" x="14" y="224" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.95" x="28" y="224" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.26" x="42" y="224" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.68" x="56" y="224" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.55" x="70" y="224" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.2" x="84" y="224" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.63" x="98" y="224" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.5" x="112" y="224" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.79" x="126" y="224" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.02" x="140" y="224" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.17" x="154" y="224" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.99" x="168" y="224" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.82" x="182" y="224" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.28" x="196" y="224" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.76" y="238" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.39" x="14" y="238" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.14" x="28" y="238" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.17" x="42" y="238" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.37" x="56" y="238" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.13" x="70" y="238" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.35" x="84" y="238" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.13" x="98" y="238" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.55" x="112" y="238" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.83" x="126" y="238" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.86" x="140" y="238" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.63" x="154" y="238" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.38" x="168" y="238" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.57" x="182" y="238" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.13" x="196" y="238" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.9" y="252" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.63" x="14" y="252" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.23" x="28" y="252" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.56" x="42" y="252" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.38" x="56" y="252" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.19" x="70" y="252" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.29" x="84" y="252" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.78" x="98" y="252" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.14" x="112" y="252" width="6" height="6" fill="#BCBBBB"></rect><rect opacity="0.64" x="126" y="252" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.27" x="140" y="252" width="6" height="6" fill="#CFCECD"></rect><rect opacity="0.85" x="154" y="252" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.02" x="168" y="252" width="6" height="6" fill="#DAD9D9"></rect><rect opacity="0.29" x="182" y="252" width="6" height="6" fill="#8E8B8B"></rect><rect opacity="0.4" x="196" y="252" width="6" height="6" fill="#8E8B8B"></rect></g></g><defs><clipPath id="clip0_236_15557"><rect width="205" height="264" fill="white"></rect></clipPath><clipPath id="clip1_236_15557"><rect width="236" height="264" fill="white" transform="translate(-0.164062)"></rect></clipPath></defs></svg></div>
              <span class="stat-label">MCP 协议</span>
            </div>
            <div class="growth-stat">
<div data-component="stat-illustration"><svg width="205" height="264" viewBox="0 0 205 264" fill="none" xmlns="http://www.w3.org/2000/svg"><g opacity="0.5"><path d="M205 0H203.985V264H205V0Z" fill="#8E8B8B"></path><path d="M197.896 34H196.881V264H197.896V34Z" fill="#8E8B8B"></path><path d="M189.777 26H188.762V264H189.777V26Z" fill="#8E8B8B"></path><path d="M183.688 52H182.673V264H183.688V52Z" fill="#8E8B8B"></path><path d="M176.584 0H175.569V264H176.584V0Z" fill="#8E8B8B"></path><path d="M169.48 29H168.465V264H169.48V29Z" fill="#8E8B8B"></path><path d="M162.376 44H161.361V264H162.376V44Z" fill="#8E8B8B"></path><path d="M155.272 65H154.257V264H155.272V65Z" fill="#8E8B8B"></path><path d="M149.183 29H148.168V264H149.183V29Z" fill="#8E8B8B"></path><path d="M142.079 36H141.064V264H142.079V36Z" fill="#8E8B8B"></path><path d="M134.975 48H133.96V264H134.975V48Z" fill="#8E8B8B"></path><path d="M127.871 7H126.856V264H127.871V7Z" fill="#8E8B8B"></path><path d="M120.767 0H119.752V264H120.767V0Z" fill="#8E8B8B"></path><path d="M113.663 14H112.649V264H113.663V14Z" fill="#8E8B8B"></path><path d="M106.559 27H105.545V264H106.559V27Z" fill="#8E8B8B"></path><path d="M99.4554 70H98.4406V264H99.4554V70Z" fill="#8E8B8B"></path><path d="M92.3515 32H91.3366V264H92.3515V32Z" fill="#8E8B8B"></path><path d="M85.2475 35H84.2327V264H85.2475V35Z" fill="#8E8B8B"></path><path d="M78.1436 36H77.1287V264H78.1436V36Z" fill="#8E8B8B"></path><path d="M71.0396 10H70.0248V264H71.0396V10Z" fill="#8E8B8B"></path><path d="M63.9356 42H62.9208V264H63.9356V42Z" fill="#8E8B8B"></path><path d="M56.8317 43H55.8168V264H56.8317V43Z" fill="#8E8B8B"></path><path d="M49.7277 38H48.7129V264H49.7277V38Z" fill="#8E8B8B"></path><path d="M42.6238 56H41.6089V264H42.6238V56Z" fill="#8E8B8B"></path><path d="M36.5347 36H35.5198V264H36.5347V36Z" fill="#8E8B8B"></path><path d="M29.4307 8H28.4158V264H29.4307V8Z" fill="#8E8B8B"></path><path d="M22.3267 20H21.3119V264H22.3267V20Z" fill="#8E8B8B"></path><path d="M15.2228 1H14.2079V264H15.2228V1Z" fill="#8E8B8B"></path><path d="M8.11881 9H7.10396V264H8.11881V9Z" fill="#8E8B8B"></path><path d="M1.01485 31H0V264H1.01485V31Z" fill="#8E8B8B"></path></g></svg></div>
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
  background: #302c2c;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #646262;
  text-align: left;
}

.code-block-header {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  background: #262323;
  border-bottom: 1px solid rgba(15, 0, 0, 0.12);
}

.code-dots {
  display: flex;
  gap: 8px;
  margin-right: 16px;
}

.code-dots span {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  transition: all 150ms cubic-bezier(0.2, 0, 0, 1);
}

.code-dots span:first-child { background: #ff3b30; }
.code-dots span:nth-child(2) { background: #ff9f0a; }
.code-dots span:last-child { background: #30d158; }

.code-lang {
  color: #9a9898;
  font-size: 12px;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
  flex: 1;
}

.copy-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: transparent;
  color: #9a9898;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  transition: all 150ms cubic-bezier(0.2, 0, 0, 1);
  border: 1px solid #646262;
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
  padding: 24px;
  overflow-x: auto;
}

.code-body pre {
  margin: 0;
}

.code-body code {
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
  font-size: 14px;
  line-height: 1.50;
  font-feature-settings: 'liga' 1, 'calt' 1;
}

/* Syntax Highlighting - Simplified for OpenCode */
.code-body :deep(.hljs-keyword),
.code-body :deep(.hljs-selector-tag) {
  color: #007aff;
}

.code-body :deep(.hljs-string),
.code-body :deep(.hljs-template-variable) {
  color: #30d158;
}

.code-body :deep(.hljs-comment),
.code-body :deep(.hljs-doctag) {
  color: #6e6e73;
  font-style: italic;
}

.code-body :deep(.hljs-type),
.code-body :deep(.hljs-class .hljs-title),
.code-body :deep(.hljs-title.class_) {
  color: #007aff;
}

.code-body :deep(.hljs-number),
.code-body :deep(.hljs-literal) {
  color: #ff9f0a;
}

.code-body :deep(.hljs-function),
.code-body :deep(.hljs-title.function_) {
  color: #007aff;
}

.code-body :deep(.hljs-params) {
  color: #c8c6c4;
}

.code-body :deep(.hljs-built_in) {
  color: #30b0c7;
}

.code-body :deep(.hljs-meta),
.code-body :deep(.hljs-meta .hljs-keyword) {
  color: #30b0c7;
}

.code-body :deep(.hljs-variable),
.code-body :deep(.hljs-attr) {
  color: #5856d6;
}

.code-body :deep(.hljs-symbol),
.code-body :deep(.hljs-bullet) {
  color: #ff9f0a;
}

.code-body :deep(.hljs-addition) {
  color: #30d158;
}

.code-body :deep(.hljs-deletion) {
  color: #ff3b30;
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
  border-color: #646262;
}

[data-theme="light"] .code-block-header {
  background: #f1eeee;
  border-bottom-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .code-lang {
  color: #6e6e73;
}

[data-theme="light"] .code-body :deep(.hljs-keyword),
[data-theme="light"] .code-body :deep(.hljs-selector-tag) {
  color: #007aff;
}

[data-theme="light"] .code-body :deep(.hljs-string),
[data-theme="light"] .code-body :deep(.hljs-template-variable) {
  color: #30d158;
}

[data-theme="light"] .code-body :deep(.hljs-comment),
[data-theme="light"] .code-body :deep(.hljs-doctag) {
  color: #9a9898;
  font-style: italic;
}

[data-theme="light"] .code-body :deep(.hljs-type),
[data-theme="light"] .code-body :deep(.hljs-class .hljs-title),
[data-theme="light"] .code-body :deep(.hljs-title.class_) {
  color: #007aff;
}

[data-theme="light"] .code-body :deep(.hljs-number),
[data-theme="light"] .code-body :deep(.hljs-literal) {
  color: #ff9f0a;
}

[data-theme="light"] .code-body :deep(.hljs-function),
[data-theme="light"] .code-body :deep(.hljs-title.function_) {
  color: #007aff;
}

[data-theme="light"] .code-body :deep(.hljs-params) {
  color: #6e6e73;
}

[data-theme="light"] .code-body :deep(.hljs-built_in) {
  color: #30b0c7;
}

[data-theme="light"] .code-body :deep(.hljs-meta),
[data-theme="light"] .code-body :deep(.hljs-meta .hljs-keyword) {
  color: #30b0c7;
}

[data-theme="light"] .code-body :deep(.hljs-variable),
[data-theme="light"] .code-body :deep(.hljs-attr) {
  color: #5856d6;
}

[data-theme="light"] .code-body :deep(.hljs-symbol),
[data-theme="light"] .code-body :deep(.hljs-bullet) {
  color: #ff9f0a;
}

[data-theme="light"] .code-body :deep(.hljs-addition) {
  color: #30d158;
}

[data-theme="light"] .code-body :deep(.hljs-deletion) {
  color: #ff3b30;
}

[data-theme="light"] .copy-btn {
  background: transparent;
  color: #6e6e73;
  border-color: #646262;
}

[data-theme="light"] .copy-btn:hover {
  background: #f1eeee;
  border-color: #1c1919;
  color: #1c1919;
}

[data-theme="light"] .code-dots span:first-child { background: #ff3b30; }
[data-theme="light"] .code-dots span:nth-child(2) { background: #ff9f0a; }
[data-theme="light"] .code-dots span:last-child { background: #30d158; }

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
