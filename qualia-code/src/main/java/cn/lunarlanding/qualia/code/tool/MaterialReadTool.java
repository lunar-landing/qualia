package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.Map;

/**
 * material_read：整篇阅读资料库文章（frontmatter 元信息头 + 全文 Markdown）。
 *
 * <p>一期核心理念的智能体侧落地：不切块、不检索片段，整篇返回供模型通读；
 * 超长文章截断保护（对齐工作区 ReadTool 的截断策略），提示用户在「资料」界面看全文。</p>
 */
public class MaterialReadTool extends MaterialToolSupport {

    /** 正文返回上限（字符）：超出截断，避免单次工具结果挤爆上下文 */
    private static final int MAX_READ_CHARS = 50000;

    public MaterialReadTool(MaterialService materials) {
        super(materials);
        this.setName("material_read");
        this.setDescription("整篇阅读资料库中的一篇文章，返回元信息（标题/作者/字数）与全文 Markdown。"
                + "path 用 material_tree 输出中的相对路径。不切块，返回完整正文；超长时截断。");
        this.setParameters(new Parameter[]{
                new Parameter("kb", "资料库 id 或名称", "string", true),
                new Parameter("path", "文章相对路径（来自 material_tree，如 guide/deployment.md）", "string", true),
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        MaterialService.MaterialInfo kb = resolveKb(arguments);
        if (kb == null) {
            return kbMissMessage(arguments.get("kb"));
        }
        String path = argString(arguments, "path");
        if (path.isEmpty()) {
            return "错误：path 参数不能为空（可先调用 material_tree 获取文章路径）。";
        }
        try {
            MaterialService.Article article = materials.article(kb.id(), path);
            String content = article.content() == null ? "" : article.content();
            StringBuilder sb = new StringBuilder();
            sb.append("【").append(kb.name()).append(" / ").append(article.path()).append("】\n");
            sb.append("标题：").append(article.title())
                    .append(" · 作者：").append(article.author())
                    .append(" · ").append(article.words()).append(" 字\n\n");
            if (content.length() > MAX_READ_CHARS) {
                sb.append(content, 0, MAX_READ_CHARS);
                sb.append("\n\n…（正文过长已截断，全文共 ").append(article.words())
                        .append(" 字，可在「资料」界面阅读完整内容）");
            } else {
                sb.append(content);
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return "读取失败：" + e.getMessage() + "。路径请先通过 material_tree 确认。";
        } catch (Exception e) {
            return "读取失败：" + e.getMessage();
        }
    }

    /** 只读工具：问答模式可用 */
    @Override
    public boolean isReadOnly() {
        return true;
    }
}
