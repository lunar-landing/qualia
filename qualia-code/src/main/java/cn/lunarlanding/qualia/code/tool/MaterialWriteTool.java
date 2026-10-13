package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.Map;

/**
 * material_write：向指定资料库写入一篇 Markdown 文章。
 *
 * <p>写侧工具（isReadOnly=false，只读问答模式自动拦截）。path 与 material_tree
 * 输出的契约一致（库内相对路径），父目录自动创建；已存在则覆盖正文（保留系统
 * frontmatter），新建则注入 author=agent 的元信息头。</p>
 */
public class MaterialWriteTool extends MaterialToolSupport {

    public MaterialWriteTool(MaterialService materials) {
        super(materials);
        this.setName("material_write");
        this.setDescription("向指定资料库写入一篇 Markdown 文章。path 为库内相对路径（可用 material_tree 查看现有结构，"
                + "支持子目录如 guide/deploy.md，父目录自动创建）；content 为 Markdown 正文，不要包含 frontmatter"
                + "（元信息由系统维护）。目标文章已存在时覆盖其正文。用户要求沉淀、保存或整理文档进资料库时使用。");
        this.setParameters(new Parameter[]{
                new Parameter("kb", "目标资料库的 id 或名称（先用 material_list 发现）", "string", true),
                new Parameter("path", "文章路径（库内相对路径，.md 后缀可省略，如 guide/deploy.md）", "string", true),
                new Parameter("content", "Markdown 正文（不含 frontmatter）", "string", true),
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        MaterialService.MaterialInfo kb = resolveKb(arguments);
        if (kb == null) {
            return kbMissMessage(arguments.get("kb"));
        }
        String path = argString(arguments, "path");
        String content = argString(arguments, "content");
        if (path.isEmpty()) {
            return "写入失败：path 不能为空（库内相对路径，如 guide/deploy.md）。";
        }
        if (content.isEmpty()) {
            return "写入失败：content 不能为空。";
        }
        try {
            MaterialService.WriteResult r = materials.writeArticle(kb.id(), path, content);
            String verb = r.created() ? "已新建文章" : "已覆盖更新文章";
            return verb + "：「" + kb.name() + "」/ " + r.path() + "（" + r.words() + " 字）。用户可在「资料」页面查看。";
        } catch (Exception e) {
            return "写入失败：" + e.getMessage();
        }
    }

    /** 写操作：问答只读模式不可用 */
    @Override
    public boolean isReadOnly() {
        return false;
    }
}
