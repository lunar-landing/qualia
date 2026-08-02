package com.lunarlanding.qualia.core.retrieval;

import com.lunarlanding.qualia.core.constant.Constant;
import com.lunarlanding.qualia.core.model.chat.ChatModel;
import com.lunarlanding.qualia.core.model.chat.ChatResponse;
import com.lunarlanding.qualia.core.model.embedding.EmbeddingModel;
import com.lunarlanding.qualia.core.model.rerank.RerankModel;
import com.lunarlanding.qualia.core.store.VectorStore;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持查询重写的Retriever实现
 * 此实现会将单个查询重写为多个相关查询，然后对每个查询进行检索并合并结果
 */
public class QueryRewritingRetriever extends AbstractRetriever {
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private String customRewritePrompt;
    private final ChatModel chatModel;

    public QueryRewritingRetriever(VectorStore vectorStore, EmbeddingModel embeddingModel, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    public QueryRewritingRetriever(VectorStore vectorStore, EmbeddingModel embeddingModel, ChatModel chatModel, RerankModel rerankModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.rerankModel = rerankModel;
    }

    /**
     * 设置自定义查询重写规则（不包含 JSON 格式约束）
     * @param rules 自定义优化规则说明，如"将保险术语标准化"等
     */
    public void setRewritePrompt(String rules) {
        this.customRewritePrompt = rules;
    }

    /**
     * 构建完整的重写提示词
     * 固定 JSON 格式约束 + 可自定义的优化规则
     */
    private String buildRewritePrompt(String originalQuery) {
        StringBuilder prompt = new StringBuilder();

        // 1. 可自定义的优化规则部分
        if (customRewritePrompt != null && !customRewritePrompt.isEmpty()) {
            prompt.append("## 查询优化规则\n\n");
            prompt.append(customRewritePrompt).append("\n\n");
        }

        // 2. 固定的 JSON 格式约束（确保能正确解析）
        String jsonFormat = Constant.REWRITE_QUERY_PROMPT;
        prompt.append(String.format(jsonFormat, originalQuery));
        return prompt.toString();
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        System.out.println("[Query Rewriting Retriever] 开始处理查询: " + query + "top-K:" + topK);

        // 1. 生成重写查询
        List<String> rewrittenQueries = rewriteQueryWithJson(query);
        if (rewrittenQueries == null) {
            rewrittenQueries = new ArrayList<>();
        }

        if (!rewrittenQueries.contains(query)) {
            rewrittenQueries.add(0, query);
        }

        System.out.println("[Query Rewriting Retriever] 原始查询: " + query);
        System.out.println("[Query Rewriting Retriever] 重写后的查询列表: " + rewrittenQueries);
        System.out.println("[Query Rewriting Retriever] 重写后的子查询数量: " + (rewrittenQueries.size() - 1));

        // 2. 对每个子查询进行向量检索并立即执行重排
        List<RetrievalResult> allRerankedResults = new ArrayList<>();
        for (String q : rewrittenQueries) {
            List<RetrievalResult> subResults = vectorStore.similaritySearch(q, topK);
            if (rerankModel != null && !subResults.isEmpty()) {
                subResults = performRerank(query, subResults, topK);
            }
            allRerankedResults.addAll(subResults);
        }

        // 3. 全局去重并基于重排后的分数进行最终排序
        return deduplicateAndSort(allRerankedResults, topK);
    }

    @Override
    public List<RetrievalResult> retrieve(List<String> queries, int topK) {
        System.out.println("[Query Rewriting Retriever] 开始处理批量查询，共 " + queries.size() + " 个查询");

        List<RetrievalResult> allResults = new ArrayList<>();

        for (String query : queries) {
            allResults.addAll(retrieve(query, topK));
        }

        // 最终汇总去重排序返回
        return deduplicateAndSort(allResults, topK);
    }

    /**
     * 使用JSON格式约束的查询重写方法
     *
     * @param originalQuery 原始查询
     * @return 重写后的查询列表
     */
    private List<String> rewriteQueryWithJson(String originalQuery) {
        // 构建提示词：固定 JSON 格式约束 + 可自定义的优化规则
        String prompt = buildRewritePrompt(originalQuery);
        String responseContent = null;
        try {
            // 发送请求到大模型
            ChatResponse response = chatModel.chat(prompt);
            responseContent = response.getChoices().get(0).getMessage().getContent();
            responseContent = responseContent.replace("```json", "").replace("```","");

            // 尝试解析JSON响应
            JSONObject jsonResponse = JSON.parseObject(responseContent);
            JSONArray queriesArray = jsonResponse.getJSONArray("queries");

            if (queriesArray != null && !queriesArray.isEmpty()) {
                List<String> queries = new ArrayList<>();
                for (int i = 0; i < queriesArray.size() && i < 5; i++) {  // 最多取5个查询
                    String query = queriesArray.getString(i);
                    if (query != null && !query.trim().isEmpty()) {
                        queries.add(query.trim());
                    }
                }
                return queries;
            }
        } catch (Exception e) {
            // 如果JSON解析失败，尝试使用备用方法解析
            if (responseContent != null) {
                return fallbackParseQueries(responseContent);
            }
        }

        return new ArrayList<>();
    }

    /**
     * 备用查询解析方法，以防JSON解析失败
     */
    private List<String> fallbackParseQueries(String response) {
        List<String> queries = new ArrayList<>();

        // 按行分割响应
        String[] lines = response.split("\\n");

        for (String line : lines) {
            // 移除行首的编号（如 "1. ", "2. " 等）
            line = line.trim();

            // 匹配编号格式（如 "1. ", "2. ", "1.QUERY", 等）
            if (line.matches("^\\d+\\.\\s*.*")) {
                // 提取查询部分（移除编号）
                String query = line.replaceFirst("^\\d+\\.\\s*", "").trim();

                if (!query.isEmpty()) {
                    queries.add(query);
                }
            }
        }

        return queries;
    }

    /**
     * 获取聊天模型
     */
    public ChatModel getChatModel() {
        return chatModel;
    }
}
