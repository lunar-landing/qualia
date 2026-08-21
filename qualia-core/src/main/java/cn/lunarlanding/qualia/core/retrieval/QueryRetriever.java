package cn.lunarlanding.qualia.core.retrieval;

import cn.lunarlanding.qualia.core.model.embedding.EmbeddingModel;
import cn.lunarlanding.qualia.core.model.rerank.RerankModel;
import cn.lunarlanding.qualia.core.store.VectorStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单检索器实现
 * 支持基本查询功能
 */
public class QueryRetriever extends AbstractRetriever {

    private final VectorStore vectorStore;
    private EmbeddingModel embeddingModel;

    public QueryRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public QueryRetriever(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    public QueryRetriever(VectorStore vectorStore, EmbeddingModel embeddingModel, RerankModel rerankModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.rerankModel = rerankModel;
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        if (rerankModel == null) {
            return vectorStore.similaritySearch(query, topK);
        }

        // 使用 topK 获取候选集并进行重排
        List<RetrievalResult> candidates = vectorStore.similaritySearch(query, topK);
        return performRerank(query, candidates, topK);
    }

    @Override
    public List<RetrievalResult> retrieve(List<String> queries, int topK) {
        List<RetrievalResult> allResults = new ArrayList<>();

        // 对每个查询执行检索（内部包含可能的重排逻辑）
        for (String query : queries) {
            allResults.addAll(retrieve(query, topK));
        }

        // 去重并对所有查询的结果进行全局排序返回
        return deduplicateAndSort(allResults, topK);
    }
}
