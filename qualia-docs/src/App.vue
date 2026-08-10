<script setup lang="ts">
import { useRoute } from 'vue-router'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useTheme } from './composables/useTheme'

const route = useRoute()
const { isDark, toggleTheme } = useTheme()
const isDocPage = computed(() => route.path.startsWith('/docs'))
const isScrolled = ref(false)
const isMobileMenuOpen = ref(false)
const scrollY = ref(0)

const handleScroll = () => {
  scrollY.value = window.scrollY
  isScrolled.value = window.scrollY > 20
}

const toggleMobileMenu = () => {
  isMobileMenuOpen.value = !isMobileMenuOpen.value
  if (isMobileMenuOpen.value) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
}

const closeMobileMenu = () => {
  isMobileMenuOpen.value = false
  document.body.style.overflow = ''
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="app-layout">
    <!-- Top Navigation Bar -->
    <header
      class="top-navbar"
      :class="{
        scrolled: isScrolled,
        'mobile-open': isMobileMenuOpen
      }"
    >
      <div class="navbar-container">
        <!-- Brand -->
        <router-link to="/" class="navbar-brand" @click="closeMobileMenu">
          <div class="brand-icon-wrapper">
            <div class="brand-icon">
              <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
                <rect width="32" height="32" rx="8" fill="url(#brand-gradient)"/>
                <path d="M10 16L14 12L18 16L22 12" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M10 20L14 16L18 20L22 16" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" opacity="0.6"/>
                <defs>
                  <linearGradient id="brand-gradient" x1="0" y1="0" x2="32" y2="32">
                    <stop stop-color="#6366f1"/>
                    <stop offset="1" stop-color="#8b5cf6"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <div class="brand-glow"></div>
          </div>
          <div class="brand-text-group">
            <span class="brand-text">Qualia</span>
          </div>
        </router-link>

        <!-- Search Area -->
        <div class="search-area">
          <button class="search-btn" title="搜索 (⌘K)">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="M21 21l-4.35-4.35"/>
            </svg>
            <span class="search-shortcut">
              <kbd>⌘</kbd><kbd>K</kbd>
            </span>
          </button>
        </div>

        <!-- Desktop Navigation -->
        <nav class="navbar-nav">
          <router-link
            to="/"
            class="nav-link"
            :class="{ active: route.path === '/' }"
            @click="closeMobileMenu"
          >
            <span>首页</span>
          </router-link>
          <router-link
            to="/docs/qualia-docs"
            class="nav-link"
            :class="{ active: isDocPage }"
            @click="closeMobileMenu"
          >
            <span>文档</span>
          </router-link>
          <div class="nav-item-dropdown">
            <button
              class="nav-link dropdown-trigger"
              :class="{ active: route.path.startsWith('/product/qualia-code') }"
            >
              <span>产品</span>
              <svg class="dropdown-icon" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd"/>
              </svg>
            </button>
            <div class="dropdown-menu">
              <router-link to="/product/qualia-code" class="dropdown-item" @click="closeMobileMenu">
                <span class="dropdown-item-title">Qualia Code</span>
                <span class="dropdown-item-desc">Java Native Programming Assistant</span>
              </router-link>
            </div>
          </div>
          <a
            href="https://github.com/lunar-landing/qualia"
            target="_blank"
            rel="noopener noreferrer"
            class="nav-link"
            @click="closeMobileMenu"
          >
            <span>代码</span>
          </a>
        </nav>

        <!-- Right Actions -->
        <div class="navbar-actions">
          <!-- Theme Toggle -->
          <button
            class="theme-toggle-btn"
            @click="toggleTheme"
            :title="isDark ? '切换到日间模式' : '切换到夜间模式'"
            :aria-label="isDark ? '切换到日间模式' : '切换到夜间模式'"
          >
            <!-- Sun icon (shown in dark mode, click to switch to light) -->
            <svg v-if="isDark" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="5"/>
              <line x1="12" y1="1" x2="12" y2="3"/>
              <line x1="12" y1="21" x2="12" y2="23"/>
              <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
              <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
              <line x1="1" y1="12" x2="3" y2="12"/>
              <line x1="21" y1="12" x2="23" y2="12"/>
              <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
              <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
            <!-- Moon icon (shown in light mode, click to switch to dark) -->
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
            </svg>
          </button>


        </div>

        <!-- Mobile Menu Button -->
        <button class="mobile-menu-btn" @click="toggleMobileMenu" aria-label="Toggle menu">
          <div class="hamburger" :class="{ open: isMobileMenuOpen }">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </button>
      </div>

      <!-- Mobile Menu -->
      <Transition name="slide-down">
        <div v-if="isMobileMenuOpen" class="mobile-menu">
          <div class="mobile-menu-inner">
            <router-link
              to="/"
              class="mobile-link"
              :class="{ active: route.path === '/' }"
              @click="closeMobileMenu"
            >
              <svg class="mobile-link-icon" viewBox="0 0 20 20" fill="currentColor">
                <path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z"/>
              </svg>
              <span>首页</span>
            </router-link>
            <router-link
              to="/docs/qualia-docs"
              class="mobile-link"
              :class="{ active: isDocPage }"
              @click="closeMobileMenu"
            >
              <svg class="mobile-link-icon" viewBox="0 0 20 20" fill="currentColor">
                <path d="M9 4.804A7.968 7.968 0 005.5 4c-1.255 0-2.443.29-3.5.804v10A7.969 7.969 0 015.5 14c1.669 0 3.218.51 4.5 1.385A7.962 7.962 0 0114.5 14c1.255 0 2.443.29 3.5.804v-10A7.968 7.968 0 0014.5 4c-1.255 0-2.443.29-3.5.804V14a1 1 0 11-2 0V4.804z"/>
              </svg>
              <span>文档</span>
            </router-link>
            <router-link
              to="/product"
              class="mobile-link"
              :class="{ active: route.path === '/product' }"
              @click="closeMobileMenu"
            >
              <svg class="mobile-link-icon" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clip-rule="evenodd"/>
              </svg>
              <span>产品</span>
            </router-link>
            <a
              href="https://github.com/lunar-landing/qualia"
              target="_blank"
              rel="noopener noreferrer"
              class="mobile-link"
              @click="closeMobileMenu"
            >
              <svg class="mobile-link-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
              <span>代码</span>
            </a>
            <div class="mobile-divider"></div>
            <button class="mobile-link mobile-theme-toggle" @click="toggleTheme">
              <!-- Sun icon (shown in dark mode) -->
              <svg v-if="isDark" class="mobile-link-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="5"/>
                <line x1="12" y1="1" x2="12" y2="3"/>
                <line x1="12" y1="21" x2="12" y2="23"/>
                <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
                <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
                <line x1="1" y1="12" x2="3" y2="12"/>
                <line x1="21" y1="12" x2="23" y2="12"/>
                <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
                <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
              </svg>
              <!-- Moon icon (shown in light mode) -->
              <svg v-else class="mobile-link-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
              </svg>
              <span>{{ isDark ? '日间模式' : '夜间模式' }}</span>
            </button>

          </div>
        </div>
      </Transition>
    </header>

    <!-- Main Content -->
    <main class="main-content">
      <router-view v-slot="{ Component, route }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="route.path" />
        </Transition>
      </router-view>
    </main>

    <!-- Footer -->
    <footer class="footer">
      <div class="footer-container">
        <div class="footer-grid">
          <!-- Brand Column -->
          <div class="footer-brand">
            <div class="footer-logo">
              <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
                <rect width="32" height="32" rx="8" fill="url(#footer-gradient)"/>
                <path d="M10 16L14 12L18 16L22 12" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M10 20L14 16L18 20L22 16" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" opacity="0.6"/>
                <defs>
                  <linearGradient id="footer-gradient" x1="0" y1="0" x2="32" y2="32">
                    <stop stop-color="#6366f1"/>
                    <stop offset="1" stop-color="#8b5cf6"/>
                  </linearGradient>
                </defs>
              </svg>
              <span class="footer-brand-text">Qualia</span>
            </div>
            <p class="footer-desc">
              企业级 Java AI 智能体框架，助力构建下一代智能应用。
            </p>
            <div class="footer-social">
              <a href="https://github.com/lunar-landing/qualia" target="_blank" rel="noopener noreferrer" class="social-link" title="Github">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
              </a>
            </div>
          </div>

          <!-- Links Columns -->
          <div class="footer-links">
            <h4 class="footer-title">文档</h4>
            <ul class="footer-list">
              <li><a href="#">测试链接</a></li>
              <li><a href="#">测试链接</a></li>
              <li><a href="#">测试链接</a></li>
            </ul>
          </div>

          <div class="footer-links">
            <h4 class="footer-title">资源</h4>
            <ul class="footer-list">
              <li><a href="#">测试链接</a></li>
              <li><a href="#">测试链接</a></li>
              <li><a href="#">测试链接</a></li>
            </ul>
          </div>

          <div class="footer-links">
            <h4 class="footer-title">社区</h4>
            <ul class="footer-list">
              <li><a href="#">测试链接</a></li>
              <li><a href="#">测试链接</a></li>
              <li><a href="#">测试链接</a></li>
            </ul>
          </div>
        </div>

        <!-- Footer Bottom -->
        <div class="footer-bottom">
          <p class="copyright">
            © {{ new Date().getFullYear() }} Qualia. 基于 MIT 许可证开源。
          </p>
          <div class="footer-meta">
            <span class="footer-badge">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
              稳定版本
            </span>
          </div>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ============================================
   Top Navigation Bar
   ============================================ */
