package com.lunarlanding.qualia.core.model.chat.impl;

/**
 * 阿里云DashScope聊天模型实现
 * 继承Chat Completions基类，配置DashScope默认参数
 */
public class DashscopeChatModel extends ChatCompletions {

    public DashscopeChatModel(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String getDefaultModel() {
        return "qwen3.7-plus";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    }
}
