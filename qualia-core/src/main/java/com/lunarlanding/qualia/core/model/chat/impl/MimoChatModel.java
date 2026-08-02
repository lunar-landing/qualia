package com.lunarlanding.qualia.core.model.chat.impl;

import com.alibaba.fastjson2.JSONObject;

/**
 * 小米MiMo聊天模型实现（按量计费）
 * 继承Chat Completions基类，配置小米默认参数
 */
public class MimoChatModel extends ChatCompletions {

    public MimoChatModel(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String getDefaultModel() {
        return "MiMo";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://api.xiaomimimo.com/v1/chat/completions";
    }

    @Override
    protected void addThinkingParams(JSONObject requestBody) {
        // 小米格式: {"thinking": {"type": "enabled/disabled"}}
        JSONObject thinking = new JSONObject();
        thinking.put("type", enableThinking ? "enabled" : "disabled");
        requestBody.put("thinking", thinking);
    }
}
