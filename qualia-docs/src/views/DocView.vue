<script setup lang="ts">
import { ref, onMounted, watch, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useMarkdown } from '../composables/useMarkdown'

const route = useRoute()
const { parseMarkdown, extractHeadings } = useMarkdown()

const content = ref('')
const title = ref('')
const description = ref('')
const loading = ref(true)
const error = ref('')
const headings = ref<Array<{ level: number; text: string; id: string }>>([])
const activeHeading = ref('')
const isSidebarOpen = ref(false)
const isVisible = ref(false)

// Document navigation groups
interface DocItem {
  title: string
  description: string
  path: string
  file: string
  icon: string
  badge?: string
}

interface DocGroup {
  title: string
  items: DocItem[]
}

const docGroups: DocGroup[] = [
  {
    title: '入门指南',
    items: [
      { 
        title: '快速入门', 
        description: 'Qualia 框架架构与技术文档',
        path: '/docs/qualia-docs', 
        file: 'DOCS.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path d="M10.75 2.75a.75.75 0 00-1.5 0v8.614L6.295 8.235a.75.75 0 10-1.09 1.03l4.25 4.5a.75.75 0 001.09 0l4.25-4.5a.75.75 0 00-1.09-1.03l-2.955 3.129V2.75z"/><path d="M3.5 12.75a.75.75 0 00-1.5 0v2.5A2.75 2.75 0 004.75 18h10.5A2.75 2.75 0 0018 15.25v-2.5a.75.75 0 00-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5z"/></svg>`,
        badge: '入门'
      },
      { 
        title: '更新日志', 
        description: '版本变更与更新记录',
        path: '/docs/changelog', 
        file: 'CHANGELOG.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path d="M10 2a6 6 0 00-6 6v3.586l-.707.707A1 1 0 004 14h12a1 1 0 00.707-1.707L16 11.586V8a6 6 0 00-6-6zM10 18a3 3 0 01-3-3h6a3 3 0 01-3 3z"/></svg>`
      }
    ]
  },
  {
    title: '核心功能',
    items: [
      { 
        title: '智能代理', 
        description: 'ReAct 模式与 Agent 执行引擎',
        path: '/docs/react-agent', 
        file: 'react-agent.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4.25 2A2.25 2.25 0 002 4.25v11.5A2.25 2.25 0 004.25 18h11.5A2.25 2.25 0 0018 15.75V4.25A2.25 2.25 0 0015.75 2H4.25zm4.03 6.28a.75.75 0 00-1.06-1.06L4.97 9.47a.75.75 0 000 1.06l2.25 2.25a.75.75 0 001.06-1.06L6.56 10l1.72-1.72zm4.5-1.06a.75.75 0 10-1.06 1.06L13.44 10l-1.72 1.72a.75.75 0 101.06 1.06l2.25-2.25a.75.75 0 000-1.06l-2.25-2.25z" clip-rule="evenodd"/></svg>`
      },
      { 
        title: '模型服务', 
        description: '多模型适配与统一接口',
        path: '/docs/multi-model', 
        file: 'multi-model.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M2 10a8 8 0 1116 0 8 8 0 01-16 0zm8-6a.75.75 0 01.75.75v4.5h4.5a.75.75 0 010 1.5h-4.5v4.5a.75.75 0 01-1.5 0v-4.5h-4.5a.75.75 0 010-1.5h4.5v-4.5A.75.75 0 0110 4z" clip-rule="evenodd"/></svg>`
      },
      { 
        title: '对话记忆', 
        description: '会话历史与上下文管理',
        path: '/docs/memory-management', 
        file: 'memory-management.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.5 3A2.5 2.5 0 003 5.5v2.879a2.5 2.5 0 00.732 1.767l7.5 7.5a2.5 2.5 0 003.536 0l2.878-2.878a2.5 2.5 0 000-3.536l-7.5-7.5A2.5 2.5 0 007.88 3H5.5zM6 7a1 1 0 100-2 1 1 0 000 2z"/></svg>`
      },
      { 
        title: '记忆压缩', 
        description: '上下文摘要压缩与优化',
        path: '/docs/context-compression', 
        file: 'context-compression.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 2a2 2 0 00-2 2v11a3 3 0 106 0V4a2 2 0 00-2-2H4zm1 14a1 1 0 100-2 1 1 0 000 2zm5-1.757l4.9-4.9a2 2 0 000-2.828L13.485 5.1a2 2 0 00-2.828 0L10 5.757v8.486zM16 18H9.071l6-6H16a2 2 0 012 2v2a2 2 0 01-2 2z" clip-rule="evenodd"/></svg>`
      },
      { 
        title: '工具系统', 
        description: 'FunctionTool 与参数声明',
        path: '/docs/function-tool', 
        file: 'function-tool.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M7.84 1.804A1 1 0 018.82 1h2.36a1 1 0 01.98.804l.331 1.652a6.993 6.993 0 011.929 1.115l1.598-.54a1 1 0 011.186.447l1.18 2.044a1 1 0 01-.205 1.251l-1.267 1.113a7.047 7.047 0 010 2.228l1.267 1.113a1 1 0 01.206 1.25l-1.18 2.045a1 1 0 01-1.187.447l-1.598-.54a6.993 6.993 0 01-1.929 1.115l-.33 1.652a1 1 0 01-.98.804H8.82a1 1 0 01-.98-.804l-.331-1.652a6.993 6.993 0 01-1.929-1.115l-1.598.54a1 1 0 01-1.186-.447l-1.18-2.044a1 1 0 01.205-1.251l1.267-1.114a7.05 7.05 0 010-2.227L1.821 7.773a1 1 0 01-.206-1.25l1.18-2.045a1 1 0 011.187-.447l1.598.54A6.993 6.993 0 017.51 3.456l.33-1.652zM10 13a3 3 0 100-6 3 3 0 000 6z" clip-rule="evenodd"/></svg>`
      },
      { 
        title: '技能管理', 
        description: 'Skill 编排与配置加载',
        path: '/docs/skill', 
        file: 'skill.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path d="M11.986 3H12a2 2 0 00-1.732 1l-.866.5A2 2 0 017.87 5.5l-.866.5A2 2 0 006 7.5V8a2 2 0 002 2h.5a2 2 0 002-2v-.5a.5.5 0 01.5-.5H12a2 2 0 002-2V4a2 2 0 00-2.014-1zM3.793 9.293a1 1 0 00-1.414 1.414l1.414-1.414zM5.5 12l-1.414 1.414a1 1 0 001.414 1.414L5.5 12zm3 3l-1.414 1.414a1 1 0 001.414 1.414L8.5 15zm3 3l-1.414 1.414a1 1 0 001.414 1.414L11.5 18zm3-3l1.414 1.414a1 1 0 01-1.414 1.414L14.5 15zm3-3l1.414 1.414a1 1 0 01-1.414 1.414L17.5 12zm-3-3l1.414-1.414a1 1 0 00-1.414-1.414L14.5 9z"/></svg>`
      },

      { 
        title: '多智能体', 
        description: '多智能体协作与任务分解', 
        path: '/docs/multi-agent', 
        file: 'multi-agent.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path d="M10 9a3 3 0 100-6 3 3 0 000 6zM6 8a2 2 0 11-4 0 2 2 0 014 0zM1.49 15.326a.78.78 0 01-.358-.442 3 3 0 013.005-4.39 3 3 0 014.39-3.005.78.78 0 01.826.47 1.96 1.96 0 002.456.884 1.96 1.96 0 012.214.974 1.96 1.96 0 00.884 2.456.78.78 0 01.47.826 3 3 0 01-4.39 3.005 3 3 0 01-3.005-4.39.78.78 0 01.442-.358z"/></svg>` 
      }
    ]
  },
  {
    title: 'MCP',
    items: [
      { 
        title: 'MCP 客户端', 
        description: '连接远程 MCP 服务器与工具调用', 
        path: '/docs/mcp-client', 
        file: 'mcp-client.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M2 10a8 8 0 1116 0 8 8 0 01-16 0zm8-6a.75.75 0 01.75.75v4.5h4.5a.75.75 0 010 1.5h-4.5v4.5a.75.75 0 01-1.5 0v-4.5h-4.5a.75.75 0 010-1.5h4.5v-4.5A.75.75 0 0110 4z" clip-rule="evenodd"/></svg>` 
      },
      { 
        title: 'MCP 服务端', 
        description: '暴露本地工具为 MCP 服务', 
        path: '/docs/mcp-server', 
        file: 'mcp-server.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M2 4.25A2.25 2.25 0 014.25 2h11.5A2.25 2.25 0 0118 4.25v8.5A2.25 2.25 0 0115.75 15h-3.105a3.001 3.001 0 001.663-.996l.544-.544a.75.75 0 00-1.06-1.06l-.545.544A1.5 1.5 0 0110.645 13.5H9.354a1.5 1.5 0 01-1.473-1.118l-.545-.544a.75.75 0 10-1.06 1.06l.544.544A3.001 3.001 0 008.479 15H4.25A2.25 2.25 0 012 12.75v-8.5zm1.5 0a.75.75 0 01.75-.75h11.5a.75.75 0 01.75.75v8.5a.75.75 0 01-.75.75H4.25a.75.75 0 01-.75-.75v-8.5z" clip-rule="evenodd"/></svg>` 
      }
    ]
  },
  {
    title: '文件工具',
    items: [
      { 
        title: 'Read', 
        description: '读取文件内容，支持指定行范围', 
        path: '/docs/read-tool', 
        file: 'read-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/></svg>`, 
        badge: '文件' 
      },
      { 
        title: 'Write', 
        description: '写入文件内容，支持覆盖、追加和插入', 
        path: '/docs/write-tool', 
        file: 'write-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/></svg>`, 
        badge: '文件' 
      },
      { 
        title: 'Edit', 
        description: '基于文本匹配替换文件内容', 
        path: '/docs/edit-tool', 
        file: 'edit-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/></svg>`, 
        badge: '文件' 
      },
      { 
        title: 'Delete', 
        description: '删除工作区内的文件，变更可追溯', 
        path: '/docs/delete-tool', 
        file: 'delete-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/></svg>`, 
        badge: '文件' 
      },
      { 
        title: 'Bash', 
        description: '执行系统命令，包括 git、npm 等', 
        path: '/docs/bash-tool', 
        file: 'bash-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/></svg>`, 
        badge: '文件' 
      },
      { 
        title: 'Grep', 
        description: '搜索文件内容，支持正则表达式', 
        path: '/docs/grep-tool', 
        file: 'grep-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/></svg>`, 
        badge: '文件' 
      },
      { 
        title: 'Glob', 
        description: '搜索文件路径，支持 glob 模式', 
        path: '/docs/glob-tool', 
        file: 'glob-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/></svg>`, 
        badge: '文件' 
      }
    ]
  },
  {
    title: '网络工具',
    items: [
      { 
        title: 'WebFetch', 
        description: '网页内容抓取工具，获取在线文档和网页内容', 
        path: '/docs/web-fetch', 
        file: 'web-fetch.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4.25 2A2.25 2.25 0 002 4.25v11.5A2.25 2.25 0 004.25 18h11.5A2.25 2.25 0 0018 15.75V4.25A2.25 2.25 0 0015.75 2H4.25zm4.03 6.28a.75.75 0 00-1.06-1.06L4.97 9.47a.75.75 0 000 1.06l2.25 2.25a.75.75 0 001.06-1.06L6.56 10l1.72-1.72zm4.5-1.06a.75.75 0 10-1.06 1.06L13.44 10l-1.72 1.72a.75.75 0 101.06 1.06l2.25-2.25a.75.75 0 000-1.06l-2.25-2.25z" clip-rule="evenodd"/></svg>`, 
        badge: '网络' 
      },
      { 
        title: 'Http', 
        description: '通用 HTTP 请求工具', 
        path: '/docs/http-tool', 
        file: 'http-tool.md', 
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4.25 2A2.25 2.25 0 002 4.25v2.5A2.25 2.25 0 004.25 9h2.5A2.25 2.25 0 009 6.75v-2.5A2.25 2.25 0 006.75 2h-2.5zm0 9A2.25 2.25 0 002 13.25v2.5A2.25 2.25 0 004.25 18h2.5A2.25 2.25 0 009 15.75v-2.5A2.25 2.25 0 006.75 11h-2.5zm9-9A2.25 2.25 0 0011 4.25v2.5A2.25 2.25 0 0013.25 9h2.5A2.25 2.25 0 0018 6.75v-2.5A2.25 2.25 0 0015.75 2h-2.5zm0 9A2.25 2.25 0 0011 13.25v2.5A2.25 2.25 0 0013.25 18h2.5A2.25 2.25 0 0018 15.75v-2.5A2.25 2.25 0 0015.75 11h-2.5z" clip-rule="evenodd"/></svg>`, 
        badge: '网络' 
      }
    ]
  },
  {
    title: '搜索工具',
    items: [
      { 
        title: 'BaiduSearch', 
        description: '基于百度搜索引擎的网络搜索工具',
        path: '/docs/baidu-search', 
        file: 'baidu-search.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clip-rule="evenodd"/></svg>`,
        badge: '搜索'
      },
      { 
        title: 'BingSearch', 
        description: '基于必应搜索引擎的网络搜索工具',
        path: '/docs/bing-search', 
        file: 'bing-search.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clip-rule="evenodd"/></svg>`,
        badge: '搜索'
      },
      { 
        title: 'GoogleSearch', 
        description: '基于 Google 搜索引擎的网络搜索工具',
        path: '/docs/google-search', 
        file: 'google-search.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clip-rule="evenodd"/></svg>`,
        badge: '搜索'
      },
      { 
        title: 'DuckSearch', 
        description: '基于 DuckDuckGo 搜索引擎的网络搜索工具',
        path: '/docs/duckduckgo-search', 
        file: 'duckduckgo-search.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clip-rule="evenodd"/></svg>`,
        badge: '搜索'
      },
      { 
        title: 'TavilySearch', 
        description: '基于 Tavily API 的网络搜索工具',
        path: '/docs/tavily-search', 
        file: 'tavily-search.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clip-rule="evenodd"/></svg>`,
        badge: '搜索'
      }
    ]
  },
  {
    title: '资料检索',
    items: [
      { 
        title: 'Ragflow', 
        description: '基于 RAGFlow API 的知识库检索工具',
        path: '/docs/ragflow-tool', 
        file: 'ragflow-tool.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path d="M10.75 2.75a.75.75 0 00-1.5 0v8.614L6.295 8.235a.75.75 0 10-1.09 1.03l4.25 4.5a.75.75 0 001.09 0l4.25-4.5a.75.75 0 00-1.09-1.03l-2.955 3.129V2.75z"/><path d="M3.5 12.75a.75.75 0 00-1.5 0v2.5A2.75 2.75 0 004.75 18h10.5A2.75 2.75 0 0018 15.25v-2.5a.75.75 0 00-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5z"/></svg>`,
        badge: '知识'
      }
    ]
  },
  {
    title: '更多内容',
    items: [
      { 
        title: '常见问题', 
        description: '使用指南与常见疑问',
        path: '/docs/faq', 
        file: 'FAQ.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM8.94 6.94a.75.75 0 11-1.061-1.061 3 3 0 112.871 5.026v.345a.75.75 0 01-1.5 0v-.5c0-.72.57-1.172 1.081-1.287A1.5 1.5 0 108.94 6.94zM10 15a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd"/></svg>`
      },
      { 
        title: '发展规划', 
        description: '项目改进与迭代计划',
        path: '/docs/roadmap', 
        file: 'ROAD_MAP.md',
        icon: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm.75-11.25a.75.75 0 00-1.5 0v2.5h-2.5a.75.75 0 000 1.5h2.5v2.5a.75.75 0 001.5 0v-2.5h2.5a.75.75 0 000-1.5h-2.5v-2.5z" clip-rule="evenodd"/></svg>`
      },
    ]
  }
]

