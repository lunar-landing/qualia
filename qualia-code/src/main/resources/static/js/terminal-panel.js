/**
 * TerminalPanel —— 工作区「终端」面板组件（自包含：样式自注入，依赖页面 CSS 变量）
 *
 * 职责：
 *   1. 从步骤流中过滤 bash 命令（ACTION）及其紧随的执行输出（OBSERVATION），
 *      以连续终端屏幕的形态渲染：`$` 提示符 + 命令绿字 + 输出灰字
 *   2. 执行中的命令（尚无输出）在命令行尾显示闪烁光标；空闲时屏幕底部保留待命提示符
 *   3. 每次渲染自动滚动到底部，模拟真实终端的跟随行为
 *
 * 视觉约定：深色主题为深底绿字，浅色主题为浅底深字，配色随主题整体适配
 *
 * 对外 API：
 *   TerminalPanel.init(containerEl)   绑定渲染容器（面板挂载点）
 *   TerminalPanel.render(steps)       按步骤数组全量渲染，返回命令条数
 */
window.TerminalPanel = (function () {
    'use strict';

    const OUTPUT_LIMIT = 4000;

    let container = null;

    // ===== 内部工具函数 =====
    function esc(s) {
        const div = document.createElement('div');
        div.textContent = String(s == null ? '' : s);
        return div.innerHTML;
    }

    function truncate(s, n) {
        s = String(s || '');
        return s.length > n ? s.slice(0, n) + '…' : s;
    }

    // 提取 bash 命令块：命令 + 紧随其后的观察输出；碰到下一个 ACTION 说明本条已结束
    function extractBlocks(steps) {
        const blocks = [];
        (steps || []).forEach((step, i) => {
            if (step.stepType !== 'ACTION' || step.toolName !== 'bash') return;
            const cmd = (step.toolArgs && step.toolArgs.command) || '';
            let output = null;
            for (let j = i + 1; j < steps.length; j++) {
                if (steps[j].stepType === 'OBSERVATION') { output = steps[j].content || ''; break; }
                if (steps[j].stepType === 'ACTION') { output = ''; break; }
            }
            // output === null 表示命令仍在执行（后面既无输出也无新动作）
            blocks.push({ cmd, output });
        });
        return blocks;
    }

    // ===== 渲染 =====
    function render(steps) {
        if (!container) return 0;
        const blocks = extractBlocks(steps);
        const running = blocks.length > 0 && blocks[blocks.length - 1].output === null;

        let body = '';
        blocks.forEach(b => {
            const isRunning = b.output === null;
            body += `
                <div class="tp-entry">
                    <div class="tp-cmd"><span class="tp-prompt">$</span> ${esc(b.cmd)}${isRunning ? '<span class="tp-cursor"></span>' : ''}</div>
                    ${b.output ? `<div class="tp-out">${esc(truncate(b.output, OUTPUT_LIMIT))}</div>` : ''}
                </div>
            `;
        });
        // 空闲时保留一条待命提示符；执行中光标已挂在命令行尾
        if (!running) {
            body += `<div class="tp-idle"><span class="tp-prompt">$</span> <span class="tp-cursor"></span></div>`;
        }
        if (blocks.length === 0) {
            body += `<div class="tp-hint">等待 Agent 执行命令…</div>`;
        }

        container.innerHTML = `
            <div class="tp-shell">
                <div class="tp-screen">${body}</div>
            </div>
        `;
        const screen = container.querySelector('.tp-screen');
        if (screen) screen.scrollTop = screen.scrollHeight;
        return blocks.length;
    }

    function init(el) {
        if (!el) return;
        container = el;
        container.classList.remove('steps-list');
        container.classList.add('tp-host');
        render([]);
    }

    // ===== 样式自注入（终端屏幕固定深色，边框随页面 CSS 变量适配）=====
    const STYLE = `
        /* 面板宿主：让终端壳撑满整个面板 */
        .tp-host {
            flex: 1;
            display: flex;
            flex-direction: column;
            padding: 11px;
            min-height: 0;
        }
        .tp-shell {
            flex: 1;
            display: flex;
            flex-direction: column;
            min-height: 0;
            border: 1px solid var(--border-color);
            border-radius: 9px;
            overflow: hidden;
            background: var(--terminal-bg);
        }

        /* 终端屏幕 */
        .tp-screen {
            flex: 1;
            overflow-y: auto;
            padding: 11px 13px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            line-height: 1.7;
        }
        .tp-screen::-webkit-scrollbar { width: 4px; }
        .tp-screen::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.14); border-radius: 2px; }
        .tp-entry { margin-bottom: 9px; }
        .tp-prompt { color: var(--terminal-prompt); user-select: none; }
        .tp-cmd {
            color: var(--terminal-success);
            word-break: break-all;
            white-space: pre-wrap;
        }
        .tp-out {
            color: var(--terminal-idle);
            white-space: pre-wrap;
            word-break: break-word;
            margin-top: 2px;
        }
        .tp-idle { color: var(--terminal-prompt); }
        .tp-hint {
            margin-top: 5px;
            font-size: 10.5px;
            color: var(--text-muted);
        }

        /* 闪烁光标 */
        .tp-cursor {
            display: inline-block;
            width: 6px;
            height: 12px;
            margin-left: 3px;
            vertical-align: -2px;
            background: var(--terminal-success);
            animation: tpBlink 1.1s steps(1) infinite;
        }
        .tp-idle .tp-cursor { background: var(--terminal-prompt); }
        @keyframes tpBlink {
            50% { opacity: 0; }
        }

        /* 浅色主题：浅底深字，沿用页面浅色终端配色 */
        body.light-theme .tp-shell { background: var(--terminal-bg); }
        body.light-theme .tp-screen::-webkit-scrollbar-thumb { background: rgba(0, 0, 0, 0.16); }
        body.light-theme .tp-prompt { color: var(--terminal-prompt); }
        body.light-theme .tp-cmd { color: var(--terminal-success); }
        body.light-theme .tp-out { color: var(--terminal-prompt); }
        body.light-theme .tp-idle { color: var(--terminal-prompt); }
        body.light-theme .tp-hint { color: var(--text-muted); }
        body.light-theme .tp-cursor { background: var(--terminal-success); }
        body.light-theme .tp-idle .tp-cursor { background: var(--terminal-prompt); }
    `;

    function injectStyle() {
        if (document.getElementById('terminal-panel-style')) return;
        const style = document.createElement('style');
        style.id = 'terminal-panel-style';
        style.textContent = STYLE;
        document.head.appendChild(style);
    }
    injectStyle();

    return { init, render };
})();
