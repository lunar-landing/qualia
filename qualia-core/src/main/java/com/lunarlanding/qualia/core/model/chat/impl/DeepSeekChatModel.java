package com.lunarlanding.qualia.core.model.chat.impl;

/**
 * DeepSeek聊天模型实现
 * 继承Chat Completions基类，配置DeepSeek默认参数
 */
public class DeepSeekChatModel extends ChatCompletions {

    public DeepSeekChatModel(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String getDefaultModel() {
        return "deepseek-chat";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://api.deepseek.com/v1/chat/completions";
    }
}