// Flatten all docs for prev/next navigation
const docNav = docGroups.flatMap(group => group.items)

const currentIndex = computed(() => docNav.findIndex(d => d.path === route.path || d.path === `/docs/${route.params.docId}`))
const prevDoc = computed(() => currentIndex.value > 0 ? docNav[currentIndex.value - 1] : null)
const nextDoc = computed(() => currentIndex.value < docNav.length - 1 ? docNav[currentIndex.value + 1] : null)

const loadDocument = async (docPath: string) => {
  loading.value = true
  error.value = ''
  headings.value = []
  
  try {
    const docItem = docNav.find(d => d.path === docPath)
    const mdFile = docItem?.file || 'DOCS.md'
    
    const base = import.meta.env.BASE_URL || '/'
    const response = await fetch(`${base}docs/${mdFile}`)
    
    if (!response.ok) {
      throw new Error(`无法加载文档: ${response.status}`)
    }
    
    const mdContent = await response.text()
    
    const parsed = parseMarkdown(mdContent)
    content.value = parsed.html
    // 优先使用菜单配置的 title 和 description
    title.value = docItem?.title || parsed.title || ''
    description.value = docItem?.description || ''
    
    headings.value = extractHeadings(mdContent)
    
    // Scroll to top
    window.scrollTo({ top: 0, behavior: 'smooth' })
    
    // Reset visibility for animation
    isVisible.value = false
    setTimeout(() => {
      isVisible.value = true
    }, 50)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载文档失败'
    console.error('加载文档失败:', err)
  } finally {
    loading.value = false
  }
}

