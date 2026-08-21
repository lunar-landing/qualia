package cn.lunarlanding.qualia.core.store.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.lunarlanding.qualia.core.retrieval.parser.Document;
import cn.lunarlanding.qualia.core.retrieval.RetrievalResult;
import cn.lunarlanding.qualia.core.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ragflow VectorStore实现
 * 基于RAGFlow API的向量存储服务
 */
public class RagflowVectorStore implements VectorStore {

    private static final Logger logger = LoggerFactory.getLogger(RagflowVectorStore.class);
    private static final String DEFAULT_ADDRESS = "localhost:9000";

    private final String apiKey;
    private final String address;
    private List<String> datasetIds;
    private final HttpClient httpClient;

    public RagflowVectorStore(String apiKey, String address, List<String> datasetIds) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.datasetIds = datasetIds;
        this.address = address;
    }

    @Override
    public void addDocument(String filePath) {
        // RAGFlow的文档添加需要通过其管理界面或专门的文档上传API
        // 这里提供一个基本的实现框架
        logger.warn("RAGFlow文档添加功能需要通过其管理界面完成，当前仅记录文档信息: {}", filePath);
    }

    @Override
    public void addDocuments(List<Document> documents) {
        // RAGFlow的文档添加需要通过其管理界面或专门的文档上传API
        // 这里提供一个基本的实现框架
        logger.warn("RAGFlow文档添加功能需要通过其管理界面完成，当前仅记录文档信息");
    }

    @Override
    public List<RetrievalResult> similaritySearch(String query, int topK) {
        try {
            // 构建请求参数
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("question", query);
            requestBody.put("top_k", topK);
            requestBody.put("dataset_ids", datasetIds);
            requestBody.put("keyword", true);

            String jsonBody = JSON.toJSONString(requestBody);
            String urlStr = String.format("%s/api/v1/retrieval", address);

            System.err.println(query);
            System.err.println("访问地址：" + urlStr);

            // 构建HTTP请求
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("RAGFlow API调用失败，状态码: {}", response.statusCode());
                return new ArrayList<>();
            }

            // 解析响应
            JSONObject responseJson = JSON.parseObject(response.body());
            int code = responseJson.getIntValue("code");

            if (code != 0) {
                String message = responseJson.containsKey("message") ? responseJson.getString("message") : "未知错误";
                logger.error("RAGFlow API返回错误: {}", message);
                return new ArrayList<>();
            }

            // 解析chunks数据
            JSONObject dataNode = responseJson.getJSONObject("data");
            JSONArray chunksNode = dataNode.getJSONArray("chunks");

            List<RetrievalResult> results = new ArrayList<>();
            if (chunksNode != null) {
                for (int i = 0; i < chunksNode.size(); i++) {
                    JSONObject chunkNode = chunksNode.getJSONObject(i);
                    RetrievalResult result = parseChunkToRetrievalResult(chunkNode);
                    if (result != null) {
                        results.add(result);
                    }
                }
            }

            logger.info("RAGFlow检索完成，返回 {} 个结果", results.size());
            return results;

        } catch (Exception e) {
            logger.error("RAGFlow相似性搜索异常", e);
            return new ArrayList<>();
        }
    }

    /**
     * 将RAGFlow的chunk数据转换为RetrievalResult对象
     */
    private RetrievalResult parseChunkToRetrievalResult(JSONObject chunkNode) {
        try {
            String content = chunkNode.getString("content");
            double similarity = chunkNode.getDoubleValue("similarity");

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("similarity", similarity);
            metadata.put("vector_similarity", chunkNode.getDoubleValue("vector_similarity"));
            metadata.put("term_similarity", chunkNode.getDoubleValue("term_similarity"));

            if (chunkNode.containsKey("document_keyword")) {
                metadata.put("document_name", chunkNode.getString("document_keyword"));
            }
            if (chunkNode.containsKey("highlight")) {
                metadata.put("highlight", chunkNode.getString("highlight"));
            }
            if (chunkNode.containsKey("kb_id")) {
                metadata.put("kb_id", chunkNode.getString("kb_id"));
            }
            if (chunkNode.containsKey("chunk_id")) {
                metadata.put("chunk_id", chunkNode.getString("chunk_id"));
            }
            if (chunkNode.containsKey("id")) {
                metadata.put("chunk_id", chunkNode.getString("id"));
            }
            if (chunkNode.containsKey("doc_id")) {
                metadata.put("doc_id", chunkNode.getString("doc_id"));
            }
            if (chunkNode.containsKey("docnm_kwd")) {
                metadata.put("document_name", chunkNode.getString("docnm_kwd"));
            }
            if (chunkNode.containsKey("important_kwd")) {
                metadata.put("important_kwd", chunkNode.get("important_kwd"));
            }
            if (chunkNode.containsKey("important_keywords")) {
                metadata.put("important_keywords", chunkNode.get("important_keywords"));
            }
            copyMetadata(chunkNode, metadata, "chapter");
            copyMetadata(chunkNode, metadata, "chapter_name");
            copyMetadata(chunkNode, metadata, "section");
            copyMetadata(chunkNode, metadata, "section_name");
            copyMetadata(chunkNode, metadata, "section_title");
            copyMetadata(chunkNode, metadata, "heading");
            copyMetadata(chunkNode, metadata, "title");
            copyMetadata(chunkNode, metadata, "page_num");
            copyMetadata(chunkNode, metadata, "page_num_int");
            copyMetadata(chunkNode, metadata, "page_number");

            // 创建RetrievalResult对象
            return new RetrievalResult(content, similarity, metadata);

        } catch (Exception e) {
            logger.error("解析RAGFlow chunk数据失败", e);
            return null;
        }
    }

    /**
     * 设置API地址
     */
    public void setAddress(String address) {
        // 这个方法主要是为了兼容性，实际地址在构造函数中设置
        logger.info("RAGFlow地址设置为: {}", address);
    }

    /**
     * 获取API地址
     */
    public String getAddress() {
        return this.address;
    }

    private void copyMetadata(JSONObject source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }
}