.top-navbar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  background: #201d1d;
  border-bottom: 1px solid rgba(15, 0, 0, 0.12);
  transition: all var(--transition-slow);
}

.top-navbar.scrolled {
  background: #201d1d;
  border-bottom-color: rgba(15, 0, 0, 0.12);
}

.top-navbar.mobile-open {
  background: #201d1d;
}

.navbar-container {
  width: 100%;
  padding: 0 var(--spacing-6);
  height: 72px;
  display: flex;
  align-items: center;
  gap: var(--spacing-6);
}

/* Brand */
.navbar-brand {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  text-decoration: none;
  color: #ffffff;
  flex-shrink: 0;
  position: relative;
}

.brand-icon-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.brand-icon {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  transition: all var(--transition-base);
  transform-origin: center;
}

.navbar-brand:hover .brand-icon {
  transform: scale(1.1) rotate(-3deg);
}

.brand-glow {
  display: none;
}

.brand-text-group {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.brand-text {
  font-size: var(--text-xl);
  font-weight: var(--font-bold);
  color: #fdfcfc;
  font-family: var(--font-sans);
}

.brand-badge {
  padding: var(--spacing-0-5) var(--spacing-2);
  background: rgba(99, 102, 241, 0.15);
  color: var(--color-primary-light);
  font-size: var(--text-xs);
  font-weight: var(--font-semibold);
  border-radius: var(--radius-full);
  letter-spacing: var(--tracking-wide);
  border: 1px solid rgba(99, 102, 241, 0.25);
}

/* Navigation Links */
.navbar-nav {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.nav-link {
  display: flex;
  align-items: center;
  gap: var(--spacing-2-5);
  padding: var(--spacing-2-5) var(--spacing-5);
  color: var(--color-gray-500);
  text-decoration: underline;
  text-underline-offset: 4px;
  font-size: var(--text-base);
  font-weight: var(--font-medium);
  border-radius: 4px;
  transition: all var(--transition-base);
  position: relative;
  letter-spacing: var(--tracking-wide);
  overflow: hidden;
}



.nav-link:hover {
  color: #fdfcfc;
  background: rgba(253, 252, 252, 0.06);
}



.nav-link.active {
  color: #fdfcfc;
  font-weight: var(--font-bold);
}

/* Dropdown Menu */
.nav-item-dropdown {
  position: relative;
}

.nav-item-dropdown .nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dropdown-trigger {
  background: none;
  border: none;
  cursor: pointer;
  font-family: inherit;
  font-size: inherit;
}

.dropdown-icon {
  width: 16px;
  height: 16px;
  transition: transform 200ms ease;
  flex-shrink: 0;
}

.nav-item-dropdown:hover .dropdown-icon {
  transform: rotate(180deg);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%) translateY(8px);
  min-width: 220px;
  background: #302c2c;
  border: 1px solid rgba(100, 98, 98, 0.3);
  border-radius: 8px;
  padding: 8px;
  opacity: 0;
  visibility: hidden;
  transition: all 200ms ease;
  z-index: 1000;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
}

.nav-item-dropdown:hover .dropdown-menu {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(0);
}

.dropdown-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  text-decoration: none;
  border-radius: 6px;
  transition: all 200ms ease;
}

