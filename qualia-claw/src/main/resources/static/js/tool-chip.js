/**
 * ToolChip —— 聊天流内联工具 chip 组件（自包含：样式自注入，无外部依赖）
 *
 * 职责：
 *   1. 根据 AgentStep(ACTION) 创建工具 chip（图标 + 动词 + 参数摘要 + 状态）
 *   2. 点击 chip 在 chips 容器下方原地展开详情，按工具类型差异化渲染：
 *      - read / write     文件视角（路径头 + 代码块）
 *      - edit             对照视角（旧文本 / 新文本 diff 块）
 *      - bash             终端视角（$ 命令 + 输出）
 *      - grep / glob      搜索视角（模式头 + 匹配结果）
 *      - skill-*          技能视角（loader 摘要 + 浏览器详情页；runner/reader 头部行 + 内容块）
 *      - 其他             参数 JSON + 输出
 *   3. 状态流转：run → ok / err，输出到达时若详情展开则自动刷新
 *
 * 对外 API：
 *   ToolChip.create(step, idx, stepsRef, live)  -> HTMLButtonElement
 *   ToolChip.resolveRunning(scopeEl, ok)        将 scope 内运行中的 chip 置为完成/失败
 *   ToolChip.resolveAll(scopeEl)                将 scope 内所有运行中的 chip 置为完成
 */
