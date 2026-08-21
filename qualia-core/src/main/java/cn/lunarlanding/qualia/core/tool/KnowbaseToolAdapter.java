package cn.lunarlanding.qualia.core.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.lunarlanding.qualia.core.knowledge.KnowledgeBase;
import cn.lunarlanding.qualia.core.knowledge.KnowledgeSourceUtil;
import cn.lunarlanding.qualia.core.model.rerank.RerankModel;
import cn.lunarlanding.qualia.core.retrieval.RetrievalResult;
import cn.lunarlanding.qualia.core.tool.impl.knowledge.RagflowTool;

import java.util.List;
import java.util.Map;

/**
 * 知识库工具适配器 —— 将 {@link KnowledgeBase} 桥接为 FunctionTool。
 *
 * <p>与 {@link McpToolAdapter} 设计对齐：Agent 调用 {@code addKnowbase()} 时，
 * 内部创建此适配器并注册为工具。</p>
 */
public class KnowbaseToolAdapter extends FunctionTool {

    private final KnowledgeBase knowledgeBase;

    /** 检索链路委托给 RagflowTool，避免重复实现；不注册为工具，仅内部复用 */
    private final RagflowTool ragflowTool;

    /**
     * @param knowledgeBase 知识库配置
     * @param model         用于结果重排序的 RerankModel（可为 null）
     */
    public KnowbaseToolAdapter(KnowledgeBase knowledgeBase, RerankModel model) {
        this.knowledgeBase = knowledgeBase;

        String toolName = sanitizeName(knowledgeBase.getName()) + "_knowledge";
        String desc = knowledgeBase.getDescription() != null && !knowledgeBase.getDescription().isEmpty() ? "从「" + knowledgeBase.getName() + "」知识库检索文档。" + knowledgeBase.getDescription() : "从「" + knowledgeBase.getName() + "」知识库检索相关文档片段。当需要查询专业知识、文档内容或历史资料时使用此工具。";

        this.setName(toolName);
        this.setDescription(desc);
        this.setParameters(new Parameter[]{
                new Parameter("query", "string", "检索查询语句，描述你想查找的内容", true)
        });

        this.ragflowTool = new RagflowTool(
                knowledgeBase.getAddress(), knowledgeBase.getApiKey(),
                List.of(knowledgeBase.getDatasetId()), model);
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.trim().isEmpty()) {
            return "错误：查询语句不能为空";
        }

        int topK = 8;
        List<RetrievalResult> results = ragflowTool.retrieve(query, topK);

        if (results.isEmpty()) {
            return "未找到相关文档内容。";
        }

        return formatResults(results);
    }

    /**
     * 格式化检索结果（返回结构化 JSON，前端可解析展示）
     */
    private String formatResults(List<RetrievalResult> results) {
        JSONObject json = new JSONObject();
        json.put("source", "knowbase");
        json.put("knowledgeBase", knowledgeBase.getName());
        json.put("count", results.size());

        JSONArray items = new JSONArray();
        for (RetrievalResult result : results) {
            items.add(KnowledgeSourceUtil.formatItem(result));
        }
        json.put("items", items);

        return json.toJSONString();
    }

    /**
     * 将知识库名称转换为合法的工具名（只保留字母、数字、下划线）
     */
    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_").toLowerCase();
    }

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }
}
