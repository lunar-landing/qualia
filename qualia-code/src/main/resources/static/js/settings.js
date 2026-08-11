/**
 * SettingsDialog —— 全局配置弹窗组件（自包含：样式自注入、DOM 自创建，无外部依赖）
 *
 * 职责：
 *   1. 居中弹窗承载全局配置（~/.qualia/qualia-code.json），与工作区无关
 *   2. 「模型配置」「MCP 服务器」「技能」「工具」四个 Tab；技能 Tab 只读展示 ~/.qualia/skills 下的全局技能
 *   3. 模型 Tab 为预览卡片网格，点击卡片进入编辑弹窗；弹窗内保存写回草稿，持久化由「保存配置」完成
 *   4. 保存成功后回调 window.loadMcpBadge 同步顶栏 MCP 数量
 *
 * 对外 API：
 *   window.openSettings()   打开弹窗（首次打开时拉取配置）
 *   window.closeSettings()  关闭弹窗
 *   （内部事件处理统一挂在 window.QSettings 命名空间下，供生成的 DOM 引用）
 */
(function () {
    'use strict';

    // ===== 样式注入 =====
    const CSS = `
        /* ===== 设置弹窗（全局配置，与工作区无关）===== */
        .settings-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.55);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
        }
        .settings-overlay.open {
            display: flex;
        }
        .settings-dialog {
            width: min(800px, calc(100vw - 48px));
            height: min(82vh, 774px);
            background: var(--bg-surface);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            box-shadow: var(--shadow);
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .settings-dialog-head {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 13px 16px 0;
        }
        .settings-dialog-head h4 {
            font-size: 12.5px;
            font-weight: 600;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 7px;
        }
        .settings-dialog-head h4 i {
            color: var(--text-muted);
            font-size: 11.5px;
        }
        .settings-dialog-head button {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 12.5px;
            padding: 4px 7px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .settings-dialog-head button:hover {
            background: var(--bg-hover);
            color: var(--text-primary);
        }
        .settings-main {
            flex: 1;
            min-height: 0;
            display: flex;
            margin-top: 9px;
            border-top: 1px solid var(--border-color);
        }
        .settings-tabs {
            flex-shrink: 0;
            width: 180px;
            display: flex;
            flex-direction: column;
            gap: 3px;
            padding: 9px 7px;
            border-right: 1px solid var(--border-color);
        }
        .settings-tab {
            background: transparent;
            border: none;
            border-radius: 7px;
            padding: 7px 9px;
            font-size: 11.5px;
            font-weight: 500;
            font-family: inherit;
            color: var(--text-secondary);
            display: flex;
            align-items: center;
            gap: 7px;
            text-align: left;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .settings-tab i {
            width: 13px;
            text-align: center;
            font-size: 10.5px;
            color: var(--text-muted);
        }
        .settings-tab:hover {
            background: var(--bg-hover);
            color: var(--text-primary);
        }
        .settings-tab.active {
            background: var(--bg-hover);
            color: var(--text-primary);
            font-weight: 600;
        }
        .settings-tab.active i {
            color: var(--accent);
        }
        .settings-content {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
        }
        .settings-body {
            flex: 1;
            min-height: 0;
            overflow-y: auto;
            padding: 13px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }
        .set-empty {
            flex: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 9px;
            color: var(--text-muted);
            font-size: 11.5px;
        }
        .set-empty i {
            font-size: 20px;
            opacity: 0.6;
        }
        .set-card {
            border: 1px solid var(--border-color);
            border-radius: 9px;
            background: var(--bg-input);
            padding: 11px;
            display: flex;
            flex-direction: column;
            gap: 7px;
            margin-bottom: 9px;
        }
        .set-card.is-default {
            border-color: var(--accent);
        }
        .set-card-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
        }
        .set-card-info {
            display: flex;
            align-items: center;
            gap: 8px;
            min-width: 0;
            flex: 1;
        }
        .set-card-actions {
            display: flex;
            align-items: center;
            gap: 6px;
            flex-shrink: 0;
        }
        .set-card-footer {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding-top: 8px;
            margin-top: 4px;
            border-top: 1px solid var(--border-color);
        }
        .set-card-del {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 10.5px;
            padding: 4px 5px;
            border-radius: 5px;
            cursor: pointer;
            transition: color 0.15s ease, background 0.15s ease;
            opacity: 0.6;
        }
        .set-card-del:hover {
            opacity: 1;
            background: rgba(239, 68, 68, 0.12);
            color: var(--error);
        }
        /* ===== 模型预览卡片网格 ===== */
        .model-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
            gap: 10px;
        }
        .model-card {
            position: relative;
            display: flex;
            flex-direction: column;
            background: var(--bg-input);
            border: 1px solid var(--border-color);
            border-radius: 10px;
            padding: 13px 13px 10px;
            cursor: pointer;
            overflow: hidden;
            transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
        }
        .model-card:hover {
            border-color: var(--accent);
            transform: translateY(-2px);
            box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
        }
        .model-card.is-default {
            border-color: var(--accent);
        }
        .mc-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            margin-bottom: 10px;
        }
        .mc-provider {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-size: 11px;
            font-weight: 500;
            color: var(--text-secondary);
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .mc-provider .dot {
            width: 18px;
            height: 18px;
            border-radius: 5px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 9px;
            font-weight: 700;
            color: var(--white);
            flex-shrink: 0;
        }
        .mc-dot-dashscope { background: linear-gradient(145deg, #615ced, #8a7cf0); }
        .mc-dot-deepseek { background: linear-gradient(145deg, #2f6bff, #5c9bff); }
        .mc-dot-xiaomi { background: linear-gradient(145deg, #ff8a00, #ffb340); }
        .mc-dot-openai { background: linear-gradient(145deg, #30333e, #5a5f70); }
        .mc-dot-custom { background: var(--text-muted); }
        .mc-badge-default {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-size: 10px;
            font-weight: 600;
            color: var(--accent-light);
            background: var(--bg-active);
            border-radius: 100px;
            padding: 2px 7px;
            flex-shrink: 0;
        }
        .mc-model-id {
            font-size: 13px;
            font-weight: 600;
            color: var(--text-primary);
            margin-bottom: 2px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .mc-name {
            font-size: 11px;
            color: var(--text-muted);
            margin-bottom: 10px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .mc-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            padding-top: 9px;
            margin-top: auto;
            border-top: 1px dashed var(--border-color);
        }
        .mc-tag {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-size: 10px;
            color: var(--text-secondary);
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 100px;
            padding: 2px 7px;
        }
        .mc-tag.warn {
            color: var(--warning);
        }
        .mc-actions {
            position: absolute;
            inset: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 7px;
            background: rgba(0, 0, 0, 0.45);
            /* backdrop-filter 元素不受父级 overflow+圆角裁剪，需自带圆角 */
            border-radius: inherit;
            backdrop-filter: blur(4px);
            -webkit-backdrop-filter: blur(4px);
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.18s ease;
        }
        body.light-theme .mc-actions {
            background: rgba(238, 241, 245, 0.62);
        }
        .model-card:hover .mc-actions {
            opacity: 1;
            pointer-events: auto;
        }
        .mc-btn {
            font-size: 11px;
            font-weight: 500;
            font-family: inherit;
            padding: 5px 11px;
            border-radius: 7px;
            border: 1px solid var(--border-color);
            background: var(--bg-surface);
            color: var(--text-primary);
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .mc-btn:hover {
            border-color: var(--accent);
        }
        .mc-btn.primary {
            background: var(--accent);
            border-color: var(--accent);
            color: var(--white);
        }
        .mc-btn.ghost {
            background: transparent;
            color: var(--text-secondary);
        }
        .mc-btn.ghost:hover {
            color: var(--text-primary);
        }
        .mc-btn.danger {
            color: var(--error);
        }
        .mc-btn.danger:hover {
            border-color: var(--error);
        }
        .model-card.add-card {
            align-items: center;
            justify-content: center;
            gap: 7px;
            min-height: 128px;
            border-style: dashed;
            background: transparent;
            color: var(--text-muted);
            font-family: inherit;
        }
        .model-card.add-card:hover {
            color: var(--accent-light);
            border-color: var(--accent);
            box-shadow: none;
        }
        .model-card.add-card .plus {
            font-size: 18px;
            line-height: 1;
        }
        .model-card.add-card p {
            font-size: 11.5px;
        }
        .set-row {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .set-row label {
            font-size: 10.5px;
            font-weight: 500;
            color: var(--text-muted);
            letter-spacing: 0.3px;
        }
        .set-row input, .set-row select {
            background: var(--bg-input);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 6px 8px;
            font-size: 11.5px;
            color: var(--text-primary);
            outline: none;
            transition: border-color 0.15s ease;
            font-family: inherit;
        }
        .set-row input:focus, .set-row select:focus {
            border-color: var(--accent);
        }
        /* 自定义下拉选择器 */
        .custom-select {
            position: relative;
            flex: 1;
            min-width: 0;
        }
        .custom-select-trigger {
            display: flex;
            align-items: center;
            justify-content: space-between;
            background: var(--bg-input);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 6px 8px;
            font-size: 11.5px;
            color: var(--text-primary);
            cursor: pointer;
            transition: border-color 0.15s ease;
            gap: 6px;
        }
        .custom-select-trigger:hover {
            border-color: var(--accent);
        }
        .custom-select.open .custom-select-trigger {
            border-color: var(--accent);
        }
        .custom-select-trigger .trigger-text {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            min-width: 0;
            flex: 1;
        }
        .custom-select-trigger .trigger-arrow {
            font-size: 8px;
            color: var(--text-muted);
            transition: transform 0.2s ease;
            flex-shrink: 0;
        }
        .custom-select.open .trigger-arrow {
            transform: rotate(180deg);
        }
        .custom-select-dropdown {
            position: absolute;
            top: calc(100% + 4px);
            left: 0;
            right: 0;
            background: var(--bg-surface);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 4px;
            box-shadow: var(--shadow);
            opacity: 0;
            visibility: hidden;
            transform: translateY(-4px);
            transition: opacity 0.15s ease, transform 0.15s ease, visibility 0.15s ease;
            z-index: 1000;
            display: flex;
            flex-direction: column;
            gap: 2px;
            max-height: 200px;
            overflow-y: auto;
        }
        .custom-select.open .custom-select-dropdown {
            opacity: 1;
            visibility: visible;
            transform: translateY(0);
        }
        .custom-option {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 6px 10px;
            border-radius: 6px;
            font-size: 12px;
            color: var(--text-secondary);
            cursor: pointer;
            transition: background 0.15s ease, color 0.15s ease;
            gap: 8px;
        }
        .custom-option:hover {
            background: var(--bg-hover);
            color: var(--text-primary);
        }
        .custom-option.active {
            color: var(--text-primary);
            background: var(--bg-active);
        }
        .custom-option .option-text {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            min-width: 0;
            flex: 1;
        }
        .custom-option .check-icon {
            font-size: 10px;
            color: var(--success);
            opacity: 0;
            flex-shrink: 0;
        }
        .custom-option.active .check-icon {
            opacity: 1;
        }

        .set-kv {
            display: flex;
            gap: 5px;
            align-items: center;
        }
        .set-kv input {
            flex: 1;
            min-width: 0;
            background: var(--bg-input);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 5px 7px;
            font-size: 11px;
            color: var(--text-primary);
            outline: none;
        }
        .set-kv-del {
            background: transparent;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            padding: 4px 5px;
            border-radius: 5px;
            font-size: 10.5px;
        }
        .set-kv-del:hover {
            color: var(--error);
        }
        .set-add-btn {
            width: 100%;
            background: transparent;
            border: 1px dashed var(--border-color);
            border-radius: 8px;
            color: var(--text-secondary);
            font-size: 11.5px;
            padding: 7px;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .set-add-btn:hover {
            border-color: var(--accent);
            color: var(--accent-light);
        }
        .set-add-btn.mini {
            padding: 5px;
            font-size: 10.5px;
            border-radius: 6px;
        }
        .set-env-item {
            font-size: 11px;
            color: var(--text-secondary);
            word-break: break-all;
            line-height: 1.6;
            margin-bottom: 5px;
        }
        .settings-footer {
            flex-shrink: 0;
            padding: 11px 13px;
            border-top: 1px solid var(--border-color);
        }
        .settings-save {
            width: 100%;
            background: var(--accent);
            border: none;
            border-radius: 8px;
            color: var(--white);
            font-size: 11.5px;
            font-weight: 600;
            padding: 8px;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        .settings-save:hover {
            background: var(--accent-light);
        }
        .settings-save.saved {
            background: var(--success);
        }
        .settings-save:disabled {
            opacity: 0.6;
            cursor: default;
        }
        /* ===== 模型编辑弹窗 ===== */
        .model-edit-overlay {
            position: fixed;
            inset: 0;
            z-index: 1100;
            display: none;
            align-items: center;
            justify-content: center;
            background: rgba(0, 0, 0, 0.25);
        }
        .model-edit-overlay.open {
            display: flex;
        }
        .model-edit-dialog {
            width: min(480px, calc(100vw - 64px));
            max-height: calc(100vh - 80px);
            overflow-y: auto;
            background: var(--bg-surface);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            box-shadow: var(--shadow);
            animation: med-pop 0.18s ease;
        }
        @keyframes med-pop {
            from { transform: scale(0.96) translateY(6px); opacity: 0; }
        }
        .med-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 13px 16px 10px;
            border-bottom: 1px solid var(--border-color);
        }
        .med-head h4 {
            font-size: 12.5px;
            font-weight: 600;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 7px;
        }
        .med-head h4 i {
            color: var(--text-muted);
            font-size: 11.5px;
        }
        .med-close {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 12px;
            padding: 4px 7px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .med-close:hover {
            background: var(--bg-hover);
            color: var(--text-primary);
        }
        .med-body {
            display: flex;
            flex-direction: column;
            gap: 13px;
            padding: 14px 16px 4px;
        }
        .med-field {
            display: flex;
            flex-direction: column;
            gap: 5px;
        }
        .med-field > label {
            font-size: 11px;
            font-weight: 500;
            color: var(--text-muted);
            letter-spacing: 0.3px;
        }
        .med-field > label .hint {
            color: var(--text-muted);
            font-weight: 400;
            opacity: 0.75;
            margin-left: 5px;
        }
        .med-field input {
            width: 100%;
            background: var(--bg-input);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            padding: 7px 9px;
            font-size: 12px;
            color: var(--text-primary);
            outline: none;
            font-family: inherit;
            transition: border-color 0.15s ease, box-shadow 0.15s ease;
        }
        .med-field input.mono {
            font-size: 11.5px;
            letter-spacing: 0.02em;
        }
        .med-field input:focus {
            border-color: var(--accent);
            box-shadow: 0 0 0 3px rgba(124, 108, 240, 0.15);
        }
        .med-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
        }
        .med-row .custom-select {
            flex: none;
            width: 100%;
        }
        .med-seg {
            display: flex;
            padding: 3px;
            gap: 3px;
            background: var(--bg-input);
            border: 1px solid var(--border-color);
            border-radius: 7px;
        }
        .med-seg button {
            flex: 1;
            border: none;
            cursor: pointer;
            background: transparent;
            color: var(--text-secondary);
            font-size: 11.5px;
            font-weight: 500;
            font-family: inherit;
            padding: 6px 8px;
            border-radius: 5px;
            transition: all 0.15s ease;
        }
        .med-seg button:disabled {
            opacity: 0.4;
            cursor: not-allowed;
        }
        .med-seg button.on {
            background: var(--accent);
            color: var(--white);
        }
        .med-key-wrap {
            position: relative;
        }
        .med-key-wrap input {
            padding-right: 52px;
        }
        .med-key-toggle {
            position: absolute;
            right: 8px;
            top: 50%;
            transform: translateY(-50%);
            border: none;
            background: transparent;
            cursor: pointer;
            font-size: 10.5px;
            color: var(--text-muted);
            font-family: inherit;
        }
        .med-key-toggle:hover {
            color: var(--accent);
        }
        .med-baseurl-note {
            display: flex;
            align-items: center;
            gap: 7px;
            font-size: 10.5px;
            color: var(--text-muted);
            background: var(--bg-input);
            border: 1px dashed var(--border-color);
            border-radius: 7px;
            padding: 7px 9px;
            line-height: 1.5;
        }
        .med-baseurl-note .mono {
            font-size: 10px;
            color: var(--text-secondary);
            word-break: break-all;
        }
        .med-foot {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 8px;
            padding: 13px 16px 15px;
        }
        .med-foot .med-del {
            margin-right: auto;
        }
        .med-save {
            background: var(--accent);
            border-color: var(--accent);
            color: var(--white);
        }
        .med-save:hover {
            background: var(--accent-light);
            border-color: var(--accent-light);
        }
        .med-save.err {
            background: var(--error);
            border-color: var(--error);
        }
        /* ===== 技能 Tab（只读展示全局技能）===== */
        .skill-name, .mcp-name {
            font-size: 12px;
            font-weight: 600;
            color: var(--text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .skill-src {
            font-size: 10.5px;
            font-weight: 600;
            color: var(--accent);
            background: var(--bg-hover);
            border-radius: 5px;
            padding: 2px 6px;
            flex-shrink: 0;
        }
        .skill-desc {
            font-size: 11px;
            color: var(--text-secondary);
            line-height: 1.6;
        }
        .skill-sub {
            font-size: 10.5px;
            font-weight: 500;
            color: var(--text-muted);
            letter-spacing: 0.3px;
            margin-bottom: 3px;
        }
        .skill-list {
            margin: 0;
            padding-left: 14px;
            font-size: 11px;
            color: var(--text-secondary);
            line-height: 1.7;
        }
        .skill-refs {
            display: flex;
            flex-wrap: wrap;
            gap: 5px;
        }
        .skill-ref {
            font-size: 10.5px;
            color: var(--text-secondary);
            background: var(--bg-input);
            border: 1px solid var(--border-color);
            border-radius: 5px;
            padding: 2px 7px;
        }
        /* 启用/禁用开关 */
        .toggle-switch {
            position: relative;
            width: 40px;
            height: 22px;
            flex-shrink: 0;
        }
        .toggle-switch input {
            opacity: 0;
            width: 0;
            height: 0;
        }
        .toggle-slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: var(--text-muted);
            transition: background-color 0.2s;
            border-radius: 4px;
        }
        .toggle-slider:before {
            position: absolute;
            content: "";
            height: 18px;
            width: 18px;
            left: 2px;
            bottom: 2px;
            background-color: var(--white);
            transition: transform 0.2s;
            border-radius: 3px;
        }
        .toggle-switch input:checked + .toggle-slider {
            background-color: var(--accent);
        }
        .toggle-switch input:checked + .toggle-slider:before {
            transform: translateX(18px);
        }
        .set-card.disabled {
            opacity: 0.5;
        }
        /* ===== 工具管理 Tab ===== */
        .tool-category {
            margin-bottom: 16px;
        }
        .tool-category-title {
            font-size: 11px;
            font-weight: 600;
            color: var(--text-muted);
            letter-spacing: 0.3px;
            padding: 8px 0;
            border-bottom: 1px solid var(--border-color);
            margin-bottom: 8px;
        }
        .tool-card {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 10px 12px;
            border-radius: 8px;
            transition: background 0.15s;
        }
        .tool-card:hover {
            background: var(--bg-hover);
        }
        .tool-card.disabled {
            opacity: 0.5;
        }
        .tool-info {
            display: flex;
            align-items: center;
            gap: 12px;
            flex: 1;
            min-width: 0;
        }
        .tool-icon {
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--bg-input);
            border-radius: 6px;
            font-size: 14px;
            flex-shrink: 0;
        }
        .tool-details {
            flex: 1;
            min-width: 0;
        }
        .tool-name {
            font-size: 12px;
            font-weight: 600;
            color: var(--text-primary);
            margin-bottom: 2px;
        }
        .tool-desc {
            font-size: 11px;
            color: var(--text-muted);
            line-height: 1.4;
        }
        .tool-status {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-shrink: 0;
        }
        .tool-status-text {
            font-size: 10px;
            color: var(--text-muted);
        }
        .tool-footer-info {
            padding: 12px 0 0;
            margin-top: 8px;
            border-top: 1px solid var(--border-color);
            font-size: 11px;
            color: var(--text-muted);
            line-height: 1.6;
        }
        .tool-footer-info strong {
            color: var(--text-secondary);
        }
    `;

    // ===== 内部工具函数 =====
    function esc(s) {
        const div = document.createElement('div');
        div.textContent = String(s == null ? '' : s);
        return div.innerHTML;
    }

    // 属性值转义（esc 基于 textContent，不处理双引号，不能直接用于 value=""）
    function escAttr(s) {
        return String(s || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    }

    // ===== 预设 =====
    // 服务类型：决定对接协议与 baseUrl（当前仅开放按量付费）
    const MODEL_TYPES = [
        { id: 'pay-as-you-go', label: '按量付费' },
        { id: 'token-plan', label: '令牌计划' }
    ];

    // 厂商预设（OpenAI 兼容协议，baseUrl 可手改）
    const MODEL_PRESETS = {
        dashscope: {
            label: '通义千问（阿里云百炼）',
            models: ['qwen3.7-plus', 'qwen-max-latest', 'qwen-plus-latest', 'qwen3-coder-plus'],
            types: ['pay-as-you-go']
        },
        deepseek: {
            label: 'DeepSeek',
            models: ['deepseek-chat', 'deepseek-reasoner'],
            types: ['pay-as-you-go']
        },
        xiaomi: {
            label: '小米MiMo',
            models: ['MiMo'],
            types: ['pay-as-you-go', 'token-plan']
        },
        openai: {
            label: 'OpenAI',
            models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo'],
            types: ['pay-as-you-go']
        }
    };

    // baseUrl 由后端 Java 代码自动管理，前端不再需要 presetBaseUrl

    // ===== 状态 =====
    // defaultIndex 指向默认模型，避免改名后丢失默认标记
    let data = null;
    let loading = false;
    let activeTab = 'models'; // 'models' | 'mcp' | 'skills' | 'tools'

    // ===== DOM 创建 =====
    function mount() {
        const style = document.createElement('style');
        style.id = 'settings-dialog-style';
        style.textContent = CSS;
        document.head.appendChild(style);

        const overlay = document.createElement('div');
        overlay.className = 'settings-overlay';
        overlay.id = 'settingsOverlay';
        overlay.innerHTML = `
            <div class="settings-dialog">
                <div class="settings-dialog-head">
                    <h4><i class="fas fa-sliders-h"></i> 设置</h4>
                    <button title="关闭" onclick="closeSettings()"><i class="fas fa-times"></i></button>
                </div>
                <div class="settings-main">
                    <div class="settings-tabs">
                        <button class="settings-tab active" data-tab="models" onclick="QSettings.switchTab('models')">
                            <i class="fas fa-robot"></i> 模型
                        </button>
                        <button class="settings-tab" data-tab="mcp" onclick="QSettings.switchTab('mcp')">
                            <i class="fas fa-server"></i> MCP
                        </button>
                        <button class="settings-tab" data-tab="skills" onclick="QSettings.switchTab('skills')">
                            <i class="fas fa-shapes"></i> 技能
                        </button>
                        <button class="settings-tab" data-tab="tools" onclick="QSettings.switchTab('tools')">
                            <i class="fas fa-wrench"></i> 工具
                        </button>
                    </div>
                    <div class="settings-content">
                        <div class="settings-body" id="settingsBody">
                            <div class="set-empty"><i class="fas fa-sliders-h"></i><span>加载中...</span></div>
                        </div>
                        <div class="settings-footer">
                            <button class="settings-save" id="settingsSaveBtn" onclick="QSettings.save()">保存配置</button>
                        </div>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(overlay);

        // 模型编辑弹窗容器（叠于设置弹窗之上，内容在打开时动态生成）
        const editOverlay = document.createElement('div');
        editOverlay.className = 'model-edit-overlay';
        editOverlay.id = 'modelEditOverlay';
        editOverlay.addEventListener('click', function (e) {
            if (e.target === this) window.QSettings.closeEditDialog();
        });
        document.body.appendChild(editOverlay);

        // 点击遮罩或按 Esc 关闭（编辑弹窗打开时 Esc 仅关闭弹窗）
        overlay.addEventListener('click', function (e) {
            if (e.target === this) closeSettings();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            if (editOverlay.classList.contains('open')) { window.QSettings.closeEditDialog(); return; }
            closeSettings();
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', mount);
    } else {
        mount();
    }

    // ===== 打开 / 关闭 =====
    // 点击外部关闭所有自定义下拉
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.custom-select')) {
            document.querySelectorAll('.custom-select.open').forEach(el => el.classList.remove('open'));
        }
    });

    window.openSettings = function () {
        document.getElementById('settingsOverlay').classList.add('open');
        if (!data && !loading) load();
    };

    window.closeSettings = function () {
        document.getElementById('settingsOverlay').classList.remove('open');
    };

    // ===== 数据加载 =====
    async function load() {
        loading = true;
        try {
            const res = await fetch('/api/config');
            const cfg = await res.json();
            const models = (cfg.models || []).map(m => ({
                name: m.name || '', provider: m.provider || 'dashscope',
                // 仅开放按量付费，历史配置中的其他类型归一化
                type: MODEL_TYPES.some(t => t.id === m.type) ? m.type : 'pay-as-you-go',
                model: m.model || '', baseUrl: m.baseUrl || '', apiKey: m.apiKey || ''
            }));
            let defaultIndex = models.findIndex(m => m.name === cfg.defaultModel);
            if (defaultIndex < 0) defaultIndex = models.length > 0 ? 0 : -1;
            data = {
                defaultIndex,
                models,
                mcpServers: (cfg.mcpServers || []).map(s => ({
                    name: s.name || '', transport: s.transport || 'streamable-http', url: s.url || '',
                    enabled: s.enabled !== false,
                    headers: Object.entries(s.headers || {}).map(([k, v]) => ({ k, v }))
                })),
                disabledSkills: cfg.disabledSkills || [],
                disabledTools: cfg.disabledTools || [],
                skills: [],
                tools: []
            };
            // 全局技能只读列表（独立接口，不依赖模型配置）
            try {
                const sres = await fetch('/api/config/skills');
                if (sres.ok) data.skills = await sres.json();
            } catch (e) { /* 技能拉取失败不阻断配置展示 */ }
            // 工具列表（独立接口）
            try {
                const tres = await fetch('/api/config/tools');
                if (tres.ok) data.tools = await tres.json();
            } catch (e) { /* 工具拉取失败不阻断配置展示 */ }
            render();
        } catch (e) {
            document.getElementById('settingsBody').innerHTML =
                '<div class="set-empty"><i class="fas fa-exclamation-circle"></i><span>配置加载失败</span></div>';
        } finally {
            loading = false;
        }
    }

    // ===== 渲染 =====
    function render() {
        document.querySelectorAll('.settings-tab').forEach(t =>
            t.classList.toggle('active', t.dataset.tab === activeTab));
        const footer = document.querySelector('.settings-footer');
        if (footer) footer.style.display = '';
        const body = document.getElementById('settingsBody');
        if (activeTab === 'models') body.innerHTML = renderModels();
        else if (activeTab === 'mcp') body.innerHTML = renderMcp();
        else if (activeTab === 'skills') body.innerHTML = renderSkills();
        else body.innerHTML = renderTools();
    }

    function renderSkills() {
        const skills = (data && data.skills) || [];
        if (!skills.length) {
            return '<div class="set-empty"><i class="fas fa-shapes"></i>'
                + '<span>暂无全局技能</span>'
                + '<span class="skill-hint">在 ~/.qualia/skills/ 下创建技能目录后刷新</span></div>';
        }
        const disabledSkills = (data && data.disabledSkills) || [];
        const cards = skills.map(s => {
            const isEnabled = s.enabled !== false && !disabledSkills.includes(s.name);
            const scripts = (s.scripts || []).filter(Boolean)
                .map(x => `<li>${esc(x)}</li>`).join('');
            const refs = (s.references || [])
                .map(x => `<span class="skill-ref">${esc(x)}</span>`).join('');
            return ` 
            <div class="set-card ${isEnabled ? '' : 'disabled'}">
                <div class="set-card-head">
                    <div class="set-card-info">
                        <span class="skill-name">${esc(s.name)}</span>
                        <span class="skill-src">全局</span>
                    </div>
                </div>
                <div class="skill-desc">${esc(s.description) || '（无描述）'}</div>
                ${scripts ? `<div class="set-row"><div class="skill-sub">脚本</div><ul class="skill-list">${scripts}</ul></div>` : ''}
                ${refs ? `<div class="set-row"><div class="skill-sub">附属文档</div><div class="skill-refs">${refs}</div></div>` : ''}
                <div class="set-card-footer">
                    <button class="set-card-del" title="卸载技能" onclick="QSettings.deleteSkill('${escAttr(s.name)}')"><i class="far fa-trash-alt"></i> 卸载</button>
                    <label class="toggle-switch" title="${isEnabled ? '点击禁用' : '点击启用'}">
                        <input type="checkbox" ${isEnabled ? 'checked' : ''} onchange="QSettings.toggleSkill('${escAttr(s.name)}', this.checked)">
                        <span class="toggle-slider"></span>
                    </label>
                </div>
            </div>`;
        }).join('');
        return `<div>${cards}</div>`;
    }

    function renderTools() {
        const tools = (data && data.tools) || [];
        if (!tools.length) {
            return '<div class="set-empty"><i class="fas fa-wrench"></i><span>暂无可用工具</span></div>';
        }
        const disabledTools = (data && data.disabledTools) || [];
        
        // 工具图标映射
        const toolIcons = {
            read: '📖', grep: '🔍', glob: '📂', replace: '✏️', write: '📝',
            delete_file: '🗑️', bash: '💻', web_fetch: '🌍', baidu_search: '🔎', http: '🔗'
        };
        
        // 按类别分组
        const categories = {};
        tools.forEach(t => {
            const cat = t.category || '其他';
            if (!categories[cat]) categories[cat] = [];
            categories[cat].push(t);
        });
        
        const categoryLabels = { file: '📁 文件操作', network: '🌐 网络操作', other: '📦 其他' };
        
        let html = '';
        for (const [cat, catTools] of Object.entries(categories)) {
            html += `<div class="tool-category"><div class="tool-category-title">${categoryLabels[cat] || cat}</div>`;
            html += catTools.map(t => {
                const isEnabled = !disabledTools.includes(t.name);
                const icon = toolIcons[t.name] || '🔧';
                return `
                <div class="tool-card ${isEnabled ? '' : 'disabled'}">
                    <div class="tool-info">
                        <div class="tool-icon">${icon}</div>
                        <div class="tool-details">
                            <div class="tool-name">${esc(t.name)}</div>
                            <div class="tool-desc">${esc(t.description)}</div>
                        </div>
                    </div>
                    <div class="tool-status">
                        <span class="tool-status-text">${isEnabled ? '启用' : '禁用'}</span>
                        <label class="toggle-switch" title="${isEnabled ? '点击禁用' : '点击启用'}">
                            <input type="checkbox" ${isEnabled ? 'checked' : ''} onchange="QSettings.toggleTool('${escAttr(t.name)}', this.checked)">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                </div>`;
            }).join('');
            html += '</div>';
        }
        html += `<div class="tool-footer-info"><strong>提示：</strong>禁用后新对话将不可用，高风险工具建议按需启用。</div>`;
        return `<div>${html}</div>`;
    }

    // 模型预览卡片：厂商标识 → 彩色徽标与渐变色
    const PROVIDER_DOT_CLASS = {
        dashscope: 'mc-dot-dashscope',
        deepseek: 'mc-dot-deepseek',
        xiaomi: 'mc-dot-xiaomi',
        openai: 'mc-dot-openai'
    };

    // apiKey 脱敏：仅保留首 4 位与末 2 位
    function maskKey(k) {
        const s = String(k || '');
        if (!s) return '';
        if (s.includes('*')) return s; // 后端回显的掩码值原样展示
        if (s.length <= 6) return s.slice(0, 2) + '****';
        return s.slice(0, 4) + '****' + s.slice(-2);
    }

    function renderModels() {
        const cards = data.models.map((m, i) => {
            const preset = MODEL_PRESETS[m.provider] || {};
            const providerLabel = preset.label || m.provider || '未知厂商';
            const dotClass = PROVIDER_DOT_CLASS[m.provider] || 'mc-dot-custom';
            const dotLetter = esc((providerLabel.trim()[0] || '?').toUpperCase());
            const typeLabel = (MODEL_TYPES.find(t => t.id === m.type) || { label: m.type }).label;
            const keyTag = m.apiKey
                ? `<span class="mc-tag">API Key ${esc(maskKey(m.apiKey))}</span>`
                : '<span class="mc-tag warn">API Key 未配置</span>';
            const defaultBadge = i === data.defaultIndex
                ? `<span class="mc-badge-default"><i class="fas fa-star"></i> 默认</span>` : '';
            const setDefaultBtn = i === data.defaultIndex ? ''
                : `<button class="mc-btn ghost" onclick="event.stopPropagation(); QSettings.setDefaultModel(${i})">设为默认</button>`;
            return `
            <div class="model-card ${i === data.defaultIndex ? 'is-default' : ''}" onclick="QSettings.openEditDialog(${i})" title="点击编辑">
                <div class="mc-head">
                    <span class="mc-provider"><span class="dot ${dotClass}">${dotLetter}</span>${esc(providerLabel)}</span>
                    ${defaultBadge}
                </div>
                <div class="mc-model-id">${esc(m.model) || '（未设置模型）'}</div>
                <div class="mc-name">${esc(m.name) || '（未命名）'}</div>
                <div class="mc-meta">
                    <span class="mc-tag">${esc(typeLabel)}</span>
                    ${keyTag}
                </div>
                <div class="mc-actions" onclick="event.stopPropagation()">
                    <button class="mc-btn primary" onclick="QSettings.openEditDialog(${i})">编辑</button>
                    ${setDefaultBtn}
                    <button class="mc-btn ghost danger" onclick="QSettings.delModel(${i})">删除</button>
                </div>
            </div>`;
        }).join('');

        const addCard = `
            <div class="model-card add-card" onclick="QSettings.openEditDialog(-1)" title="添加模型">
                <span class="plus"><i class="fas fa-plus"></i></span>
                <p>添加模型</p>
            </div>`;

        return `
            <div class="model-grid">
                ${cards}
                ${addCard}
            </div>
            ${cards ? '' : '<div class="set-env-item">尚未配置模型，点击上方卡片添加</div>'}`;
    }

    // ===== 模型编辑弹窗（点击卡片进入；弹窗内保存仅写回本地草稿，最终持久化仍由「保存配置」完成）=====
    let editIndex = -1;   // -1 表示新增
    let editDraft = null;
    let dlgKeyVisible = false;

    function renderEditDialog() {
        const overlay = document.getElementById('modelEditOverlay');
        if (!overlay || !editDraft) return;
        const m = editDraft;
        const providerIds = Object.keys(MODEL_PRESETS);
        // 配置中出现预设外的厂商标识时附加为选项，避免回显丢失
        const pids = providerIds.includes(m.provider) ? providerIds : providerIds.concat(m.provider);
        const preset = MODEL_PRESETS[m.provider] || {};
        const allowedTypes = preset.types || null;
        const modelOpts = (preset.models || []).map(v => `<option value="${escAttr(v)}"></option>`).join('');
        overlay.innerHTML = `
            <div class="model-edit-dialog">
                <div class="med-head">
                    <h4><i class="fas fa-${editIndex < 0 ? 'plus' : 'pen'}"></i> ${editIndex < 0 ? '添加模型' : '编辑模型'}</h4>
                    <button class="med-close" title="关闭" onclick="QSettings.closeEditDialog()"><i class="fas fa-times"></i></button>
                </div>
                <div class="med-body">
                    <div class="med-row">
                        <div class="med-field">
                            <label>名称</label>
                            <input value="${escAttr(m.name)}" placeholder="自定义名称" oninput="QSettings.dlgSetField('name', this.value)">
                        </div>
                        <div class="med-field">
                            <label>服务类型</label>
                            <div class="med-seg">
                                ${MODEL_TYPES.map(t => {
                                    const disabled = allowedTypes && !allowedTypes.includes(t.id);
                                    return `<button type="button" class="${m.type === t.id ? 'on' : ''}" ${disabled ? 'disabled' : ''} onclick="QSettings.dlgSetType('${t.id}')">${esc(t.label)}</button>`;
                                }).join('')}
                            </div>
                        </div>
                    </div>
                    <div class="med-row">
                        <div class="med-field">
                            <label>厂商</label>
                            <div class="custom-select" data-scope="dlg">
                                <div class="custom-select-trigger" onclick="QSettings.toggleSelect(this)">
                                    <span class="trigger-text">${esc(preset.label || m.provider)}</span>
                                    <i class="fas fa-chevron-down trigger-arrow"></i>
                                </div>
                                <div class="custom-select-dropdown">
                                    ${pids.map(pid => `
                                        <div class="custom-option${m.provider === pid ? ' active' : ''}" data-value="${escAttr(pid)}" onclick="QSettings.selectOption(this)">
                                            <span class="option-text">${esc((MODEL_PRESETS[pid] || {}).label || pid)}</span>
                                            <i class="fas fa-check check-icon"></i>
                                        </div>
                                    `).join('')}
                                </div>
                            </div>
                        </div>
                        <div class="med-field">
                            <label>模型标识</label>
                            <input class="mono" list="dlgModelOpts" value="${escAttr(m.model)}" placeholder="选择或输入模型标识" oninput="QSettings.dlgSetField('model', this.value)">
                            <datalist id="dlgModelOpts">${modelOpts}</datalist>
                        </div>
                    </div>
                    <div class="med-field">
                        <label>API Key<span class="hint">含 **** 时保持原值不变</span></label>
                        <div class="med-key-wrap">
                            <input class="mono" id="dlgKeyInput" type="${dlgKeyVisible ? 'text' : 'password'}" value="${escAttr(m.apiKey)}" placeholder="输入 API Key" oninput="QSettings.dlgSetField('apiKey', this.value)">
                            <button type="button" class="med-key-toggle" onclick="QSettings.dlgToggleKey(this)">${dlgKeyVisible ? '隐藏' : '显示'}</button>
                        </div>
                    </div>
                    <div class="med-baseurl-note">
                        <i class="fas fa-cog"></i> baseUrl 由系统按厂商自动管理
                        ${m.baseUrl ? `<span class="mono">${esc(m.baseUrl)}</span>` : ''}
                    </div>
                </div>
                <div class="med-foot">
                    ${editIndex >= 0 ? '<button type="button" class="mc-btn ghost danger med-del" onclick="QSettings.delInDialog()">删除</button>' : ''}
                    <button type="button" class="mc-btn ghost" onclick="QSettings.closeEditDialog()">取消</button>
                    <button type="button" class="mc-btn primary med-save" id="dlgSaveBtn" onclick="QSettings.saveEditDialog()">保存</button>
                </div>
            </div>`;
    }

    function renderMcp() {
        const transports = ['streamable-http', 'http-sse', 'stdio'];
        const cards = data.mcpServers.map((s, i) => `
            <div class="set-card ${s.enabled === false ? 'disabled' : ''}">
                <div class="set-card-head">
                    <div class="set-card-info">
                        <span class="mcp-name">${esc(s.name) || 'MCP 服务器'}</span>
                    </div>
                </div>
                <div class="set-row"><label>名称</label><input value="${escAttr(s.name)}" placeholder="服务唯一标识" oninput="QSettings.setMcpField(${i},'name',this.value)"></div>
                <div class="set-row"><label>传输方式</label>
                    <select onchange="QSettings.setMcpField(${i},'transport',this.value)">
                        ${transports.map(t => `<option value="${t}" ${s.transport === t ? 'selected' : ''}>${t}</option>`).join('')}
                    </select>
                </div>
                <div class="set-row"><label>服务地址</label><input value="${escAttr(s.url)}" placeholder="http(s)://..." oninput="QSettings.setMcpField(${i},'url',this.value)"></div>
                <div class="set-row"><label>Headers</label>
                    ${s.headers.map((h, j) => `<div class="set-kv">
                        <input value="${escAttr(h.k)}" placeholder="Header 名" oninput="QSettings.setHeaderField(${i},${j},'k',this.value)">
                        <input value="${escAttr(h.v)}" placeholder="Header 值" oninput="QSettings.setHeaderField(${i},${j},'v',this.value)">
                        <button class="set-kv-del" title="删除" onclick="QSettings.delHeader(${i},${j})"><i class="fas fa-times"></i></button>
                    </div>`).join('')}
                    <button class="set-add-btn mini" onclick="QSettings.addHeader(${i})"><i class="fas fa-plus"></i> 添加 Header</button>
                </div>
                <div class="set-card-footer">
                    <button class="set-card-del" title="删除" onclick="QSettings.delMcp(${i})"><i class="far fa-trash-alt"></i> 删除</button>
                    <label class="toggle-switch" title="${s.enabled === false ? '点击启用' : '点击禁用'}">
                        <input type="checkbox" ${s.enabled !== false ? 'checked' : ''} onchange="QSettings.toggleMcp(${i}, this.checked)">
                        <span class="toggle-slider"></span>
                    </label>
                </div>
            </div>`).join('');

        return `
            <div>
                ${cards || '<div class="set-env-item">暂无 MCP 服务器</div>'}
                <button class="set-add-btn" onclick="QSettings.addMcp()"><i class="fas fa-plus"></i> 新增 MCP 服务器</button>
            </div>`;
    }

    // ===== 保存 =====
    async function save() {
        if (!data) return;
        const btn = document.getElementById('settingsSaveBtn');
        // 校验失败时切到对应 Tab 提示
        if (data.models.some(m => !String(m.name || '').trim())) {
            activeTab = 'models';
            render();
            flash(btn, '模型名称不能为空', false);
            return;
        }
        if (data.mcpServers.some(s => !String(s.name || '').trim())) {
            activeTab = 'mcp';
            render();
            flash(btn, 'MCP 名称不能为空', false);
            return;
        }
        const payload = {
            defaultModel: data.defaultIndex >= 0 ? data.models[data.defaultIndex].name.trim() : '',
            // 各字段统一 String() 兜底，避免历史/新增对象缺字段时 trim 崩溃
            models: data.models.map(m => ({
                name: String(m.name || '').trim(), provider: String(m.provider || '').trim(), type: m.type,
                model: String(m.model || '').trim(), baseUrl: String(m.baseUrl || '').trim(), apiKey: String(m.apiKey || '').trim()
            })),
            mcpServers: data.mcpServers.map(s => ({
                name: String(s.name || '').trim(), transport: s.transport, url: String(s.url || '').trim(), enabled: s.enabled !== false,
                headers: (s.headers || []).reduce((o, h) => {
                    const k = String(h.k || '').trim();
                    if (k) o[k] = String(h.v || '');
                    return o;
                }, {})
            })),
            disabledSkills: data.disabledSkills || [],
            disabledTools: data.disabledTools || []
        };
        btn.disabled = true;
        btn.textContent = '保存中...';
        try {
            const res = await fetch('/api/config', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const result = await res.json();
            btn.disabled = false;
            btn.textContent = '保存配置';
            if (result.success) {
                if (window.loadMcpBadge) window.loadMcpBadge(); // 同步顶栏 MCP 数量
                if (window.refreshModelSelector) window.refreshModelSelector(); // 同步输入区模型下拉
                closeSettings();
                load(); // 重新拉取，下次打开时 apiKey 回显为掩码
            } else {
                flash(btn, result.error || '保存失败', false);
            }
        } catch (e) {
            btn.disabled = false;
            flash(btn, '保存失败', false);
        }
    }

    function flash(btn, text, ok) {
        btn.textContent = text;
        btn.classList.toggle('saved', ok);
        setTimeout(() => {
            btn.textContent = '保存配置';
            btn.classList.remove('saved');
        }, 1800);
    }

    // ===== 事件处理命名空间（供生成的 DOM 的 inline handler 引用）=====
    window.QSettings = {
        switchTab(tab) {
            activeTab = tab;
            if (data) render();
            else document.querySelectorAll('.settings-tab').forEach(t =>
                t.classList.toggle('active', t.dataset.tab === tab));
        },
        save,

        // 自定义下拉：切换展开/收起
        toggleSelect(triggerEl) {
            const select = triggerEl.closest('.custom-select');
            // 先关闭其他已打开的下拉
            document.querySelectorAll('.custom-select.open').forEach(el => {
                if (el !== select) el.classList.remove('open');
            });
            select.classList.toggle('open');
        },
        // 自定义下拉：选择选项（当前仅编辑弹窗内的厂商下拉使用）
        selectOption(optionEl) {
            const select = optionEl.closest('.custom-select');
            if (select.dataset.scope === 'dlg') {
                QSettings.dlgSelectProvider(optionEl.dataset.value);
            }
            select.classList.remove('open');
        },
        setDefaultModel(i) { data.defaultIndex = i; render(); },
        delModel(i) {
            data.models.splice(i, 1);
            if (data.defaultIndex === i) data.defaultIndex = data.models.length > 0 ? 0 : -1;
            else if (data.defaultIndex > i) data.defaultIndex--;
            render();
        },

        // ----- 模型编辑弹窗 -----
        // 打开编辑弹窗；i 为 -1 时新增（草稿保存后才写入列表）
        openEditDialog(i) {
            if (!data) return;
            editIndex = i;
            editDraft = i >= 0
                ? Object.assign({}, data.models[i])
                : { name: '', provider: 'dashscope', type: 'pay-as-you-go', model: MODEL_PRESETS.dashscope.models[0], baseUrl: '', apiKey: '' };
            dlgKeyVisible = false;
            renderEditDialog();
            document.getElementById('modelEditOverlay').classList.add('open');
        },
        closeEditDialog() {
            editDraft = null;
            document.getElementById('modelEditOverlay').classList.remove('open');
        },
        dlgSetField(field, value) { if (editDraft) editDraft[field] = value; },
        dlgSetType(v) {
            if (!editDraft) return;
            editDraft.type = v;
            renderEditDialog();
        },
        // 切换厂商：模型回显该厂商首个常用模型，类型越界时归一化
        dlgSelectProvider(v) {
            if (!editDraft) return;
            editDraft.provider = v;
            const p = MODEL_PRESETS[v];
            editDraft.model = p && p.models.length ? p.models[0] : '';
            if (p && p.types && !p.types.includes(editDraft.type)) editDraft.type = p.types[0];
            renderEditDialog();
        },
        dlgToggleKey(btn) {
            const input = document.getElementById('dlgKeyInput');
            if (!input) return;
            dlgKeyVisible = input.type === 'password';
            input.type = dlgKeyVisible ? 'text' : 'password';
            btn.textContent = dlgKeyVisible ? '隐藏' : '显示';
        },
        // 弹窗内保存：草稿写回 data，最终持久化仍由「保存配置」触发
        saveEditDialog() {
            if (!editDraft) return;
            const btn = document.getElementById('dlgSaveBtn');
            if (!String(editDraft.name || '').trim()) {
                btn.classList.add('err');
                btn.textContent = '名称不能为空';
                setTimeout(() => { btn.classList.remove('err'); btn.textContent = '保存'; }, 1600);
                return;
            }
            if (editIndex >= 0) data.models[editIndex] = editDraft;
            else {
                data.models.push(editDraft);
                if (data.defaultIndex < 0) data.defaultIndex = data.models.length - 1;
            }
            QSettings.closeEditDialog();
            render();
        },
        delInDialog() {
            if (editIndex < 0) return;
            const m = data.models[editIndex];
            if (!confirm(`确定要删除模型 "${m.name || m.model}" 吗？`)) return;
            QSettings.delModel(editIndex);
            QSettings.closeEditDialog();
        },

        setMcpField(i, field, value) { data.mcpServers[i][field] = value; },
        toggleMcp(i, enabled) { data.mcpServers[i].enabled = enabled; },
        addMcp() {
            data.mcpServers.push({ name: '', transport: 'streamable-http', url: '', headers: [], enabled: true });
            render();
        },
        delMcp(i) { data.mcpServers.splice(i, 1); render(); },
        toggleSkill(name, enabled) {
            if (!data.disabledSkills) data.disabledSkills = [];
            if (enabled) {
                data.disabledSkills = data.disabledSkills.filter(n => n !== name);
            } else {
                if (!data.disabledSkills.includes(name)) data.disabledSkills.push(name);
            }
        },
        async deleteSkill(name) {
            if (!confirm(`确定要卸载技能 "${name}" 吗？\n\n此操作将删除技能目录，不可恢复。`)) return;
            try {
                const res = await fetch(`/api/config/skills/${encodeURIComponent(name)}`, { method: 'DELETE' });
                const result = await res.json();
                if (result.success) {
                    // 重新加载技能列表
                    const sres = await fetch('/api/config/skills');
                    if (sres.ok) data.skills = await sres.json();
                    // 清理 disabledSkills 中的记录
                    if (data.disabledSkills) {
                        data.disabledSkills = data.disabledSkills.filter(n => n !== name);
                    }
                    render();
                } else {
                    alert(result.error || '删除失败');
                }
            } catch (e) {
                alert('删除失败: ' + e.message);
            }
        },
        toggleTool(name, enabled) {
            if (!data.disabledTools) data.disabledTools = [];
            if (enabled) {
                data.disabledTools = data.disabledTools.filter(n => n !== name);
            } else {
                if (!data.disabledTools.includes(name)) data.disabledTools.push(name);
            }
        },
        setHeaderField(i, j, field, value) { data.mcpServers[i].headers[j][field] = value; },
        addHeader(i) { data.mcpServers[i].headers.push({ k: '', v: '' }); render(); },
        delHeader(i, j) { data.mcpServers[i].headers.splice(j, 1); render(); }
    };
})();
