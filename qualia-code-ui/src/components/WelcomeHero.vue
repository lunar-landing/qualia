<template>
  <!-- 空会话欢迎 Hero（对齐旧 showWelcome）：输入框经 ChatArea 的 Teleport 移入下方锚点 -->
  <div class="welcome-hero">
    <div class="hero-head">
      <div class="hero-logo">
        <!-- AI 品牌符号：四角星火花（单智能体无登录态，消息不展示头像） -->
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2c1 6 4 9 10 10-6 1-9 4-10 10-1-6-4-9-10-10 6-1 9-4 10-10z" />
        </svg>
      </div>
      <div class="hero-title">Qualia Code</div>
    </div>
    <div class="hero-sub">{{ $t('welcome.sub') }}</div>
    <!-- 输入框挂载锚点（ChatArea 以 querySelector 定位后 Teleport） -->
    <div class="hero-input-anchor"></div>
  </div>
</template>
