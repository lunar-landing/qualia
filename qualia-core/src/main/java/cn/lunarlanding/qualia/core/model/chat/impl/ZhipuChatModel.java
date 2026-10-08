package cn.lunarlanding.qualia.core.model.chat.impl;

import com.alibaba.fastjson2.JSONObject;

/**
 * 智谱GLM聊天模型实现（按量付费）
 * 继承Chat Completions基类，配置智谱开放平台默认参数
 */
public class ZhipuChatModel extends ChatCompletions {

    public ZhipuChatModel(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String getDefaultModel() {
        return "glm-4.6";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    }

    @Override
    protected void addThinkingParams(JSONObject requestBody) {
        // 智谱格式: {"thinking": {"type": "enabled/disabled"}}
        JSONObject thinking = new JSONObject();
        thinking.put("type", enableThinking ? "enabled" : "disabled");
        requestBody.put("thinking", thinking);
    }
}
