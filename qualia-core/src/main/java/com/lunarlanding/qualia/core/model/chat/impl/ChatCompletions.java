package com.lunarlanding.qualia.core.model.chat.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lunarlanding.qualia.core.model.chat.*;
import com.lunarlanding.qualia.core.model.chat.conf.ResponseFormatType;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Chat Completions 协议基类
 * 符合OpenAI Chat Completions API标准
 * 
 * 子类可重写默认模型和baseUrl来适配不同厂商
 */
public class ChatCompletions implements ChatModel {

    private String apiKey;
    private String modelName;
    private String baseUrl;
    private HttpClient httpClient;
    private Double temperature = 0.1;
    private Integer maxTokens;
    protected boolean enableThinking = false;
    private Double topP;

    public ChatCompletions(String apiKey) {
        this.apiKey = apiKey;
        this.modelName = getDefaultModel();
        this.baseUrl = getDefaultBaseUrl();
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 获取默认模型名称，子类可重写
     */
    protected String getDefaultModel() {
        return "gpt-4o";
    }

    /**
     * 获取默认baseUrl，子类可重写
     */
    protected String getDefaultBaseUrl() {
        return "https://api.openai.com/v1/chat/completions";
    }

    /**
     * 添加深度思考参数到请求体，子类可重写
     * DashScope: {"enable_thinking": true/false}
     * 小米: {"thinking": {"type": "enabled/disabled"}}
     */
    protected void addThinkingParams(JSONObject requestBody) {
        requestBody.put("enable_thinking", enableThinking);
    }

    @Override
    public ChatResponse chat(String message) {
        List<ChatMessage> messages = List.of(ChatMessage.user(message));
        return chat(messages, null);
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        return chat(messages, null);
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, ResponseFormatType formatType) {
        try {
            JSONObject requestBody = buildRequestBody(messages, false, formatType);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseNonStreamingResponse(response.body());
            } else {
                throw new RuntimeException("HTTP Error: " + response.statusCode() + ", Body: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error calling OpenAI Compatible API: " + e.getMessage(), e);
        }
    }

    /**
     * 构建请求体
     */
    protected JSONObject buildRequestBody(List<ChatMessage> messages, boolean stream, ResponseFormatType formatType) {
        List<Map<String, String>> messageMaps = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, String> msgMap = new java.util.HashMap<>();
            msgMap.put("role", message.getRole());
            msgMap.put("content", message.getContent());
            // tool 角色必须包含 tool_call_id
            if ("tool".equals(message.getRole()) && message.getToolCallId() != null) {
                msgMap.put("tool_call_id", message.getToolCallId());
            }
            messageMaps.add(msgMap);
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", modelName);
        requestBody.put("temperature", temperature);
        addThinkingParams(requestBody);
        requestBody.put("messages", messageMaps);

        if (stream) {
            requestBody.put("stream", true);
        }

        if (formatType != null) {
            JSONObject responseFormat = new JSONObject();
            responseFormat.put("type", formatType.getValue());
            requestBody.put("response_format", responseFormat);
        }

        if (maxTokens != null) {
            requestBody.put("max_tokens", maxTokens);
        }

        if (topP != null) {
            requestBody.put("top_p", topP);
        }

        return requestBody;
    }

    /**
     * 解析非流式响应
     */
    protected ChatResponse parseNonStreamingResponse(String responseBody) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            ChatResponse response = new ChatResponse();

            if (json.containsKey("choices")) {
                var choicesArray = json.getJSONArray("choices");
                if (choicesArray != null && !choicesArray.isEmpty()) {
                    var choiceJson = choicesArray.getJSONObject(0);

                    Integer index = choiceJson.getInteger("index");
                    String finishReason = choiceJson.getString("finish_reason");

                    String content = "";
                    ChatMessage msg;

                    if (choiceJson.containsKey("message")) {
                        var messageJson = choiceJson.getJSONObject("message");
                        String role = messageJson.getString("role");
                        content = messageJson.getString("content");
                        msg = new ChatMessage(role != null ? role : "assistant", content);
                    } else {
                        msg = new ChatMessage("assistant", content);
                    }
                    ChatChoice choice = new ChatChoice(index, msg, finishReason);
                    response.setChoices(List.of(choice));
                }
            }

            parseResponseMetadata(json, response);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse chat response: " + e.getMessage(), e);
        }
    }

    /**
     * 解析流式响应 chunk
     */
    protected ChatResponse parseStreamingChunk(String streamData) {
        try {
            JSONObject json = JSON.parseObject(streamData);
            ChatResponse response = new ChatResponse();

            if (json.containsKey("choices")) {
                var choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    var choice = choices.getJSONObject(0);

                    String content = "";
                    String reasoningContent = null;
                    if (choice.containsKey("delta")) {
                        var delta = choice.getJSONObject("delta");
                        content = delta.getString("content");
                        reasoningContent = delta.getString("reasoning_content");
                    }

                    Integer index = choice.getInteger("index");
                    String finishReason = choice.getString("finish_reason");

                    ChatMessage msg = new ChatMessage("assistant", content, reasoningContent);
                    ChatChoice choiceObj = new ChatChoice(index, msg, finishReason);
                    response.setChoices(List.of(choiceObj));
                }
            }

            parseResponseMetadata(json, response);
            if ((response.getChoices() != null && !response.getChoices().isEmpty()) || response.getUsage() != null) {
                return response;
            }
        } catch (Exception e) {
            // 解析失败时返回 null
        }
        return null;
    }

    /**
     * 解析公共元数据
     */
    protected void parseResponseMetadata(JSONObject json, ChatResponse response) {
        if (json.containsKey("usage")) {
            var usageJson = json.getJSONObject("usage");
            if (usageJson != null) {
                ChatUsage usage = new ChatUsage();
                usage.setPromptTokens(usageJson.getInteger("prompt_tokens"));
                usage.setCompletionTokens(usageJson.getInteger("completion_tokens"));
                usage.setTotalTokens(usageJson.getInteger("total_tokens"));
                response.setUsage(usage);
            }
        }
        if (json.containsKey("model")) {
            response.setModel(json.getString("model"));
        }
        if (json.containsKey("id")) {
            response.setId(json.getString("id"));
        }
    }

    @Override
    public Flux<ChatResponse> chatStream(String message) {
        List<ChatMessage> messages = List.of(ChatMessage.user(message));
        return chatStream(messages, null);
    }

    @Override
    public Flux<ChatResponse> chatStream(List<ChatMessage> messages) {
        return chatStream(messages, null);
    }

    @Override
    public Flux<ChatResponse> chatStream(List<ChatMessage> messages, ResponseFormatType formatType) {
        return Flux.<ChatResponse>create(sink -> {
            try {
                JSONObject requestBody = buildRequestBody(messages, true, formatType);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()))
                        .build();
                HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (sink.isCancelled()) break;
                            line = line.trim();
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (!"[DONE]".equals(data)) {
                                    try {
                                        ChatResponse partialResponse = parseStreamingChunk(data);
                                        if (partialResponse != null) {
                                            boolean hasContent = false;
                                            boolean hasReasoning = false;
                                            boolean hasUsage = partialResponse.getUsage() != null;
                                            if (partialResponse.getChoices() != null && !partialResponse.getChoices().isEmpty()) {
                                                ChatChoice choice = partialResponse.getChoices().get(0);
                                                if (choice != null && choice.getMessage() != null) {
                                                    String c = choice.getMessage().getContent();
                                                    hasContent = c != null && !c.isEmpty();
                                                    String r = choice.getMessage().getReasoningContent();
                                                    hasReasoning = r != null && !r.isEmpty();
                                                }
                                            }
                                            if (hasContent || hasReasoning || hasUsage) {
                                                sink.next(partialResponse);
                                            }
                                        }
                                    } catch (Exception e) {
                                        // 解析单个数据块出错时继续处理
                                    }
                                }
                            }
                        }
                        sink.complete();
                    }
                } else {
                    sink.error(new RuntimeException("HTTP Error: " + response.statusCode() + ", Body: " + new String(response.body().readAllBytes())));
                }
            } catch (IOException | InterruptedException e) {
                sink.error(new RuntimeException("Error calling OpenAI Compatible API: " + e.getMessage(), e));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public String modelName() {
        return modelName;
    }

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

    public void temperature(Double temperature) {
        this.temperature = temperature;
    }

    public void topP(Double topP) {
        this.topP = topP;
    }

    public void maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void enableThinking(boolean enableThinking) {
        this.enableThinking = enableThinking;
    }
}
