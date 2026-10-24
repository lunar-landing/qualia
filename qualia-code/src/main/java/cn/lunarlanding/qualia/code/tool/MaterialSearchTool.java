package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.List;
import java.util.Map;

/**
 * material_search：库内全文检索（现场扫描，无持久索引），返回相关文章的片段与路径。
 *
 * <p>导航式检索的兜底层：material_tree 只覆盖标题与首行摘要，正文中间的概念
 * 只能靠本工具召回。片段仅用于确认相关性——回答必须 material_read 阅读原文，
 * 防止片段断章取义成为答案来源。</p>
 */
public class MaterialSearchTool extends MaterialToolSupport {

    /** 返回条数上限：过多会稀释上下文，与知识库检索工具的 topK 口径一致 */
    private static final int TOP_K = 8;

    public MaterialSearchTool(MaterialService materials) {
        super(materials);
        this.setName("material_search");
        this.setDescription("在指定资料库内全文检索关键词，返回最相关文章的片段与路径。"
                + "当 material_tree 目录过大、或关键词只出现在正文中（标题定位不到）时使用。"
                + "片段仅用于确认相关性，引用内容必须先用 material_read 阅读原文。");
        this.setParameters(new Parameter[]{
                new Parameter("kb", "资料库 id 或名称", "string", true),
                new Parameter("query", "检索关键词，支持中英文，多个词用空格分隔", "string", true),
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        MaterialService.MaterialInfo kb = resolveKb(arguments);
        if (kb == null) {
            return kbMissMessage(arguments.get("kb"));
        }
        String query = argString(arguments, "query");
        if (query.isEmpty()) {
            return "错误：query 不能为空";
        }
        try {
            List<MaterialService.SearchHit> hits = materials.search(kb.id(), query, TOP_K);
            if (hits.isEmpty()) {
                return "资料库「" + kb.name() + "」中未找到与「" + query + "」相关的内容。"
                        + "可尝试换关键词（如正文中可能出现的术语），或用 material_tree 浏览目录。";
            }
            StringBuilder sb = new StringBuilder("资料库「").append(kb.name())
                    .append("」中与「").append(query).append("」相关的内容（按相关性排序）：\n");
            int i = 1;
            for (MaterialService.SearchHit hit : hits) {
                sb.append(i++).append(". ").append(hit.title())
                        .append(" [").append(hit.path()).append("]\n   ")
                        .append(hit.snippet()).append('\n');
            }
            sb.append("\n片段仅用于定位，回答引用请先用 material_read 阅读对应文章原文。");
            return sb.toString();
        } catch (Exception e) {
            return "检索失败：" + e.getMessage();
        }
    }

    /** 只读工具：问答模式可用 */
    @Override
    public boolean isReadOnly() {
        return true;
    }
}
