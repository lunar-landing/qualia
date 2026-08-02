package com.lunarlanding.qualia.core.store.impl;

import com.lunarlanding.qualia.core.retrieval.parser.Document;
import com.lunarlanding.qualia.core.retrieval.RetrievalResult;
import com.lunarlanding.qualia.core.store.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储实现
 */
public class InMemoryVectorStore implements VectorStore {

    // 存储文档内容和其对应的嵌入向量
    private final Map<String, List<Float>> vectorStore = new ConcurrentHashMap<>();
    // 存储文档的元数据
    private final Map<String, Document> documentStore = new ConcurrentHashMap<>();
    // 用于生成唯一文档ID
    private long documentIdCounter = 0;

    @Override
    public void addDocument(String filePath) {
        // 这里应该从文件路径加载文档内容，暂时模拟为简单字符串
        // 实际应用中需要结合DocumentLoader或类似机制
        String content = "Content from " + filePath; // 实际应从文件读取
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("source", filePath);

        Document document = new Document(content, metadata);
        addDocument(document);
    }

    @Override
    public void addDocuments(List<Document> documents) {
        for (Document doc : documents) {
            addDocument(doc);
        }
    }

    /**
     * 添加单个文档到向量存储
     */
    public void addDocument(Document document) {
        String docId = "doc_" + (++documentIdCounter);

        // 在没有embedding model的情况下，我们暂时存储文档但无法计算向量
        documentStore.put(docId, document);
    }

    @Override
    public List<RetrievalResult> similaritySearch(String query, int topK) {
        if (documentStore.isEmpty()) {
            return new ArrayList<>();
        }

        // 在没有embedding model的情况下，我们使用简单的关键词匹配
        List<ScoredDocument> scoredDocs = new ArrayList<>();

        for (Map.Entry<String, Document> entry : documentStore.entrySet()) {
            String docId = entry.getKey();
            Document doc = entry.getValue();

            // 简单的关键词匹配分数计算
            double similarity = calculateKeywordSimilarity(query, doc.getContent());

            if (similarity > 0) {
                scoredDocs.add(new ScoredDocument(similarity, doc));
            }
        }

        // 按相似度降序排序
        scoredDocs.sort((a, b) -> Double.compare(b.score, a.score));

        // 返回topK个结果
        List<RetrievalResult> results = new ArrayList<>();
        int count = Math.min(topK, scoredDocs.size());
        for (int i = 0; i < count; i++) {
            ScoredDocument scoredDoc = scoredDocs.get(i);
            results.add(new RetrievalResult(
                scoredDoc.document.getContent(),
                scoredDoc.score,
                scoredDoc.document.getMetadata()
            ));
        }

        return results;
    }

    /**
     * 简单的关键词相似度计算
     */
    private double calculateKeywordSimilarity(String query, String content) {
        String lowerQuery = query.toLowerCase();
        String lowerContent = content.toLowerCase();

        // 计算查询词在内容中出现的比例
        String[] queryWords = lowerQuery.split("\\s+");
        int matchedWords = 0;

        for (String word : queryWords) {
            if (!word.trim().isEmpty() && lowerContent.contains(word.trim())) {
                matchedWords++;
            }
        }

        return (double) matchedWords / queryWords.length;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private double cosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            float a = vectorA.get(i);
            float b = vectorB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0 || normB == 0) {
            return 0.0; // 如果任一向量为零向量，则相似度为0
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 用于临时存储文档及其相似度得分的内部类
     */
    private static class ScoredDocument {
        final double score;
        final Document document;

        ScoredDocument(double score, Document document) {
            this.score = score;
            this.document = document;
        }
    }

    /**
     * 清空向量存储
     */
    public void clear() {
        vectorStore.clear();
        documentStore.clear();
        documentIdCounter = 0;
    }

    /**
     * 获取存储的文档数量
     */
    public int size() {
        return vectorStore.size();
    }
}