.dropdown-item:hover {
  background: rgba(253, 252, 252, 0.06);
}

.dropdown-item-title {
  font-size: 14px;
  font-weight: 600;
  color: #fdfcfc;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

.dropdown-item-desc {
  font-size: 12px;
  color: #9a9898;
  font-family: "Berkeley Mono", "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", Courier New, monospace;
}

/* Right Actions */
.navbar-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

/* Theme Toggle Button */
.theme-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--color-gray-500);
  border-radius: 4px;
  transition: all var(--transition-base);
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(15, 0, 0, 0.12);
}

.theme-toggle-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(99, 102, 241, 0.1);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.theme-toggle-btn:hover {
  color: #fdfcfc;
  border-color: rgba(15, 0, 0, 0.12);
}

.theme-toggle-btn:hover::before {
  opacity: 1;
}

.theme-toggle-btn svg {
  position: relative;
  z-index: 1;
  transition: transform var(--transition-base);
}

.theme-toggle-btn:hover svg {
  transform: rotate(30deg);
}





/* Search Area */
.search-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
  margin-left: auto;
}

.search-btn {
  display: flex;
  align-items: center;
  gap: var(--spacing-2-5);
  padding: var(--spacing-2-5) var(--spacing-5);
  background: rgba(253, 252, 252, 0.04);
  border: 1px solid rgba(15, 0, 0, 0.12);
  border-radius: 4px;
  color: var(--color-gray-500);
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  transition: all var(--transition-base);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 280px;
}

