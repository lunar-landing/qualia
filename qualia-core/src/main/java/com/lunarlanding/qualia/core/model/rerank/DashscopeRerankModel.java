package com.lunarlanding.qualia.core.model.rerank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * DashScope 重排模型实现
 */
public class DashscopeRerankModel implements RerankModel {
    private String apiKey;
    private String modelName = "qwen3-rerank";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-api/v1/reranks";
    private Protocol protocol = Protocol.COMPATIBLE;

    public enum Protocol { COMPATIBLE, NATIVE }
    private HttpClient httpClient;
    private String instruct;
    public DashscopeRerankModel(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public DashscopeRerankModel(String apiKey, String modelName, Protocol protocol) {
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.protocol = protocol;
        updateBaseUrlByProtocol();
    }

    private void updateBaseUrlByProtocol() {
        if (protocol == Protocol.COMPATIBLE) {
            this.baseUrl = "https://dashscope.aliyuncs.com/compatible-api/v1/reranks";
        } else {
            this.baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        }
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
        updateBaseUrlByProtocol();
    }

    public void setInstruct(String instruct) {
        this.instruct = instruct;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents) {
        return rerank(query, documents, null);
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, Integer topN) {
        try {
            String requestBody = protocol == Protocol.COMPATIBLE ?
                buildCompatibleRequestBody(query, documents, topN) :
                buildNativeRequestBody(query, documents, topN);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                throw new RuntimeException("API 调用失败，状态码: " + response.statusCode() + ", 响应: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("调用重排模型时发生错误: " + e.getMessage(), e);
        }
    }

    private String buildCompatibleRequestBody(String query, List<String> documents, Integer topN) {
        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("query", query);
        body.put("documents", documents);
        if (topN != null) {
            body.put("top_n", topN);
        }
        if (instruct != null) {
            body.put("instruct", instruct);
        }
        return body.toJSONString();
    }

    private String buildNativeRequestBody(String query, List<String> documents, Integer topN) {
        JSONObject body = new JSONObject();
        body.put("model", modelName);

        JSONObject input = new JSONObject();
        input.put("query", query);
        input.put("documents", documents);
        body.put("input", input);

        JSONObject parameters = new JSONObject();
        parameters.put("return_documents", true);
        if (topN != null) {
            parameters.put("top_n", topN);
        }
        body.put("parameters", parameters);

        return body.toJSONString();
    }

    private List<RerankResult> parseResponse(String responseBody) {
        List<RerankResult> results = new ArrayList<>();
        JSONObject jsonResponse = JSON.parseObject(responseBody);

        JSONArray dataArray;
        if (protocol == Protocol.COMPATIBLE) {
            dataArray = jsonResponse.getJSONArray("results");
        } else {
            JSONObject output = jsonResponse.getJSONObject("output");
            dataArray = output != null ? output.getJSONArray("results") : null;
        }

        if (dataArray != null) {
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                Integer index = item.getInteger("index");
                Double score = item.getDouble("relevance_score");

                String docContent = null;
                Object docObj = item.get("document");
                if (docObj instanceof JSONObject) {
                    docContent = ((JSONObject) docObj).getString("text");
                } else if (docObj instanceof String) {
                    docContent = (String) docObj;
                }

                results.add(new RerankResult(index, score, docContent));
            }
        }
        return results;
    }

    @Override
    public void apiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void modelName(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
