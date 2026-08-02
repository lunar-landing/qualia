package com.lunarlanding.qualia.core.model.embedding;

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
 * DashScope嵌入模型实现
 */
public class DashscopeEmbeddingModel implements EmbeddingModel {

    private String apiKey;
    private String modelName = "text-embedding-v4";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    private Integer dimensions = 1024; // 默认向量维度
    private HttpClient httpClient;

    public DashscopeEmbeddingModel(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public List<Float> embed(String input) {
        return embed(input, modelName, dimensions);
    }

    @Override
    public List<List<Float>> embedBatch(String[] inputs) {
        try {

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", modelName);
            requestBody.put("dimensions", dimensions);
            requestBody.put("input", inputs);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                // 解析响应并提取向量数据
                JSONObject jsonResponse = JSON.parseObject(response.body());

                // 提取嵌入向量
                List<List<Float>> embeddings = new ArrayList<>();
                JSONArray dataArray = jsonResponse.getJSONArray("data");

                for (int i = 0; i < dataArray.size(); i++) {
                    JSONObject item = dataArray.getJSONObject(i);
                    JSONArray embeddingArray = item.getJSONArray("embedding");

                    List<Float> embedding = new ArrayList<>();
                    for (int j = 0; j < embeddingArray.size(); j++) {
                        embedding.add(embeddingArray.getFloatValue(j));
                    }
                    embeddings.add(embedding);
                }

                return embeddings;
            } else {
                throw new RuntimeException("API调用失败，状态码: " + response.statusCode() + ", 响应: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("调用嵌入模型时发生错误: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Float> embed(String input, String model, Integer dimensions) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("input", input);
            if (dimensions != null) {
                requestBody.put("dimensions", dimensions);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                // 解析响应并提取向量数据
                JSONObject jsonResponse = JSON.parseObject(response.body());

                // 提取嵌入向量
                JSONArray dataArray = jsonResponse.getJSONArray("data");
                if (dataArray != null && dataArray.size() > 0) {
                    JSONObject item = dataArray.getJSONObject(0);
                    JSONArray embeddingArray = item.getJSONArray("embedding");

                    List<Float> embedding = new ArrayList<>();
                    for (int j = 0; j < embeddingArray.size(); j++) {
                        embedding.add(embeddingArray.getFloatValue(j));
                    }
                    return embedding;
                } else {
                    throw new RuntimeException("API响应中未找到嵌入向量数据");
                }
            } else {
                throw new RuntimeException("API调用失败，状态码: " + response.statusCode() +
                        ", 响应: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("调用嵌入模型时发生错误: " + e.getMessage(), e);
        }
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public void modelName(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public void baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void apiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