const scrollToHeading = (id: string) => {
  const element = document.getElementById(id)
  if (element) {
    const offset = 80 // Account for sticky header
    const elementPosition = element.getBoundingClientRect().top
    const offsetPosition = elementPosition + window.pageYOffset - offset
    
    window.scrollTo({
      top: offsetPosition,
      behavior: 'smooth'
    })
    
    activeHeading.value = id
    isSidebarOpen.value = false
  }
}

// Watch for route changes
watch(
  () => route.path,
  (newPath) => {
    if (newPath.startsWith('/docs/')) {
      loadDocument(newPath)
    }
  },
  { immediate: true }
)

onMounted(() => {
  setTimeout(() => {
    isVisible.value = true
  }, 100)
  
  if (route.path.startsWith('/docs/')) {
    loadDocument(route.path)
  }
})
</script>

<template>
  <div class="doc-layout" :class="{ visible: isVisible }">
    <!-- Mobile Sidebar Toggle -->
    <button class="sidebar-toggle" @click="isSidebarOpen = !isSidebarOpen">
      <svg viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M2 4.75A.75.75 0 012.75 4h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 4.75zM2 10a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 10zm0 5.25a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75a.75.75 0 01-.75-.75z" clip-rule="evenodd"/>
      </svg>
      <span>目录</span>
    </button>

    <!-- Sidebar Overlay -->
    <Transition name="fade">
      <div 
        v-if="isSidebarOpen" 
        class="sidebar-overlay"
        @click="isSidebarOpen = false"
      ></div>
    </Transition>

    <!-- Sidebar -->
    <aside class="doc-sidebar" :class="{ open: isSidebarOpen }">
      <div class="sidebar-inner">

        
        <!-- Search Box -->
        <div class="sidebar-search">
          <svg viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clip-rule="evenodd"/>
          </svg>
          <input type="text" placeholder="搜索文档..." disabled>
        </div>

        <!-- Document Navigation -->
        <div class="sidebar-section">
          <h3 class="sidebar-title">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path d="M3 3.5A1.5 1.5 0 014.5 2h6.879a1.5 1.5 0 011.06.44l4.122 4.12A1.5 1.5 0 0117 7.622V16.5a1.5 1.5 0 01-1.5 1.5h-11A1.5 1.5 0 013 16.5v-13z"/>
            </svg>
            文档导航
          </h3>
          <div v-for="group in docGroups" :key="group.title" class="sidebar-group">
            <div class="group-title">{{ group.title }}</div>
            <nav class="sidebar-nav">
              <router-link 
                v-for="doc in group.items" 
                :key="doc.path"
                :to="doc.path" 
                class="sidebar-link"
                :class="{ active: route.path === doc.path }"
                @click="isSidebarOpen = false"
              >
                <span class="link-icon" v-html="doc.icon"></span>
                <span class="link-text">{{ doc.title }}</span>
                <span v-if="doc.badge" class="link-badge">{{ doc.badge }}</span>
              </router-link>
            </nav>
          </div>
        </div>
      </div>
    </aside>

    <!-- Document Content -->
    <div class="doc-wrapper">
      <div class="doc-main">
        <!-- Loading State -->
        <div v-if="loading" class="doc-state">
        <div class="loading-spinner">
          <div class="spinner"></div>
        </div>
        <p class="state-text">正在加载文档...</p>
      </div>
      
      <!-- Error State -->
      <div v-else-if="error" class="doc-state">
        <div class="error-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"/>
          </svg>
        </div>
        <h3 class="state-title">加载失败</h3>
        <p class="state-text">{{ error }}</p>
        <button @click="loadDocument(route.path)" class="retry-btn">
          <svg viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M15.312 11.424a5.5 5.5 0 01-9.201 2.466l-.312-.311h2.433a.75.75 0 000-1.5H4.598a.75.75 0 00-.75.75v3.634a.75.75 0 001.5 0v-2.033l.312.311a7 7 0 0011.712-3.138.75.75 0 00-1.449-.39zm-11.23-3.047a.75.75 0 01.564-.89 7 7 0 0111.712 3.138.75.75 0 01-1.449.39 5.5 5.5 0 00-9.201-2.466l-.312.311V5.798a.75.75 0 00-1.5 0v3.634a.75.75 0 00.75.75h3.634a.75.75 0 000-1.5H6.534l.312-.311a.75.75 0 00-.326-1.213z" clip-rule="evenodd"/>
          </svg>
          重试
        </button>
      </div>
      
      <!-- Content -->
      <div v-else class="doc-content">
        <!-- Breadcrumb -->
        <nav class="doc-breadcrumb">
          <router-link to="/" class="breadcrumb-link">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M9.293 2.293a1 1 0 011.414 0l7 7A1 1 0 0117 11h-1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-3a1 1 0 00-1-1H9a1 1 0 00-1 1v3a1 1 0 01-1 1H5a1 1 0 01-1-1v-6H3a1 1 0 01-.707-1.707l7-7z" clip-rule="evenodd"/>
            </svg>
            首页
          </router-link>
          <span class="breadcrumb-separator">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clip-rule="evenodd"/>
            </svg>
          </span>
          <span class="breadcrumb-current">文档</span>
        </nav>

        <!-- Document Header -->
        <header class="doc-header">
          <h1>{{ title }}</h1>
          <p v-if="description" class="doc-description">{{ description }}</p>
          <div class="doc-meta">
            <span class="meta-item">
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm.75-12.25a.75.75 0 00-1.5 0v4.59L7.3 7.24a.75.75 0 00-1.1 1.02l3.25 3.5a.75.75 0 001.1 0l3.25-3.5a.75.75 0 00-1.1-1.02l-1.95 2.1V5.75z" clip-rule="evenodd"/>
              </svg>
              最后更新: 2024
            </span>
          </div>
        </header>
        
        <!-- Markdown Body -->
        <article class="markdown-body" v-html="content"></article>

        <!-- Page Footer -->
        <footer class="doc-footer">
          <div class="footer-feedback">
            <p>这篇文档对您有帮助吗？</p>
            <div class="feedback-buttons">
              <button class="feedback-btn">
                <svg viewBox="0 0 20 20" fill="currentColor">
                  <path d="M1 8.74c0 .983.713 1.825 1.674 1.947.797.103 1.532.244 2.227.425.468.121.843.458.997.92.155.463.076.993-.22 1.372-.295.379-.726.594-1.176.594H3.5a.5.5 0 000 1h1.004c.69 0 1.342-.283 1.797-.783.453-.498.73-1.15.794-1.842A5.995 5.995 0 009 8.74V4a1 1 0 00-1-1H5.5a1 1 0 00-.894.553L3.22 4.22a.5.5 0 00.024.048L5.5 7H4a1 1 0 00-1 1v.74z"/>
                  <path d="M13 8.74c0 .983.713 1.825 1.674 1.947.797.103 1.532.244 2.227.425.468.121.843.458.997.92.155.463.076.993-.22 1.372-.295.379-.726.594-1.176.594h-1.004a.5.5 0 000 1h1.004c.69 0 1.342-.283 1.797-.783.453-.498.73-1.15.794-1.842A5.995 5.995 0 0017 8.74V4a1 1 0 00-1-1h-2.5a1 1 0 00-.894.553L12.22 4.22a.5.5 0 00.024.048L14.5 7H13a1 1 0 00-1 1v.74z"/>
                </svg>
                有帮助
              </button>
              <button class="feedback-btn">
                <svg viewBox="0 0 20 20" fill="currentColor">
                  <path d="M19 11.24c0 .983-.713 1.825-1.674 1.947-.797.103-1.532.244-2.227.425-.468.121-.843.458-.997.92-.155.463-.076.993.22 1.372.295.379.726.594 1.176.594H16.5a.5.5 0 010 1h-1.004c-.69 0-1.342-.283-1.797-.783a2.501 2.501 0 01-.794-1.842A5.995 5.995 0 0111 11.24V16a1 1 0 01-1 1H7.5a1 1 0 01-.894-.553l-.386-.773a.5.5 0 01.024-.048L8.5 13H7a1 1 0 01-1-1v-.74z"/>
                  <path d="M7 11.24c0 .983-.713 1.825-1.674 1.947-.797.103-1.532.244-2.227.425-.468.121-.843.458-.997.92-.155.463-.076.993.22 1.372.295.379.726.594 1.176.594H4.5a.5.5 0 010 1H3.496c-.69 0-1.342-.283-1.797-.783a2.501 2.501 0 01-.794-1.842A5.995 5.995 0 011 11.24V16a1 1 0 01-1 1h-2.5a1 1 0 01-.894-.553l-.386-.773a.5.5 0 01.024-.048L-1.5 13H-3a1 1 0 01-1-1v-.74z"/>
                </svg>
                需要改进
              </button>
            </div>
          </div>
          
          <div class="footer-nav">
            <router-link 
              v-if="prevDoc"
              :to="prevDoc.path"
              class="footer-link prev"
            >
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M12.79 5.23a.75.75 0 01-.02 1.06L8.832 10l3.938 3.71a.75.75 0 11-1.04 1.08l-4.5-4.25a.75.75 0 010-1.08l4.5-4.25a.75.75 0 011.06.02z" clip-rule="evenodd"/>
              </svg>
              <div class="footer-link-content">
                <span class="footer-link-label">上一篇</span>
                <span class="footer-link-title">{{ prevDoc.title }}</span>
              </div>
            </router-link>
            <div v-else></div>
            <router-link 
              v-if="nextDoc"
              :to="nextDoc.path"
              class="footer-link next"
            >
              <div class="footer-link-content">
                <span class="footer-link-label">下一篇</span>
                <span class="footer-link-title">{{ nextDoc.title }}</span>
              </div>
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clip-rule="evenodd"/>
              </svg>
            </router-link>
            <div v-else></div>
          </div>
        </footer>
      </div>
      </div>
      
      <!-- Right Sidebar TOC -->
      <aside v-if="headings.length > 0" class="doc-toc">
        <div class="toc-inner">
          <h3 class="toc-title">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M2 4.75A.75.75 0 012.75 4h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 4.75zm7 10.5a.75.75 0 01.75-.75h7.5a.75.75 0 010 1.5h-7.5a.75.75 0 01-.75-.75zM2 10a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 10z" clip-rule="evenodd"/>
            </svg>
            本页目录
          </h3>
          <nav class="toc-nav">
            <a 
              v-for="heading in headings" 
              :key="heading.id"
              :href="`#${heading.id}`" 
              class="toc-link"
              :class="[`toc-level-${heading.level}`, { active: activeHeading === heading.id }]"
              @click.prevent="scrollToHeading(heading.id)"
            >
              <span class="toc-indicator"></span>
              <span>{{ heading.text }}</span>
            </a>
          </nav>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.doc-layout {
  display: flex;
  min-height: calc(100vh - 72px);
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.4s var(--ease-out);
}