.search-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(99, 102, 241, 0.08);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.search-btn:hover {
  color: #fdfcfc;
  border-color: rgba(15, 0, 0, 0.12);
  background: rgba(253, 252, 252, 0.06);
}

.search-btn:hover::before {
  opacity: 1;
}

.search-btn svg,
.search-shortcut {
  position: relative;
  z-index: 1;
}

.search-shortcut {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
}

.search-shortcut kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 var(--spacing-1-5);
  background: rgba(253, 252, 252, 0.08);
  border: 1px solid rgba(15, 0, 0, 0.12);
  border-radius: 4px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: var(--font-semibold);
  color: var(--color-gray-500);
}

/* Mobile Menu Button */
.mobile-menu-btn {
  display: none;
  padding: var(--spacing-2);
  color: var(--color-gray-500);
  z-index: 1001;
}

.hamburger {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 24px;
}

.hamburger span {
  display: block;
  height: 2px;
  background: currentColor;
  border-radius: 1px;
  transition: all var(--transition-base);
  transform-origin: center;
}

.hamburger.open span:nth-child(1) {
  transform: translateY(8px) rotate(45deg);
}

.hamburger.open span:nth-child(2) {
  opacity: 0;
  transform: scaleX(0);
}

.hamburger.open span:nth-child(3) {
  transform: translateY(-8px) rotate(-45deg);
}

/* Mobile Menu */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all var(--transition-slow);
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.mobile-menu {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: #201d1d;
  border-bottom: 1px solid rgba(15, 0, 0, 0.12);
}

.mobile-menu-inner {
  padding: var(--spacing-4) var(--spacing-6);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-1);
}

.mobile-link {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  color: var(--color-gray-500);
  text-decoration: underline;
  text-underline-offset: 4px;
  font-weight: var(--font-medium);
  border-radius: 4px;
  transition: all var(--transition-fast);
}

.mobile-link:hover {
  background: rgba(253, 252, 252, 0.06);
  color: #fdfcfc;
}

