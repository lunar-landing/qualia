package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.List;
import java.util.Map;

/**
 * material_tree：浏览指定资料库的目录树（文件夹/文章标题 + 字数 + 首行摘要）。
 *
 * <p>wiki 导航的核心：模型靠树与摘要定位文章；树输出每行附带 path（方括号标注），
 * 模型无需猜测标题到文件名的映射，直接拿 path 调 material_read 整篇阅读。</p>
 */
public class MaterialTreeTool extends MaterialToolSupport {

    public MaterialTreeTool(MaterialService materials) {
        super(materials);
        this.setName("material_tree");
        this.setDescription("浏览指定资料库的目录树，返回文件夹结构与每篇文章的标题、字数和首行摘要。"
                + "先用 material_list 发现库，再用本工具定位文章（记下其相对路径），然后 material_read 整篇阅读。");
        this.setParameters(new Parameter[]{
                new Parameter("kb", "资料库 id 或名称", "string", true),
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        MaterialService.MaterialInfo kb = resolveKb(arguments);
        if (kb == null) {
            return kbMissMessage(arguments.get("kb"));
        }
        try {
            List<MaterialService.TreeNode> tree = materials.tree(kb.id());
            if (tree.isEmpty()) {
                return "资料库「" + kb.name() + "」还没有文章，可提示用户先上传。";
            }
            StringBuilder sb = new StringBuilder("资料库「").append(kb.name()).append("」目录（文章行方括号内为 path，调用 material_read 时原样传入）：\n");
            render(sb, tree, 0);
            return sb.toString();
        } catch (Exception e) {
            return "读取目录树失败：" + e.getMessage();
        }
    }

    /** 树渲染：文件夹以 name/ 标识；文章为 标题（N字）[path] — 摘要（截 50 字），path 供 material_read 直接引用 */
    private void render(StringBuilder sb, List<MaterialService.TreeNode> nodes, int depth) {
        for (MaterialService.TreeNode node : nodes) {
            String indent = "  ".repeat(depth);
            if ("folder".equals(node.type())) {
                sb.append(indent).append(node.name()).append("/\n");
                render(sb, node.children() == null ? List.of() : node.children(), depth + 1);
                continue;
            }
            sb.append(indent).append(node.name());
            if (node.words() != null) {
                sb.append("（").append(node.words()).append("字）");
            }
            sb.append('[').append(node.path()).append(']');
            if (node.summary() != null && !node.summary().isBlank()) {
                String summary = node.summary();
                sb.append(" — ").append(summary.length() > 50 ? summary.substring(0, 50) : summary);
            }
            sb.append('\n');
        }
    }

    /** 只读工具：问答模式可用 */
    @Override
    public boolean isReadOnly() {
        return true;
    }
}