.doc-layout.visible {
  opacity: 1;
  transform: translateY(0);
}

/* ============================================
   Sidebar Toggle (Mobile)
   ============================================ */
.sidebar-toggle {
  display: none;
  position: fixed;
  bottom: var(--spacing-6);
  right: var(--spacing-6);
  z-index: var(--z-docked);
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-3) var(--spacing-5);
  background: var(--gradient-primary);
  color: #ffffff;
  border-radius: var(--radius-full);
  box-shadow: var(--shadow-primary-lg);
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  transition: all var(--transition-base);
  cursor: pointer;
}

.sidebar-toggle:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: var(--shadow-primary-xl);
}

.sidebar-toggle svg {
  width: 18px;
  height: 18px;
}

.sidebar-overlay {
  display: none;
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

/* ============================================
   Sidebar
   ============================================ */
.doc-sidebar {
  width: 260px;
  background: #fff;
  border-right: 1px solid var(--oc-border);
  position: sticky;
  top: 72px;
  height: calc(100vh - 72px);
  overflow-y: auto;
  flex-shrink: 0;
  transition: transform var(--motion-base) var(--ease-standard);
}

.sidebar-inner {
  padding: var(--spacing-6);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-6);
}

/* Sidebar Header */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--spacing-4);
  border-bottom: 1px solid var(--oc-border-soft);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--oc-fg);
}