.mobile-link.active {
  background: rgba(253, 252, 252, 0.08);
  color: #fdfcfc;
}

.mobile-link-icon {
  width: 20px;
  height: 20px;
}

.mobile-divider {
  height: 1px;
  background: rgba(15, 0, 0, 0.12);
  margin: var(--spacing-2) 0;
}

/* Mobile Theme Toggle */
.mobile-theme-toggle {
  cursor: pointer;
  width: 100%;
  text-align: left;
}

/* ============================================
   Main Content
   ============================================ */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* Page Transition */
.page-enter-active {
  transition: all var(--transition-slow);
}

.page-leave-active {
  transition: all var(--transition-fast);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}

/* ============================================
   Footer
   ============================================ */
.footer {
  background: #201d1d;
  color: var(--color-gray-500);
  padding: var(--spacing-16) 0 var(--spacing-8);
  margin-top: auto;
  position: relative;
  overflow: hidden;
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.footer-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 var(--spacing-6);
}

.footer-grid {
  display: grid;
  grid-template-columns: 1.5fr repeat(3, 1fr);
  gap: var(--spacing-12);
  margin-bottom: var(--spacing-12);
}

/* Footer Brand */
.footer-brand {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.footer-brand-text {
  font-size: var(--text-xl);
  font-weight: var(--font-bold);
  color: #ffffff;
}

.footer-desc {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-gray-400);
  max-width: 300px;
}

.footer-social {
  display: flex;
  gap: var(--spacing-3);
  margin-top: var(--spacing-2);
}

.social-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--color-gray-500);
  background: rgba(253, 252, 252, 0.04);
  border-radius: 4px;
  transition: all var(--transition-fast);
  border: 1px solid rgba(15, 0, 0, 0.12);
}

.social-link:hover {
  color: #fdfcfc;
  background: rgba(253, 252, 252, 0.06);
  border-color: rgba(15, 0, 0, 0.12);
}

/* Footer Links */
.footer-links {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
}

.footer-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: #ffffff;
  text-transform: uppercase;
  letter-spacing: var(--tracking-wider);
}

.footer-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

.footer-list a {
  color: var(--color-gray-500);
  text-decoration: none;
  font-size: var(--text-sm);
  transition: all var(--transition-fast);
  display: inline-flex;
  align-items: center;
}

.footer-list a:hover {
  color: #fdfcfc;
}

/* Footer Bottom */
.footer-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--spacing-8);
  border-top: 1px solid rgba(15, 0, 0, 0.12);
}

.copyright {
  font-size: var(--text-sm);
  color: var(--color-gray-500);
}

.footer-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
}

.footer-badge {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-1) var(--spacing-3);
  background: rgba(48, 209, 88, 0.08);
  color: var(--color-accent-emerald);
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  border-radius: 4px;
  border: 1px solid rgba(48, 209, 88, 0.12);
}

/* ============================================
   Responsive Design
   ============================================ */
@media (max-width: 1024px) {
  .search-btn {
    width: 220px;
  }
}

@media (max-width: 768px) {
  .navbar-container {
    height: 68px;
    padding: 0 var(--spacing-4);
    gap: var(--spacing-4);
  }

  .navbar-nav {
    display: none;
  }

  .search-area {
    display: none;
  }

  .navbar-actions {
    display: none;
  }

  .mobile-menu-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: auto;
    width: 44px;
    height: 44px;
    border-radius: var(--radius-xl);
    transition: all var(--transition-fast);
  }

  .mobile-menu-btn:hover {
    background: rgba(253, 252, 252, 0.06);
  }

  .footer-grid {
    grid-template-columns: 1fr;
    gap: var(--spacing-8);
  }

  .footer-bottom {
    flex-direction: column;
    gap: var(--spacing-4);
    text-align: center;
  }
}

@media (max-width: 480px) {
  .navbar-container {
    height: 64px;
    padding: 0 var(--spacing-3);
  }

  .brand-text-group {
    gap: var(--spacing-1);
  }

  .brand-badge {
    display: none;
  }

  .mobile-menu-btn {
    width: 40px;
    height: 40px;
  }
}

