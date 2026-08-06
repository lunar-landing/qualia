package com.lunarlanding.qualia.claw;

/**
 * 单个模型配置
 */
public class ClawModelConfig {

    /** 模型唯一标识 */
    private String name;

    /** 厂家标识：dashscope, openai, claude, deepseek 等 */
    private String provider;

    /** 服务类型：pay-as-you-go（按量）, token-plan, coding-plan, free, enterprise，决定对接协议与 baseUrl */
    private String type;

    /** API密钥，支持 ${ENV_VAR} 格式 */
    private String apiKey;

    /** API基础地址 */
    private String baseUrl;

    /** 模型名称：qwen-max, gpt-4, claude-3.5-sonnet 等 */
    private String model;

    public ClawModelConfig() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

}