.sidebar-version {
  padding: var(--spacing-0-5) var(--spacing-2);
  background: var(--oc-bg-surface);
  color: var(--oc-muted);
  font-size: var(--text-xs);
  font-weight: 500;
  border-radius: var(--radius-sm);
  border: 1px solid var(--oc-border-soft);
}

/* Sidebar Search */
.sidebar-search {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--oc-bg-surface);
  border: 1px solid var(--oc-border-soft);
  border-radius: var(--radius-sm);
  color: var(--oc-muted);
  transition: all var(--motion-fast) var(--ease-standard);
}

.sidebar-search:focus-within {
  border-color: var(--oc-border);
  box-shadow: 0 0 0 2px var(--oc-accent);
  background: var(--oc-bg-surface);
}

.sidebar-search svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.sidebar-search input {
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font-size: var(--text-sm);
  color: var(--color-gray-700);
}

.sidebar-search input::placeholder {
  color: var(--color-gray-400);
}

/* Sidebar Sections */
.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: 0 var(--spacing-2) var(--spacing-2);
  color: var(--color-gray-400);
  font-size: var(--text-xs);
  font-weight: var(--font-semibold);
  text-transform: uppercase;
  letter-spacing: var(--tracking-wider);
}

.sidebar-title svg {
  width: 14px;
  height: 14px;
}

.sidebar-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-0-5);
}

.group-title {
  padding: var(--spacing-3) var(--spacing-3) var(--spacing-1-5);
  font-size: 11px;
  font-weight: var(--font-semibold);
  color: var(--color-gray-400);
  text-transform: uppercase;
  letter-spacing: var(--tracking-wider);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-0-5);
}

.sidebar-link {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: 8px 12px;
  color: var(--oc-fg);
  text-decoration: none;
  font-size: var(--text-sm);
  font-weight: 400;
  border-radius: var(--radius-sm);
  transition: all var(--motion-fast) var(--ease-standard);
  position: relative;
  margin: 0;
  padding-left: 12px;
  padding-right: 12px;
}

.sidebar-link:hover {
  color: var(--oc-accent);
  background: var(--oc-bg-surface);
}

.sidebar-link.active {
  color: var(--oc-accent);
  background: var(--oc-bg-surface);
  font-weight: 500;
  border-left: 2px solid rgba(0, 0, 0, 0.8);
}