/* ============================================
   Light Theme Overrides
   ============================================ */
/* Navbar */
[data-theme="light"] .top-navbar {
  background: #f8f7f7;
  border-bottom-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .top-navbar.scrolled {
  background: #f8f7f7;
  border-bottom-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .top-navbar.mobile-open {
  background: #f8f7f7;
}

/* Brand */
[data-theme="light"] .navbar-brand {
  color: #1e293b;
}

[data-theme="light"] .brand-text {
  color: #201d1d;
}

[data-theme="light"] .brand-badge {
  background: rgba(79, 70, 229, 0.08);
  color: var(--color-primary);
  border-color: rgba(79, 70, 229, 0.15);
}

/* Nav Links */
[data-theme="light"] .nav-link {
  color: #6e6e73;
}

[data-theme="light"] .nav-link:hover {
  color: #201d1d;
  background: rgba(32, 29, 29, 0.06);
}

[data-theme="light"] .nav-link.active {
  color: #201d1d;
  font-weight: var(--font-bold);
}

[data-theme="light"] .dropdown-menu {
  background: #ffffff;
  border-color: rgba(15, 0, 0, 0.12);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
}

[data-theme="light"] .dropdown-item:hover {
  background: rgba(32, 29, 29, 0.06);
}

[data-theme="light"] .dropdown-item-title {
  color: #201d1d;
}

[data-theme="light"] .dropdown-item-desc {
  color: #6e6e73;
}

/* Theme Toggle - Light */
[data-theme="light"] .theme-toggle-btn {
  color: #6e6e73;
  border-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .theme-toggle-btn::before {
  background: rgba(79, 70, 229, 0.06);
}

[data-theme="light"] .theme-toggle-btn:hover {
  color: #201d1d;
  border-color: rgba(15, 0, 0, 0.12);
}



/* Search - Light */
[data-theme="light"] .search-btn {
  background: rgba(32, 29, 29, 0.03);
  border-color: rgba(15, 0, 0, 0.12);
  color: #6e6e73;
}

[data-theme="light"] .search-btn::before {
  background: rgba(79, 70, 229, 0.06);
}

[data-theme="light"] .search-btn:hover {
  color: #201d1d;
  border-color: rgba(15, 0, 0, 0.12);
  background: rgba(32, 29, 29, 0.04);
}

[data-theme="light"] .search-shortcut kbd {
  background: rgba(32, 29, 29, 0.05);
  border-color: rgba(15, 0, 0, 0.12);
  color: #6e6e73;
}

/* Mobile Menu - Light */
[data-theme="light"] .mobile-menu {
  background: #f8f7f7;
  border-bottom-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .mobile-link {
  color: #6e6e73;
}

[data-theme="light"] .mobile-link:hover {
  background: rgba(32, 29, 29, 0.06);
  color: #201d1d;
}

[data-theme="light"] .mobile-link.active {
  background: rgba(32, 29, 29, 0.08);
  color: #201d1d;
}

[data-theme="light"] .mobile-divider {
  background: rgba(15, 0, 0, 0.12);
}

/* Footer - Light */
[data-theme="light"] .footer {
  background: #f8f7f7;
  border-top-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .footer-brand-text {
  color: #201d1d;
}

[data-theme="light"] .footer-title {
  color: #201d1d;
}

[data-theme="light"] .footer-list a {
  color: #6e6e73;
}

[data-theme="light"] .footer-list a:hover {
  color: #201d1d;
}

[data-theme="light"] .footer-bottom {
  border-top-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .social-link {
  color: #6e6e73;
  background: rgba(32, 29, 29, 0.03);
  border-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .social-link:hover {
  color: #201d1d;
  background: rgba(32, 29, 29, 0.06);
  border-color: rgba(15, 0, 0, 0.12);
}

[data-theme="light"] .footer-badge {
  background: rgba(48, 209, 88, 0.06);
  color: var(--color-accent-emerald);
  border-color: rgba(48, 209, 88, 0.12);
}

[data-theme="light"] .mobile-menu-btn:hover {
  background: rgba(32, 29, 29, 0.06);
}
</style>