window.ToolChip = (function () {
    'use strict';

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

    function baseName(p) {
        if (!p) return '';
        const norm = String(p).replace(/\\/g, '/');
        return norm.split('/').filter(Boolean).pop() || norm;
    }

    // ACTION 步骤对应的观察输出：其后第一个 OBSERVATION，遇到下一个 ACTION 则停止
    function findObservation(steps, idx) {
        for (let j = idx + 1; j < steps.length; j++) {
            if (steps[j].stepType === 'OBSERVATION') return steps[j].content || '';
            if (steps[j].stepType === 'ACTION') break;
        }
        return null;
    }

    const OUT_LIMIT = 4000;

    // ===== chip 元信息（图标 / 动词 / 参数摘要）=====
    const META = {
        read:  { icon: 'fa-magnifying-glass', verb: '读取', arg: a => baseName(a.path || a.file_path) },
        write: { icon: 'fa-file-circle-plus', verb: '写入', arg: a => baseName(a.path || a.file_path) },
        edit:  { icon: 'fa-pen',              verb: '修改', arg: a => baseName(a.path || a.file_path) },
        delete:{ icon: 'fa-trash-can',        verb: '删除', arg: a => baseName(a.path || a.file_path) },
        bash:  { icon: 'fa-terminal',         verb: '执行', arg: a => truncate(a.command || '', 36) },
        grep:  { icon: 'fa-magnifying-glass', verb: '搜索', arg: a => a.pattern || a.regex || '' },
        glob:  { icon: 'fa-magnifying-glass', verb: '搜索', arg: a => a.pattern || '' },
        'skill-loader':           { icon: 'fa-shapes',    verb: '加载技能', arg: a => a.skill_name || '' },
        'skill-script-runner':    { icon: 'fa-bolt',      verb: '运行脚本', arg: a => shortScriptName(a.script_name, a.skill_name) },
        'skill-reference-reader': { icon: 'fa-book-open', verb: '查阅文档', arg: a => a.file_name || '' },
        'skill-selector':         { icon: 'fa-list',      verb: '查询技能', arg: () => '' }
    };
    const META_FALLBACK = { icon: 'fa-wrench', verb: '', arg: () => '' };

    function metaOf(toolName) {
        if (META[toolName]) return META[toolName];
        // 网页搜索/抓取工具：元信息与详情由独立模块提供
        if (window.WebSearchDetail && WebSearchDetail.matches(toolName)) return WebSearchDetail.meta();
        if (window.WebFetchDetail && WebFetchDetail.matches(toolName)) return WebFetchDetail.meta();
        return Object.assign({}, META_FALLBACK, { verb: toolName || '工具' });
    }

    // ===== 详情渲染片段 =====
    function fileHead(args, badges) {
        const path = args.path || args.file_path || '';
        const badgeHtml = (badges || []).filter(Boolean)
            .map(b => `<span class="tc-badge">${esc(b)}</span>`).join('');
        return `
            <div class="tc-file-head">
                <i class="fas fa-file-lines"></i>
                <span class="tc-path">${esc(path || '（未知路径）')}</span>
                ${badgeHtml}
            </div>
        `;
    }

    function codeBlock(text) {
        return `<div class="tc-code">${esc(truncate(text, OUT_LIMIT))}</div>`;
    }

    // read 输出格式为 "%6d→内容"（每行带行号前缀），解析后渲染为编辑器风格（行号列 + 代码列）；
    // 解析失败（如错误消息）时回退普通代码块
    function editorBlock(text) {
        // 后端 %n 在 Windows 下为 \r\n，先归一化换行
        const lines = truncate(text, OUT_LIMIT).replace(/\r\n?/g, '\n').split('\n');
        if (lines.length && lines[lines.length - 1] === '') lines.pop();
        const parsed = [];
        for (const line of lines) {
            const m = line.match(/^\s*(\d+)→(.*)$/);
            if (!m) return codeBlock(text);
            parsed.push({ num: m[1], txt: m[2] });
        }
        if (!parsed.length) return codeBlock(text);
        const numWidth = parsed[parsed.length - 1].num.length + 1;
        const rows = parsed.map(l => `
            <div class="tc-ed-line"><span class="tc-ed-num" style="width:${numWidth}ch">${l.num}</span><span class="tc-ed-txt">${esc(l.txt) || ' '}</span></div>
        `).join('');
        return `<div class="tc-editor">${rows}</div>`;
    }

    function resultLine(output) {
        return `
            <div class="tc-result">
                <i class="fas fa-check"></i>
                <span>${esc(truncate(output, 200))}</span>
            </div>
        `;
    }

    function runningLine() {
        return `
            <div class="tc-running">
                <i class="fas fa-circle-notch fa-spin"></i>
                <span>执行中…</span>
            </div>
        `;
    }

    // ===== 按工具类型的详情渲染器：(args, output) -> html，output 为 null 表示执行中 =====
    const RENDERERS = {
        read(args, output) {
            const range = (args.begin || args.end)
                ? `L${args.begin || 1}${args.end ? '-' + args.end : '+'}` : '';
            let html = fileHead(args, [range]);
            html += output === null ? runningLine() : editorBlock(output);
            return html;
        },

        write(args, output) {
            const modeLabel = { overwrite: '覆盖', append: '追加', insert: '插入' }[args.mode] || '';
            let html = fileHead(args, [modeLabel]);
            if (args.content) html += codeBlock(args.content);
            html += output === null ? runningLine() : resultLine(output);
            return html;
        },

        edit(args, output) {
            let html = fileHead(args, [args.replace_all ? '全部替换' : '']);
            html += `
                <div class="tc-diff">
                    <div class="tc-diff-del">${esc(truncate(args.old_text || '', OUT_LIMIT))}</div>
                    <div class="tc-diff-ins">${esc(truncate(args.new_text || '', OUT_LIMIT))}</div>
                </div>
            `;
            html += output === null ? runningLine() : resultLine(output);
            return html;
        },

        delete(args, output) {
            let html = fileHead(args, ['删除']);
            html += output === null ? runningLine() : resultLine(output);
            return html;
        },

        bash(args, output) {
            return `
                <div class="tc-term">
                    <div class="tc-term-cmd">${esc(args.command || '')}</div>
                    ${output === null
                        ? `<div class="tc-term-out tc-term-wait"><i class="fas fa-circle-notch fa-spin"></i> 执行中…</div>`
                        : (output ? `<div class="tc-term-out">${esc(truncate(output, OUT_LIMIT))}</div>` : '')}
                </div>
            `;
        },

        grep(args, output) {
            return searchDetail(args.pattern || args.regex, [args.path, args.glob], output);
        },

        glob(args, output) {
            return searchDetail(args.pattern, [args.path], output);
        },

        // 技能三件套：统一解剖结构 = 头部行 + 参数行(仅 runner) + 内容块 + 资源行(仅 loader)
        // loader 只展示摘要（名字 / 描述 / 脚本与文档），完整技能说明通过「查看详情」在浏览器 Tab 打开
        'skill-loader'(args, output) {
            let html = skillHead('fa-shapes', args.skill_name);
            if (output === null) return html + runningLine();
            const parsed = parseSkillLoad(output, args.skill_name);
            if (!parsed) return html + skillBody(output);
            if (parsed.desc) html += `<div class="sk-desc">${esc(parsed.desc)}</div>`;
            if (parsed.meta.length) {
                html += `<div class="sk-meta">${parsed.meta.map(m =>
                    `<span><i class="fas ${m.icon}"></i>${esc(m.name)}</span>`).join('')}</div>`;
            }
            const id = SKILL_STORE.push(Object.assign({ name: args.skill_name || '' }, parsed)) - 1;
            html += `<button class="sk-more" onclick="ToolChip.openSkillDetail(${id})">`
                + `<i class="fas fa-angles-right"></i> 查看技能详情</button>`;
            return html;
        },

        'skill-script-runner'(args, output) {
            let html = skillHead('fa-bolt', shortScriptName(args.script_name, args.skill_name), args.skill_name);
            html += skillArgsRow(args.arguments);
            html += output === null ? runningLine() : skillFold(output);
            return html;
        },

        'skill-reference-reader'(args, output) {
            let html = skillHead('fa-book-open', args.file_name, args.skill_name);
            html += output === null ? runningLine() : skillBody(output);
            return html;
        },

        'skill-selector'(args, output) {
            let html = `<div class="sk-head"><i class="fas fa-list"></i><span class="sk-name">可用技能列表</span></div>`;
            if (output === null) return html + runningLine();
            if (!output || output === '当前没有可用的技能。') {
                return html + `<div class="sk-body">当前没有可用的技能。</div>`;
            }
            try {
                const skills = JSON.parse(output);
                if (Array.isArray(skills) && skills.length > 0) {
                    html += `<div class="sk-skills">`;
                    skills.forEach(s => {
                        html += `<div class="sk-skill-item">
                            <span class="sk-skill-icon"><i class="fas fa-shapes"></i></span>
                            <div class="sk-skill-info">
                                <span class="sk-skill-name">${esc(s.name || '')}</span>
                                ${s.description ? `<span class="sk-skill-desc">${esc(s.description)}</span>` : ''}
                            </div>
                        </div>`;
                    });
                    html += `</div>`;
                } else {
                    html += skillBody(output);
                }
            } catch (e) {
                html += skillBody(output);
            }
            return html;
        },

        fallback(toolName, args, output) {
            const argJson = args && Object.keys(args).length ? JSON.stringify(args, null, 2) : '（无参数）';
            let html = `
                <div class="tc-sec">
                    <div class="tc-label">${esc(toolName || '工具')} · 参数</div>
                    ${codeBlock(argJson)}
                </div>
            `;
            html += `<div class="tc-sec"><div class="tc-label">输出</div>${output === null ? runningLine() : codeBlock(output)}</div>`;
            return html;
        }
    };

    function searchDetail(pattern, badges, output) {
        const badgeHtml = (badges || []).filter(Boolean)
            .map(b => `<span class="tc-badge">${esc(b)}</span>`).join('');
        let html = `
            <div class="tc-search-head">
                <i class="fas fa-magnifying-glass"></i>
                <code class="tc-pattern">${esc(pattern || '')}</code>
                ${badgeHtml}
            </div>
        `;
        html += output === null ? runningLine() : codeBlock(output || '（无匹配）');
        return html;
    }

    // ===== 技能工具渲染片段 =====
    // 脚本注册名为 script_<技能名>_<文件基名>（框架内部命名），展示时剥离前缀还原短名
    function shortScriptName(name, skillName) {
        if (!name) return '';
        const prefix = 'script_' + String(skillName || '').toLowerCase().replace(/[\s\-]+/g, '_') + '_';
        return String(name).startsWith(prefix) ? String(name).slice(prefix.length) : String(name);
    }

    // 头部行：图标 + 主体名，右侧灰字标注所属技能
    function skillHead(icon, name, src) {
        return `
            <div class="sk-head">
                <i class="fas ${icon}"></i>
                <span class="sk-name">${esc(name || '')}</span>
                ${src ? `<span class="sk-src">${esc(src)}</span>` : ''}
            </div>
        `;
    }

    // 内容块：唯一内容容器，错误输出（错误：开头）转错误色
    function skillBody(text) {
        const err = /^错误[:：]/.test(String(text || '').trim());
        return `<div class="sk-body${err ? ' err' : ''}">${esc(truncate(text, OUT_LIMIT))}</div>`;
    }

    // 折叠输出：脚本运行结果默认收起，点「运行结果」行展开；出错时默认展开不遮错误
    function skillFold(text) {
        const err = /^错误[:：]/.test(String(text || '').trim());
        return `
            <details class="sk-fold"${err ? ' open' : ''}>
                <summary><i class="fas fa-chevron-right"></i>运行结果${err ? '<span class="sk-fold-err">出错</span>' : ''}</summary>
                ${skillBody(text)}
            </details>
        `;
    }

    // 参数行：脚本入参为 JSON 字符串，解析为单行 key value 对；解析失败按原文展示
    function skillArgsRow(argsJson) {
        if (!argsJson) return '';
        let pairs;
        try {
            const obj = JSON.parse(argsJson);
            pairs = Object.entries(obj).map(([k, v]) =>
                `<span><b>${esc(k)}</b>${esc(typeof v === 'object' ? JSON.stringify(v) : v)}</span>`);
        } catch (e) {
            pairs = [`<span>${esc(truncate(argsJson, 200))}</span>`];
        }
        return pairs.length ? `<div class="sk-args">${pairs.join('')}</div>` : '';
    }

    // skill-loader 输出为三段结构（【技能说明】/【可用脚本】/【附属文档】），解析失败返回 null 回退整段展示
    function parseSkillLoad(output, skillName) {
        const text = String(output || '').replace(/\r\n?/g, '\n');
        const docM = text.match(/【技能说明】\n([\s\S]*?)(?=\n*【|$)/);
        if (!docM) return null;
        const doc = docM[1].trim();
        const scripts = [];
        const scriptsM = text.match(/【可用脚本】\n([\s\S]*?)(?=\n*【|$)/);
        if (scriptsM) {
            scriptsM[1].split('\n').forEach(line => {
                const m = line.match(/^- ([^:：]+)[:：]?\s*(.*)$/);
                if (m) scripts.push({ name: shortScriptName(m[1].trim(), skillName), desc: m[2].trim() });
            });
        }
        const refs = [];
        const refsM = text.match(/【附属文档】\n([\s\S]*?)(?=\n*【|$)/);
        if (refsM) {
            refsM[1].split('\n').forEach(line => {
                const m = line.match(/^- (.+)/);
                if (m) refs.push(m[1].trim());
            });
        }
        const meta = scripts.map(s => ({ icon: 'fa-bolt', name: s.name }))
            .concat(refs.map(r => ({ icon: 'fa-book', name: r })));
        return { doc, desc: skillDesc(doc), meta, scripts, refs };
    }

    // ===== 技能详情页（工作区「浏览器」Tab）=====
    // 「查看技能详情」用的结果暂存（下标即 openSkillDetail 的 id）
    const SKILL_STORE = [];

    function openSkillDetail(id) {
        const rec = SKILL_STORE[id];
        if (!rec || typeof window.openHtmlPreview !== 'function') return;
        window.openHtmlPreview(buildSkillPage(rec));
    }

    // 技能说明为 skill.md 原文（可能带 YAML frontmatter），摘要优先取 frontmatter description
    function stripFrontmatter(text) {
        return String(text || '').replace(/^---\s*\n[\s\S]*?\n---\s*\n?/, '');
    }

    function skillDesc(doc) {
        const fm = String(doc || '').match(/^---\s*\n([\s\S]*?)\n---/);
        if (fm) {
            const m = fm[1].match(/^description:\s*['"]?(.+?)['"]?\s*$/m);
            if (m) return m[1].trim();
        }
        for (const line of stripFrontmatter(doc).split('\n')) {
            const t = line.trim();
            if (t && !t.startsWith('#')) return t;
        }
        return '';
    }

    // 轻量 Markdown 渲染（标题 / 列表 / 段落 / 行内代码与加粗），仅供技能详情页使用
    function mdInline(s) {
        return esc(s)
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    }

    function mdToHtml(text) {
        const lines = stripFrontmatter(text).replace(/\r\n?/g, '\n').split('\n');
        const out = [];
        let list = null;
        const closeList = () => { if (list) { out.push(`</${list}>`); list = null; } };
        for (const raw of lines) {
            const line = raw.trim();
            const h = line.match(/^(#{1,4})\s+(.*)$/);
            if (h) { closeList(); const lv = h[1].length + 1; out.push(`<h${lv}>${mdInline(h[2])}</h${lv}>`); continue; }
            const ul = line.match(/^[-*]\s+(.*)$/);
            const ol = line.match(/^\d+[.)]\s+(.*)$/);
            if (ul || ol) {
                const type = ul ? 'ul' : 'ol';
                if (list !== type) { closeList(); out.push(`<${type}>`); list = type; }
                out.push(`<li>${mdInline((ul || ol)[1])}</li>`);
                continue;
            }
            if (!line) { closeList(); continue; }
            // 列表项续行（原文带缩进）并入上一项
            if (list && /^\s/.test(raw) && out.length) {
                out[out.length - 1] = out[out.length - 1].replace(/<\/li>$/, ' ' + mdInline(line) + '</li>');
                continue;
            }
            closeList();
            out.push(`<p>${mdInline(line)}</p>`);
        }
        closeList();
        return out.join('');
    }

    // 自包含 HTML 页面，配色跟随当前明暗主题（与网页搜索结果页同一套变量与切换机制）
    function buildSkillPage(rec) {
        const light = document.body.classList.contains('light-theme');
        const scriptRows = (rec.scripts || []).map(s => `
            <div class="res-item">
                <span class="res-icon">⚡</span>
                <div class="res-main">
                    <span class="res-name">${esc(s.name)}</span>
                    ${s.desc ? `<span class="res-desc">${esc(s.desc)}</span>` : ''}
                </div>
            </div>`).join('');
        const refRows = (rec.refs || []).map(r => `
            <div class="res-item">
                <span class="res-icon">📄</span>
                <div class="res-main"><span class="res-name">${esc(r)}</span></div>
            </div>`).join('');
        const metaParts = [`<span class="src">技能</span>`,
            `<span>${(rec.scripts || []).length} 个脚本</span>`,
            `<span>${(rec.refs || []).length} 个附属文档</span>`].join('<span class="sep">·</span>');
        return `<!DOCTYPE html>
<html lang="zh-CN"${light ? ' class="light"' : ''}>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
    :root {
        color-scheme: dark;
        --bg: #0d0f16; --text: #e4e9f2; --sub: #8f97ae; --muted: #4f566b;
        --border: rgba(255, 255, 255, 0.06); --hover: rgba(255, 255, 255, 0.04);
        --accent: #b3a8ff; --icon-bg: rgba(124, 108, 240, 0.14); --code-bg: rgba(255, 255, 255, 0.06); --scrollbar: #2e3546;
    }
    /* 主题类同时挂在 html 与 body 上：视口滚动条属于 html，变量必须在 html 层级生效 */
    .light {
        color-scheme: light;
        --bg: #ffffff; --text: #1e232e; --sub: #5f6883; --muted: #8f97ae;
        --border: rgba(0, 0, 0, 0.07); --hover: rgba(0, 0, 0, 0.04);
        --accent: #7c6cf0; --icon-bg: rgba(124, 108, 240, 0.10); --code-bg: rgba(0, 0, 0, 0.05); --scrollbar: #cbd0db;
    }
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html { background: var(--bg); }
    ::-webkit-scrollbar { width: 5px; height: 5px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: var(--scrollbar); border-radius: 8px; }
    body { background: var(--bg); color: var(--text); font-family: -apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif; transition: background 0.2s; }
    .skill-head { position: sticky; top: 0; background: var(--bg); padding: 18px 28px 0; z-index: 10; }
    .skill-head-inner { max-width: 680px; margin: 0 auto; padding-bottom: 14px; border-bottom: 1px solid var(--border); }
    .title-row { display: flex; align-items: center; gap: 10px; }
    .title-icon { width: 30px; height: 30px; border-radius: 8px; background: var(--icon-bg); display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0; }
    .title-text { font-size: 17px; font-weight: 700; letter-spacing: 0.2px; }
    .desc-row { margin-top: 8px; padding-left: 40px; font-size: 12.5px; line-height: 1.7; color: var(--sub); }
    .meta-row { display: flex; align-items: center; gap: 6px; margin-top: 8px; padding-left: 40px; font-size: 11px; color: var(--muted); }
    .meta-row .sep { color: var(--border); }
    .meta-row .src { color: var(--accent); font-weight: 600; }
    .content { max-width: 680px; margin: 0 auto; padding: 8px 28px 24px; }
    .sec-title { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.8px; color: var(--muted); margin: 26px 0 10px; }
    /* 技能说明（Markdown） */
    .md { font-size: 13.5px; line-height: 1.8; color: var(--text); }
    .md h2 { font-size: 16px; margin: 20px 0 8px; }
    .md h3 { font-size: 14px; margin: 18px 0 6px; }
    .md h4, .md h5 { font-size: 13px; margin: 14px 0 6px; color: var(--sub); }
    .md p { margin: 8px 0; }
    .md ul, .md ol { margin: 8px 0; padding-left: 22px; }
    .md li { margin: 4px 0; }
    .md code { font-family: Consolas, monospace; font-size: 12px; background: var(--code-bg); padding: 1px 5px; border-radius: 4px; }
    /* 脚本 / 文档资源行 */
    .res-item { display: flex; align-items: flex-start; gap: 10px; padding: 11px 12px; margin: 0 -12px; border-radius: 10px; transition: background 0.15s; }
    .res-item + .res-item { border-top: 1px solid var(--border); }
    .res-item:hover { background: var(--hover); }
    .res-icon { font-size: 13px; line-height: 1.5; flex-shrink: 0; }
    .res-main { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
    .res-name { font-family: Consolas, monospace; font-size: 13px; font-weight: 600; }
    .res-desc { font-size: 12px; line-height: 1.6; color: var(--sub); }
    .end-tip { max-width: 680px; margin: 0 auto; padding: 16px 28px 40px; text-align: center; font-size: 11.5px; color: var(--muted); }
    .end-tip::before { content: '— '; color: var(--border); }
    .end-tip::after { content: ' —'; color: var(--border); }
</style>
</head>
<body${light ? ' class="light"' : ''}>
    <div class="skill-head">
        <div class="skill-head-inner">
            <div class="title-row"><span class="title-icon">🧩</span><span class="title-text">${esc(rec.name)}</span></div>
            ${rec.desc ? `<div class="desc-row">${esc(rec.desc)}</div>` : ''}
            <div class="meta-row">${metaParts}</div>
        </div>
    </div>
    <div class="content">
        <div class="sec-title">技能说明</div>
        <div class="md">${mdToHtml(rec.doc)}</div>
        ${scriptRows ? `<div class="sec-title">可用脚本</div>${scriptRows}` : ''}
        ${refRows ? `<div class="sec-title">附属文档</div>${refRows}` : ''}
    </div>
    <div class="end-tip">技能详情</div>
<script>
    window.addEventListener('message', function (e) {
        if (e.data && e.data.type === 'qualia-theme') {
            document.documentElement.classList.toggle('light', !!e.data.light);
            document.body.classList.toggle('light', !!e.data.light);
        }
    });
</script>
</body>
</html>`;
    }

    // ===== 详情渲染与展开 =====
    // 思考文本已由聊天流内联展示（.act-thought），详情只呈现参数与输出
    function renderDetail(detail, step, idx, stepsRef) {
        const args = step.toolArgs || {};
        const output = findObservation(stepsRef, idx);
        const renderer = RENDERERS[step.toolName];
        if (renderer) {
            detail.innerHTML = renderer(args, output);
        } else if (window.WebSearchDetail && WebSearchDetail.matches(step.toolName)) {
            detail.innerHTML = WebSearchDetail.render(step.toolName, args, output);
        } else if (window.WebFetchDetail && WebFetchDetail.matches(step.toolName)) {
            detail.innerHTML = WebFetchDetail.render(step.toolName, args, output);
        } else {
            detail.innerHTML = RENDERERS.fallback(step.toolName, args, output);
        }
        detail.dataset.idx = idx;
    }

    // 每个 chip 拥有独立详情块（挂在所属 chips 组下方，按 idx 保序），互不影响
    function detailOf(chip, createIfMissing) {
        const chipsEl = chip.parentElement;
        if (!chipsEl) return null;
        let sib = chipsEl.nextElementSibling;
        while (sib && sib.classList.contains('tc-detail')) {
            if (sib.dataset.idx === chip.dataset.idx) return sib;
            sib = sib.nextElementSibling;
        }
        if (!createIfMissing) return null;
        const d = document.createElement('div');
        d.className = 'tc-detail';
        d.dataset.idx = chip.dataset.idx;
        let anchor = chipsEl;
        let n = chipsEl.nextElementSibling;
        while (n && n.classList.contains('tc-detail') && Number(n.dataset.idx) < Number(chip.dataset.idx)) {
            anchor = n;
            n = n.nextElementSibling;
        }
        anchor.after(d);
        return d;
    }

    function toggleDetail(chip, step, idx, stepsRef) {
        const detail = detailOf(chip, true);
        console.log('[ToolChip] toggleDetail', {
            idx,
            toolName: step.toolName,
            chipActive: chip.classList.contains('active'),
            detailExists: !!detail,
            detailOpen: detail ? detail.classList.contains('open') : null,
            detailDisplay: detail ? getComputedStyle(detail).display : null,
            parentTag: chip.parentElement ? chip.parentElement.tagName : null
        });
        if (detail.classList.contains('open')) {
            detail.classList.remove('open');
            chip.classList.remove('active');
            return;
        }
        chip.classList.add('active');
        renderDetail(detail, step, idx, stepsRef);
        detail.classList.add('open');
        console.log('[ToolChip] afterToggle', {
            detailOpen: detail.classList.contains('open'),
            detailDisplay: getComputedStyle(detail).display,
            detailChildren: detail.children.length,
            detailHTML: detail.innerHTML.substring(0, 200),
            detailParent: detail.parentElement ? detail.parentElement.className : null,
            chipActive: chip.classList.contains('active')
        });
    }

    // 输出到达后：若该 chip 的详情正展开，刷新为最终内容
    function refreshOpenDetail(chip) {
        const detail = detailOf(chip, false);
        if (detail && detail.classList.contains('open')) {
            renderDetail(detail, chip._step, Number(chip.dataset.idx), chip._stepsRef);
        }
    }

    // ===== 对外 API =====
    function create(step, idx, stepsRef, live) {
        const meta = metaOf(step.toolName);
        let argText = '';
        try { argText = meta.arg(step.toolArgs || {}) || ''; } catch (e) {}

        const chip = document.createElement('button');
        chip.className = 'tool-chip';
        chip.dataset.idx = idx;
        chip._step = step;
        chip._stepsRef = stepsRef;
        chip.innerHTML = `
            <i class="chip-ico fas ${meta.icon}"></i>${esc(meta.verb)}
            ${argText ? `<span class="chip-arg">${esc(argText)}</span>` : ''}
            <span class="chip-st ${live ? 'run' : 'ok'}">${live ? '<i class="fas fa-circle-notch fa-spin"></i>' : '<i class="fas fa-check"></i>'}</span>
        `;
        chip.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleDetail(chip, step, idx, stepsRef);
        });
        return chip;
    }

    function settle(st, ok) {
        st.className = 'chip-st ' + (ok ? 'ok' : 'err');
        st.innerHTML = ok ? '<i class="fas fa-check"></i>' : '<i class="fas fa-xmark"></i>';
        const chip = st.closest('.tool-chip');
        if (chip) refreshOpenDetail(chip);
    }

    function resolveRunning(scopeEl, ok) {
        const st = scopeEl.querySelector('.chip-st.run');
        if (st) settle(st, ok !== false);
    }

    function resolveAll(scopeEl) {
        scopeEl.querySelectorAll('.chip-st.run').forEach(st => settle(st, true));
    }

    // 展开该 chip 的详情（默认展开时由宿主在创建后调用）
    function open(chip) {
        if (chip.classList.contains('active')) return;
        toggleDetail(chip, chip._step, Number(chip.dataset.idx), chip._stepsRef);
    }

    // ===== 样式自注入（依赖页面已有的 CSS 变量）=====
    const STYLE = `
        /* chip 本体 */
        .tool-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-size: 11px;
            font-weight: 500;
            font-family: 'JetBrains Mono', monospace;
            color: var(--text-secondary);
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            padding: 4px 10px;
            border-radius: 7px;
            cursor: pointer;
            transition: all 0.15s;
            max-width: 100%;
            white-space: nowrap;
        }
        .tool-chip:hover,
        .tool-chip.active {
            border-color: var(--border-active);
            color: var(--text-primary);
        }
        .tool-chip .chip-ico {
            font-size: 10.5px;
            color: var(--accent-light);
            flex-shrink: 0;
        }
        .tool-chip .chip-arg {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            min-width: 0;
        }
        .tool-chip .chip-st { font-size: 10px; flex-shrink: 0; }
        .tool-chip .chip-st.ok { color: #34d399; }
        .tool-chip .chip-st.run { color: #60a5fa; }
        .tool-chip .chip-st.err { color: #f87171; }
        /* 浅色主题下状态色加深，保证白底对比度 */
        body.light-theme .tool-chip .chip-st.ok { color: #059669; }
        body.light-theme .tool-chip .chip-st.run { color: #2563eb; }
        body.light-theme .tool-chip .chip-st.err { color: #dc2626; }

        /* 详情容器 */
        .tc-detail {
            display: none;
            background: var(--bg-hover);
            border: 1px solid var(--border-color);
            border-radius: 9px;
            padding: 11px 13px;
        }
        .tc-detail.open { display: flex; flex-direction: column; gap: 9px; }

        /* 文件头 / 搜索头 */
        .tc-file-head, .tc-search-head {
            display: flex;
            align-items: center;
            gap: 7px;
            min-width: 0;
        }
        .tc-file-head i, .tc-search-head i {
            font-size: 10.5px;
            color: var(--accent-light);
            flex-shrink: 0;
        }
        .tc-path {
            font-family: 'JetBrains Mono', monospace;
            font-size: 11px;
            color: var(--text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            direction: rtl;
            text-align: left;
        }
        .tc-pattern {
            font-family: 'JetBrains Mono', monospace;
            font-size: 11px;
            color: var(--text-primary);
            background: var(--bg-inline-code);
            padding: 2px 6px;
            border-radius: 5px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .tc-badge {
            flex-shrink: 0;
            font-size: 10px;
            font-weight: 600;
            font-family: 'JetBrains Mono', monospace;
            color: var(--text-muted);
            border: 1px solid var(--border-color);
            padding: 1px 6px;
            border-radius: 100px;
        }

        /* 代码 / 输出块（跟随 --bg-codeblock：暗色深底、浅色浅底） */
        .tc-code {
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            line-height: 1.65;
            white-space: pre-wrap;
            word-break: break-word;
            color: var(--text-code);
            background: var(--bg-codeblock);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            padding: 9px 11px;
            max-height: 234px;
            overflow-y: auto;
        }

        /* read 编辑器风格块（行号列 + 代码列） */
        .tc-editor {
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            line-height: 1.65;
            background: var(--bg-codeblock);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            padding: 7px 0;
            max-height: 234px;
            overflow: auto;
        }
        .tc-editor::-webkit-scrollbar-corner {
            background: var(--bg-codeblock);
        }
        .tc-ed-line {
            display: flex;
            min-width: max-content;
        }
        .tc-ed-line:hover { background: rgba(255, 255, 255, 0.04); }
        .tc-ed-num {
            flex-shrink: 0;
            text-align: right;
            padding: 0 9px 0 5px;
            margin-right: 9px;
            color: var(--text-muted);
            border-right: 1px solid var(--border-color);
            user-select: none;
            position: sticky;
            left: 0;
            background: var(--bg-codeblock);
        }
        .tc-ed-txt {
            color: var(--text-code);
            white-space: pre;
            padding-right: 11px;
        }
        /* 浅色主题：hover 改黑色基调（文字色已由 --text-code / --text-muted 随主题切换） */
        body.light-theme .tc-ed-line:hover { background: rgba(0, 0, 0, 0.04); }

        /* edit 对照块 */
        .tc-diff {
            display: flex;
            flex-direction: column;
            border: 1px solid var(--border-color);
            border-radius: 7px;
            overflow: hidden;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            line-height: 1.65;
        }
        .tc-diff-del, .tc-diff-ins {
            padding: 7px 11px 7px 23px;
            white-space: pre-wrap;
            word-break: break-word;
            position: relative;
            max-height: 162px;
            overflow-y: auto;
        }
        .tc-diff-del::before, .tc-diff-ins::before {
            position: absolute;
            left: 10px;
            font-weight: 700;
        }
        .tc-diff-del {
            background: rgba(248, 113, 113, 0.08);
            color: #e89b9b;
        }
        .tc-diff-del::before { content: '-'; color: #f87171; }
        .tc-diff-ins {
            background: rgba(52, 211, 153, 0.08);
            color: #8fd9bb;
            border-top: 1px solid var(--border-color);
        }
        .tc-diff-ins::before { content: '+'; color: #34d399; }
        /* 浅色主题下提高 diff 文字对比度 */
        body.light-theme .tc-diff-del { color: #b91c1c; }
        body.light-theme .tc-diff-ins { color: #047857; }

        /* bash 终端块 */
        .tc-term {
            background: var(--bg-codeblock);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            padding: 9px 11px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            line-height: 1.7;
        }
        .tc-term-cmd { color: #7ee2b8; }
        .tc-term-cmd::before { content: '$ '; color: #4f566b; }
        .tc-term-out {
            color: #8f97ae;
            white-space: pre-wrap;
            word-break: break-word;
            max-height: 234px;
            overflow-y: auto;
            margin-top: 4px;
        }
        /* 浅色主题：终端块改浅底深字 */
        body.light-theme .tc-term-cmd { color: #0f7b4f; }
        body.light-theme .tc-term-cmd::before { color: #8c959f; }
        body.light-theme .tc-term-out { color: #57606a; }
        .tc-term-wait { color: #60a5fa; }
        body.light-theme .tc-term-wait { color: #2563eb; }

        /* 结果行 / 执行中 */
        .tc-result {
            display: flex;
            align-items: baseline;
            gap: 6px;
            font-size: 11px;
            color: var(--text-secondary);
        }
        .tc-result i { color: #34d399; font-size: 10.5px; }
        .tc-running {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 11px;
            color: #60a5fa;
        }

        /* 通用回退段 */
        .tc-sec { min-width: 0; }
        .tc-label {
            font-size: 10.5px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--text-muted);
            margin-bottom: 5px;
        }

        /* 技能详情统一结构：头部行 / 参数行 / 内容块 / 资源行 */
        .sk-head { display: flex; align-items: center; gap: 7px; min-width: 0; }
        .sk-head > i { font-size: 10.5px; color: var(--accent-light); flex-shrink: 0; }
        .sk-name {
            font-family: 'JetBrains Mono', monospace;
            font-size: 11px;
            color: var(--text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .sk-src {
            margin-left: auto;
            flex-shrink: 0;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            color: var(--text-muted);
        }
        .sk-args {
            display: flex;
            flex-wrap: wrap;
            gap: 3px 14px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            color: var(--text-secondary);
        }
        .sk-args b { font-weight: 400; color: var(--text-muted); margin-right: 5px; }
        .sk-body {
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            line-height: 1.65;
            white-space: pre-wrap;
            word-break: break-word;
            color: var(--text-code);
            background: var(--bg-codeblock);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            padding: 9px 11px;
            max-height: 216px;
            overflow-y: auto;
        }
        .sk-body.err { color: #f87171; }
        body.light-theme .sk-body.err { color: #dc2626; }
        .sk-body::-webkit-scrollbar { width: 4px; }
        .sk-body::-webkit-scrollbar-track { background: transparent; }
        .sk-body::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 8px; }
        .sk-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 3px 14px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 10.5px;
            color: var(--text-secondary);
        }
        .sk-meta i { font-size: 9.5px; color: var(--accent-light); margin-right: 6px; }
        .sk-fold summary {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 10.5px;
            color: var(--text-muted);
            cursor: pointer;
            user-select: none;
            list-style: none;
            transition: color 0.15s;
        }
        .sk-fold summary::-webkit-details-marker { display: none; }
        .sk-fold summary:hover { color: var(--text-secondary); }
        .sk-fold summary .fa-chevron-right { font-size: 8.5px; transition: transform 0.15s; }
        .sk-fold[open] summary .fa-chevron-right { transform: rotate(90deg); }
        .sk-fold[open] summary { margin-bottom: 7px; }
        .sk-fold-err { color: #f87171; }
        body.light-theme .sk-fold-err { color: #dc2626; }
        .sk-desc {
            font-size: 10.5px;
            line-height: 1.65;
            color: var(--text-secondary);
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .sk-more {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 5px;
            width: 100%;
            padding: 6px 0;
            border: 1px dashed var(--border-color);
            border-radius: 7px;
            background: transparent;
            color: var(--text-muted);
            font-size: 10.5px;
            cursor: pointer;
            transition: color 0.15s, border-color 0.15s, background 0.15s;
        }
        .sk-more:hover {
            color: var(--accent-light);
            border-color: var(--border-active);
            background: var(--bg-hover);
        }
        .sk-more .fa-angles-right { font-size: 10px; }

        /* 技能列表样式 */
        .sk-skills {
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .sk-skill-item {
            display: flex;
            align-items: flex-start;
            gap: 10px;
            padding: 10px 12px;
            background: var(--bg-codeblock);
            border: 1px solid var(--border-color);
            border-radius: 7px;
            transition: border-color 0.15s;
        }
        .sk-skill-item:hover {
            border-color: var(--border-active);
        }
        .sk-skill-icon {
            flex-shrink: 0;
            width: 28px;
            height: 28px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--accent-bg);
            border-radius: 6px;
            color: var(--accent-light);
            font-size: 12px;
        }
        .sk-skill-info {
            display: flex;
            flex-direction: column;
            gap: 3px;
            min-width: 0;
        }
        .sk-skill-name {
            font-family: 'JetBrains Mono', monospace;
            font-size: 11.5px;
            font-weight: 600;
            color: var(--text-primary);
        }
        .sk-skill-desc {
            font-size: 10.5px;
            line-height: 1.5;
            color: var(--text-secondary);
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
    `;

    function injectStyle() {
        if (document.getElementById('tool-chip-style')) return;
        const style = document.createElement('style');
        style.id = 'tool-chip-style';
        style.textContent = STYLE;
        document.head.appendChild(style);
    }
    injectStyle();

    return { create, resolveRunning, resolveAll, open, openSkillDetail };
})();