.sidebar-link.active::before {
  display: none;
}

.link-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  color: var(--color-gray-400);
  flex-shrink: 0;
}

.link-icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.sidebar-link.active .link-icon {
  color: var(--color-primary);
}

.link-text {
  flex: 1;
}

.link-badge {
  padding: var(--spacing-0-5) var(--spacing-2);
  background: var(--oc-bg-surface);
  color: var(--oc-muted);
  font-size: 10px;
  font-weight: 600;
  border-radius: var(--radius-sm);
  border: 1px solid var(--oc-border-soft);
}

/* ============================================
   Document Wrapper & Main Content
   ============================================ */
.doc-wrapper {
  flex: 1;
  display: flex;
  min-width: 0;
}

.doc-main {
  flex: 1;
  min-width: 0;
  padding: 24px;
  max-width: 880px;
  margin: 0 auto;
  background: var(--oc-bg);
}

/* ============================================
   Right Sidebar TOC
   ============================================ */
.doc-toc {
  width: 220px;
  flex-shrink: 0;
  position: sticky;
  top: 72px;
  height: calc(100vh - 72px);
  overflow-y: auto;
  padding: var(--spacing-6) var(--spacing-4);
  background: #fff;
  border-left: 1px solid var(--oc-border);
}

.toc-inner {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

.toc-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: 0 var(--spacing-2) var(--spacing-3);
  color: var(--oc-muted);
  font-size: var(--text-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: var(--tracking-wider);
  border-bottom: 1px solid var(--oc-border-soft);
  margin-bottom: var(--spacing-2);
}

.toc-title svg {
  width: 14px;
  height: 14px;
}

.toc-nav {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-0-5);
}

.toc-link {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-1-5) var(--spacing-2);
  color: var(--oc-muted);
  text-decoration: none;
  font-size: var(--text-xs);
  font-weight: 400;
  border-radius: var(--radius-sm);
  transition: all var(--motion-fast) var(--ease-standard);
  border-left: 2px solid transparent;
}

.toc-link:hover {
  color: var(--oc-accent);
  background: var(--oc-bg-surface);
}

.toc-link.active {
  color: var(--oc-accent);
  border-left-color: var(--oc-accent);
  font-weight: 500;
}

.toc-indicator {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--oc-muted);
  flex-shrink: 0;
  transition: all var(--motion-fast) var(--ease-standard);
}

.toc-link.active .toc-indicator {
  background: var(--oc-accent);
  width: 6px;
  height: 6px;
}

.toc-level-3 {
  padding-left: calc(var(--spacing-2) + var(--spacing-3));
}

.toc-level-4 {
  padding-left: calc(var(--spacing-2) + var(--spacing-6));
}

/* States */
.doc-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-24) 0;
  text-align: center;
}

.loading-spinner {
  margin-bottom: var(--spacing-4);
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-gray-300);
  border-top-color: var(--color-primary-light);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.error-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-error-light);
  color: var(--color-error);
  border-radius: var(--radius-2xl);
  margin-bottom: var(--spacing-4);
}

.error-icon svg {
  width: 32px;
  height: 32px;
}

.state-title {
  font-size: var(--text-xl);
  font-weight: var(--font-semibold);
  color: var(--color-gray-900);
  margin-bottom: var(--spacing-2);
}

.state-text {
  font-size: var(--text-sm);
  color: var(--color-gray-500);
  margin-bottom: var(--spacing-6);
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-4);
  background: rgba(99, 102, 241, 0.15);
  color: var(--color-primary-light);
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
  border: 1px solid rgba(99, 102, 241, 0.25);
}

.retry-btn:hover {
  background: rgba(99, 102, 241, 0.25);
  transform: translateY(-1px);
  box-shadow: 0 0 15px -3px rgba(99, 102, 241, 0.2);
}

.retry-btn svg {
  width: 16px;
  height: 16px;
}

/* Document Content */
.doc-content {
  animation: fadeIn 0.4s var(--ease-out);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Breadcrumb */
.doc-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-8);
  font-size: var(--text-sm);
}

.breadcrumb-link {
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  color: var(--color-gray-400);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.breadcrumb-link:hover {
  color: var(--color-primary-light);
}

.breadcrumb-link svg {
  width: 16px;
  height: 16px;
}

.breadcrumb-separator {
  color: var(--color-gray-400);
}

.breadcrumb-separator svg {
  width: 16px;
  height: 16px;
}

.breadcrumb-current {
  color: var(--color-gray-600);
  font-weight: var(--font-medium);
}

/* Document Header */
.doc-header {
  margin-bottom: var(--spacing-10);
  padding-bottom: var(--spacing-8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.doc-header h1 {
  font-size: var(--text-4xl);
  font-weight: var(--font-extrabold);
  color: var(--color-primary);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  margin-bottom: var(--spacing-4);
}

.doc-description {
  font-size: var(--text-lg);
  color: var(--color-gray-500);
  margin-bottom: var(--spacing-4);
  line-height: var(--leading-relaxed);
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-1-5);
  font-size: var(--text-xs);
  color: var(--color-gray-400);
}

.meta-item svg {
  width: 14px;
  height: 14px;
}

/* Markdown Body - OpenCode Exact */
.markdown-body {
  line-height: 1.625;
  color: var(--oc-fg-2);
}

.markdown-body :deep(h1) {
  font-size: 2.375rem; /* 38px */
  font-weight: 700;
  color: var(--oc-fg);
  margin: 2.5rem 0 1rem 0;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--oc-border-soft);
  letter-spacing: var(--tracking-tight);
}

.markdown-body :deep(h2) {
  font-size: 1.75rem; /* 28px */
  font-weight: 700;
  color: var(--oc-fg);
  margin: 2rem 0 0.75rem 0;
  letter-spacing: var(--tracking-tight);
}

.markdown-body :deep(h3) {
  font-size: 1.375rem; /* 22px */
  font-weight: 600;
  color: var(--oc-fg);
  margin: 1.75rem 0 0.5rem 0;
}

.markdown-body :deep(h4) {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
  color: var(--color-gray-700);
  margin: 1.5rem 0 0.5rem 0;
}

.markdown-body :deep(p) {
  margin: 0 0 1.25rem 0;
}

.markdown-body :deep(a) {
  color: var(--oc-accent);
  text-decoration: underline;
  text-decoration-color: rgba(0, 122, 255, 0.3);
  text-underline-offset: 2px;
  transition: all var(--motion-fast) var(--ease-standard);
}

.markdown-body :deep(a:hover) {
  color: var(--oc-accent-hover);
  text-decoration-color: var(--oc-accent);
}

.markdown-body :deep(strong) {
  font-weight: 600;
  color: var(--oc-fg);
}

.markdown-body :deep(code) {
  background: var(--oc-bg-surface);
  padding: 0.15em 0.4em;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 14px;
  color: var(--oc-fg);
  border: 1px solid var(--oc-border-soft);
}

.markdown-body :deep(pre) {
  background: var(--oc-bg-surface);
  color: var(--oc-fg-2);
  padding: 16px;
  border-radius: var(--radius-lg);
  overflow-x: auto;
  margin: 1.5rem 0;
  border: 1px solid var(--oc-border);
  transition: all var(--motion-base) var(--ease-standard);
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: inherit;
  border: none;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0 0 1.25rem 0;
  padding-left: 1.75rem;
}

.markdown-body :deep(li) {
  margin-bottom: 0.5rem;
}

.markdown-body :deep(li::marker) {
  color: var(--color-gray-400);
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid var(--oc-border);
  margin: 1.5rem 0;
  padding: var(--spacing-4) var(--spacing-5);
  background: var(--oc-bg-surface);
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  color: var(--oc-fg-2);
  transition: all var(--motion-base) var(--ease-standard);
}

.markdown-body :deep(blockquote p) {
  margin: 0;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1.5rem 0;
  font-size: var(--text-sm);
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--oc-border);
  padding: 12px 16px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--oc-bg-surface);
  font-weight: 600;
  color: var(--oc-fg);
}

