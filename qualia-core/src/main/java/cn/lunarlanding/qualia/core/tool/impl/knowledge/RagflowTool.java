package cn.lunarlanding.qualia.core.tool.impl.knowledge;

import cn.lunarlanding.qualia.core.knowledge.KnowledgeSourceUtil;
import cn.lunarlanding.qualia.core.model.rerank.RerankModel;
import cn.lunarlanding.qualia.core.retrieval.QueryRetriever;
import cn.lunarlanding.qualia.core.retrieval.RetrievalResult;
import cn.lunarlanding.qualia.core.retrieval.Retriever;
import cn.lunarlanding.qualia.core.store.impl.RagflowVectorStore;
import cn.lunarlanding.qualia.core.tool.FunctionTool;
import cn.lunarlanding.qualia.core.tool.Parameter;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RagFlow 工具
 * 基于 RAGFlow API 的文档检索适配器
 */
public class RagflowTool extends FunctionTool {
    private final String apiKey;
    private final String address;
    private final List<String> datasetIds;
    private final Retriever retriever;

    /**
     * 构造函数（支持查询优化和重排序）
     *
     * @param address       RagFlow 服务地址，如 http://localhost:9000
     * @param apiKey        RagFlow API Key
     * @param datasetIds    数据集 ID 列表
     * @param model   用于结果重排序的 RerankModel（可为 null）
     */
    public RagflowTool(String address, String apiKey, List<String> datasetIds, RerankModel model) {

        this.setName("ragflow_tool");
        this.setDescription("从 RagFlow 知识库中检索相关文档片段。当需要查询专业知识、文档内容或历史资料时使用此工具。");
        this.setParameters(new Parameter[]{
                new Parameter("query", "string", "检索查询语句，描述你想查找的内容", true)
        });

        this.apiKey = apiKey;
        this.address = address;
        this.datasetIds = datasetIds != null ? datasetIds : new ArrayList<>();
        RagflowVectorStore vectorStore = new RagflowVectorStore(apiKey, address, this.datasetIds);
        this.retriever = new QueryRetriever(vectorStore, null, model);
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.trim().isEmpty()) {
            return "错误：查询语句不能为空";
        }

        int topK = 8;
        List<RetrievalResult> results = retrieve(query, topK);

        if (results.isEmpty()) {
            return "未找到相关文档内容。";
        }

        return formatResults(results);
    }

    /**
     * 执行检索并返回原始结果（供 KnowbaseToolAdapter 等适配器复用检索链路，自行格式化输出）
     */
    public List<RetrievalResult> retrieve(String query, int topK) {
        return retriever.retrieve(query, topK);
    }

    /**
     * 格式化检索结果（返回结构化 JSON，前端可解析展示）
     */
    private String formatResults(List<RetrievalResult> results) {
        JSONObject json = new JSONObject();
        json.put("source", "ragflow");
        json.put("count", results.size());

        JSONArray items = new JSONArray();
        for (RetrievalResult result : results) {
            items.add(KnowledgeSourceUtil.formatItem(result));
        }
        json.put("items", items);

        return json.toJSONString();
    }

    // Getter 方法
    public String getAddress() {
        return address;
    }

    public String getApiKey() {
        return apiKey;
    }

    public List<String> getDatasetIds() {
        return datasetIds;
    }
}
