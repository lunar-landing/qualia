package cn.lunarlanding.qualia.core.retrieval;

import cn.lunarlanding.qualia.core.model.rerank.RerankModel;
import cn.lunarlanding.qualia.core.model.rerank.RerankResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索器基类，提供重排和去重等通用逻辑
 */
public abstract class AbstractRetriever implements Retriever {
    protected RerankModel rerankModel;

    /**
     * 执行重排逻辑
     *
     * @param query      查询语句
     * @param candidates 候选文档列表
     * @param topK       返回数量
     * @return 排序后的结果
     */
    protected List<RetrievalResult> performRerank(String query, List<RetrievalResult> candidates, int topK) {
        if (rerankModel == null || candidates.isEmpty()) {
            return candidates.stream().limit(topK).collect(Collectors.toList());
        }

        List<String> docs = candidates.stream().map(RetrievalResult::getContent).collect(Collectors.toList());
        List<RerankResult> rerankResults = rerankModel.rerank(query, docs);

        for (RerankResult rr : rerankResults) {
            if (rr.getIndex() != null && rr.getIndex() < candidates.size()) {
                candidates.get(rr.getIndex()).setScore(rr.getScore());
            }
        }

        List<RetrievalResult> sorted = candidates.stream().sorted((a, b) -> Double.compare(b.getScore(), a.getScore())).limit(topK).collect(Collectors.toList());
        return sorted;
    }

    /**
     * 去重并按分数排序
     *
     * @param results 检索结果列表
     * @param limit   返回数量限制
     * @return 去重排序后的结果
     */
    protected List<RetrievalResult> deduplicateAndSort(List<RetrievalResult> results, int limit) {
        return results.stream().collect(Collectors.toMap(result -> result.getContent().toLowerCase().trim(), result -> result, (existing, replacement) -> existing.getScore() >= replacement.getScore() ? existing : replacement)).values().stream().sorted((a, b) -> Double.compare(b.getScore(), a.getScore())).limit(limit).collect(Collectors.toList());
    }
}