.markdown-body :deep(tr:hover td) {
  background: var(--oc-bg-surface);
}

.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-xl);
  margin: 1.5rem 0;
  box-shadow: var(--shadow-lg);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--oc-border-soft);
  margin: 2.5rem 0;
}

/* Document Footer */
.doc-footer {
  margin-top: var(--spacing-12);
  padding-top: var(--spacing-8);
  border-top: 1px solid var(--oc-border-soft);
}

/* Feedback */
.footer-feedback {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-5) var(--spacing-6);
  background: var(--oc-bg-surface);
  border: 1px solid var(--oc-border-soft);
  border-radius: var(--radius-lg);
  margin-bottom: var(--spacing-10);
}

.footer-feedback p {
  font-size: var(--text-sm);
  color: var(--color-gray-500);
  font-weight: var(--font-medium);
}

.feedback-buttons {
  display: flex;
  gap: var(--spacing-2);
}

.feedback-btn {
  display: flex;
  align-items: center;
  gap: var(--spacing-1-5);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--oc-bg-surface);
  color: var(--oc-muted);
  font-size: var(--text-xs);
  font-weight: 500;
  border-radius: var(--radius-sm);
  border: 1px solid var(--oc-border-soft);
  transition: all var(--motion-fast) var(--ease-standard);
  cursor: pointer;
}

.feedback-btn:hover {
  background: var(--oc-border-soft);
  border-color: var(--oc-border);
  color: var(--oc-accent);
  transform: translateY(-1px);
}

.feedback-btn svg {
  width: 14px;
  height: 14px;
}

/* Navigation */
.footer-nav {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-4);
}

.footer-link {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-4) var(--spacing-5);
  background: var(--oc-bg-surface);
  border: 1px solid var(--oc-border-soft);
  border-radius: var(--radius-lg);
  text-decoration: none;
  transition: all var(--motion-base) var(--ease-standard);
  flex: 1;
}

.footer-link:hover {
  border-color: var(--oc-border);
  transform: translateY(-2px);
}

.footer-link.prev {
  justify-content: flex-start;
}

.footer-link.next {
  justify-content: flex-end;
}

.footer-link svg {
  width: 20px;
  height: 20px;
  color: var(--color-gray-400);
  flex-shrink: 0;
  transition: transform var(--transition-fast);
}

.footer-link.prev:hover svg {
  transform: translateX(-4px);
}

.footer-link.next:hover svg {
  transform: translateX(4px);
}

.footer-link-content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-0-5);
}

.footer-link-label {
  font-size: var(--text-xs);
  color: var(--color-gray-400);
  font-weight: var(--font-medium);
}

.footer-link-title {
  font-size: var(--text-sm);
  color: var(--color-gray-600);
  font-weight: var(--font-semibold);
}

.footer-link-content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-0-5);
}

.footer-link-label {
  font-size: var(--text-xs);
  color: var(--color-gray-500);
  font-weight: var(--font-medium);
}

.footer-link-title {
  font-size: var(--text-sm);
  color: var(--color-gray-700);
  font-weight: var(--font-semibold);
}

/* ============================================
   Responsive Design
   ============================================ */
@media (max-width: 1024px) {
  .sidebar-toggle {
    display: flex;
  }
  
  .sidebar-overlay {
    display: block;
  }
  
  .doc-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: calc(var(--z-overlay) + 1);
    transform: translateX(-100%);
    background: #050a15;
    width: 300px;
  }
  
  .doc-sidebar.open {
    transform: translateX(0);
    box-shadow: var(--shadow-2xl);
  }
  
  .doc-main {
    padding: var(--spacing-6);
    max-width: 100%;
  }
  
  .doc-toc {
    display: none;
  }
}

@media (max-width: 768px) {
  .doc-header h1 {
    font-size: var(--text-3xl);
  }
  
  .footer-feedback {
    flex-direction: column;
    gap: var(--spacing-3);
    text-align: center;
  }
  
  .footer-nav {
    flex-direction: column;
  }
  
  .footer-link {
    justify-content: center;
  }
}

/* ============================================
   Light Theme Overrides
   ============================================ */
/* Sidebar - Light */
[data-theme="light"] .doc-sidebar {
  background: #fff;
  border-right-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .sidebar-search {
  background: rgba(0, 0, 0, 0.03);
  border-color: rgba(0, 0, 0, 0.06);
  color: var(--color-gray-400);
}

[data-theme="light"] .sidebar-search:focus-within {
  border-color: rgba(79, 70, 229, 0.25);
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.06);
  background: rgba(255, 255, 255, 0.8);
}

[data-theme="light"] .sidebar-search input {
  color: var(--color-gray-700);
}

[data-theme="light"] .sidebar-search input::placeholder {
  color: var(--color-gray-400);
}

