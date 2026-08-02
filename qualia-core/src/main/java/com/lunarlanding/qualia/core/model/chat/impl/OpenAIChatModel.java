package com.lunarlanding.qualia.core.model.chat.impl;

/**
 * OpenAI官方聊天模型实现
 * 继承Chat Completions基类，配置OpenAI默认参数
 */
public class OpenAIChatModel extends ChatCompletions {

    public OpenAIChatModel(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String getDefaultModel() {
        return "gpt-4o";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://api.openai.com/v1/chat/completions";
    }
}
