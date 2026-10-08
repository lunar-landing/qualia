package cn.lunarlanding.qualia.core.model.chat.impl;

import com.alibaba.fastjson2.JSONObject;

/**
 * 智谱GLM聊天模型实现（令牌计划/Coding Plan）
 * 继承Chat Completions基类，配置智谱令牌计划专用端点
 */
public class ZhipuCodingPlanChatModel extends ChatCompletions {

    public ZhipuCodingPlanChatModel(String apiKey) {
        super(apiKey);
    }

    @Override
    protected String getDefaultModel() {
        return "glm-4.6";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions";
    }

    @Override
    protected void addThinkingParams(JSONObject requestBody) {
        // 智谱格式: {"thinking": {"type": "enabled/disabled"}}
        JSONObject thinking = new JSONObject();
        thinking.put("type", enableThinking ? "enabled" : "disabled");
        requestBody.put("thinking", thinking);
    }
}
