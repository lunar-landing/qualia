package cn.lunarlanding.qualia.core.model.rerank;

import java.util.List;

/**
 * 重排模型接口
 */
public interface RerankModel {

    /**
     * 对文档进行重排
     *
     * @param query     查询语句
     * @param documents 待排序的文档列表
     * @return 排序后的结果列表
     */
    List<RerankResult> rerank(String query, List<String> documents);

    /**
     * 对文档进行重排（指定返回数量）
     *
     * @param query     查询语句
     * @param documents 待排序的文档列表
     * @param topN      返回的前 N 个结果
     * @return 排序后的结果列表
     */
    List<RerankResult> rerank(String query, List<String> documents, Integer topN);

    /**
     * 设置 API 密钥
     */
    void apiKey(String apiKey);

    /**
     * 设置基础 URL
     */
    void baseUrl(String baseUrl);

    /**
     * 设置模型名称
     */
    void modelName(String modelName);

    /**
     * 获取模型名称
     */
    String modelName();
}
