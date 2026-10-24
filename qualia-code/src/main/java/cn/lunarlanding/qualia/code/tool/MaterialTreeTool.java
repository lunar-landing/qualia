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

    /** 树输出行数上限：超过截断，防大库一次全量渲染挤爆上下文 */
    private static final int MAX_LINES = 200;

    public MaterialTreeTool(MaterialService materials) {
        super(materials);
        this.setName("material_tree");
        this.setDescription("浏览指定资料库的目录树，返回文件夹结构与每篇文章的标题、字数和首行摘要。"
                + "先用 material_list 发现库，再用本工具定位文章（记下其相对路径），然后 material_read 整篇阅读。"
                + "目录过大输出被截断时，改用 material_search 全文检索定位。");
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
            boolean truncated = render(sb, tree, 0, new int[]{0});
            if (truncated) {
                sb.append("\n（目录较大，仅展示前 ").append(MAX_LINES)
                        .append(" 行，已截断；请改用 material_search 全文检索定位目标文章）\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取目录树失败：" + e.getMessage();
        }
    }

    /** 树渲染：文件夹以 name/ 标识；文章为 标题（N字）[path] — 摘要（截 50 字）；行数超限时截断并返回 true */
    private boolean render(StringBuilder sb, List<MaterialService.TreeNode> nodes, int depth, int[] emitted) {
        for (MaterialService.TreeNode node : nodes) {
            if (emitted[0] >= MAX_LINES) {
                return true;
            }
            String indent = "  ".repeat(depth);
            if ("folder".equals(node.type())) {
                sb.append(indent).append(node.name()).append("/\n");
                emitted[0]++;
                if (render(sb, node.children() == null ? List.of() : node.children(), depth + 1, emitted)) {
                    return true;
                }
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
            emitted[0]++;
        }
        return false;
    }

    /** 只读工具：问答模式可用 */
    @Override
    public boolean isReadOnly() {
        return true;
    }
}