[data-theme="light"] .sidebar-header {
  border-bottom-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .sidebar-version {
  background: rgba(79, 70, 229, 0.06);
  color: var(--color-primary);
  border-color: rgba(79, 70, 229, 0.15);
}

[data-theme="light"] .group-title {
  color: var(--color-gray-500);
}

[data-theme="light"] .sidebar-link {
  color: var(--color-gray-500);
}

[data-theme="light"] .sidebar-link:hover {
  color: var(--color-gray-800);
  background: rgba(79, 70, 229, 0.06);
}

[data-theme="light"] .sidebar-link.active {
  color: var(--color-primary);
  background: var(--oc-bg-surface);
  border-left: 2px solid rgba(0, 0, 0, 0.2);
}

[data-theme="light"] .sidebar-link.active .link-icon {
  color: var(--color-primary);
}

[data-theme="light"] .link-icon {
  color: var(--color-gray-400);
}

[data-theme="light"] .link-badge {
  background: rgba(79, 70, 229, 0.06);
  color: var(--color-primary);
  border-color: rgba(79, 70, 229, 0.15);
}

/* Doc Main - Light */
[data-theme="light"] .doc-main {
  background: #ffffff;
}

/* Right TOC - Light */
[data-theme="light"] .doc-toc {
  background: #fff;
  border-left-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .toc-title {
  color: var(--color-gray-500);
  border-bottom-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .toc-link {
  color: var(--color-gray-500);
}

[data-theme="light"] .toc-link:hover {
  color: var(--color-gray-800);
  background: rgba(79, 70, 229, 0.06);
}

[data-theme="light"] .toc-link.active {
  color: var(--color-primary);
  background: rgba(79, 70, 229, 0.08);
}

[data-theme="light"] .toc-indicator {
  background: var(--color-gray-300);
}

[data-theme="light"] .toc-link.active .toc-indicator {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 0 6px rgba(79, 70, 229, 0.2);
}

/* Breadcrumb - Light */
[data-theme="light"] .breadcrumb-link {
  color: var(--color-gray-400);
}

[data-theme="light"] .breadcrumb-link:hover {
  color: var(--color-primary);
}

[data-theme="light"] .breadcrumb-separator {
  color: var(--color-gray-300);
}

[data-theme="light"] .breadcrumb-current {
  color: var(--color-gray-700);
}

/* Doc Header - Light */
[data-theme="light"] .doc-header {
  border-bottom-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .doc-header h1 {
  color: #0f172a;
  background: linear-gradient(135deg, #0f172a 0%, #4f46e5 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

[data-theme="light"] .doc-description {
  color: var(--color-gray-500);
}

/* Markdown Body - Light */
[data-theme="light"] .markdown-body {
  color: var(--color-gray-600);
}

[data-theme="light"] .markdown-body :deep(h1) {
  color: #0f172a;
  border-bottom-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .markdown-body :deep(h2) {
  color: #0f172a;
}

[data-theme="light"] .markdown-body :deep(h3) {
  color: var(--color-gray-800);
}

[data-theme="light"] .markdown-body :deep(h4) {
  color: var(--color-gray-700);
}

[data-theme="light"] .markdown-body :deep(a) {
  color: var(--color-primary);
  text-decoration-color: rgba(79, 70, 229, 0.3);
}

[data-theme="light"] .markdown-body :deep(a:hover) {
  color: var(--color-primary-dark);
  text-decoration-color: var(--color-primary);
}

[data-theme="light"] .markdown-body :deep(strong) {
  color: var(--color-gray-800);
}

[data-theme="light"] .markdown-body :deep(code) {
  color: var(--color-primary);
  border-color: rgba(79, 70, 229, 0.08);
}

[data-theme="light"] .markdown-body :deep(pre) {
  background: #f1f5f9;
  border-color: rgba(0, 0, 0, 0.06);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.04);
}

[data-theme="light"] .markdown-body :deep(blockquote) {
  border-left-color: rgba(79, 70, 229, 0.3);
  background: rgba(79, 70, 229, 0.04);
}

[data-theme="light"] .markdown-body :deep(th),
[data-theme="light"] .markdown-body :deep(td) {
  border-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .markdown-body :deep(th) {
  background: rgba(0, 0, 0, 0.02);
  color: var(--color-gray-700);
}

[data-theme="light"] .markdown-body :deep(tr:hover td) {
  background: rgba(79, 70, 229, 0.03);
}

[data-theme="light"] .markdown-body :deep(hr) {
  border-top-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .markdown-body :deep(li::marker) {
  color: var(--color-gray-400);
}

/* States - Light */
[data-theme="light"] .spinner {
  border-color: var(--color-gray-200);
  border-top-color: var(--color-primary);
}

[data-theme="light"] .error-icon {
  background: var(--color-error-light);
}

[data-theme="light"] .state-title {
  color: var(--color-gray-800);
}

[data-theme="light"] .retry-btn {
  background: rgba(79, 70, 229, 0.06);
  color: var(--color-primary);
  border-color: rgba(79, 70, 229, 0.15);
}

[data-theme="light"] .retry-btn:hover {
  background: rgba(79, 70, 229, 0.12);
  box-shadow: 0 0 15px -3px rgba(79, 70, 229, 0.1);
}

/* Doc Footer - Light */
[data-theme="light"] .doc-footer {
  border-top-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .footer-feedback {
  background: rgba(0, 0, 0, 0.02);
  border-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .footer-feedback p {
  color: var(--color-gray-500);
}

[data-theme="light"] .feedback-btn {
  background: rgba(0, 0, 0, 0.03);
  color: var(--color-gray-500);
  border-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .feedback-btn:hover {
  background: rgba(79, 70, 229, 0.06);
  border-color: rgba(79, 70, 229, 0.15);
  color: var(--color-primary);
  box-shadow: 0 0 15px -3px rgba(79, 70, 229, 0.08);
}

[data-theme="light"] .footer-link {
  background: rgba(0, 0, 0, 0.02);
  border-color: rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .footer-link:hover {
  border-color: rgba(79, 70, 229, 0.15);
  box-shadow: 0 0 20px -5px rgba(79, 70, 229, 0.08);
}

[data-theme="light"] .footer-link svg {
  color: var(--color-gray-400);
}

[data-theme="light"] .footer-link-label {
  color: var(--color-gray-400);
}

[data-theme="light"] .footer-link-title {
  color: var(--color-gray-700);
}

/* Mobile Sidebar - Light */
@media (max-width: 1024px) {
  [data-theme="light"] .doc-sidebar {
    background: #fff;
  }

  [data-theme="light"] .sidebar-overlay {
    background: rgba(0, 0, 0, 0.2);
  }
}
</style>