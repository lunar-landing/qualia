package com.lunarlanding.qualia.core.mcp.server;

import java.time.Duration;

/**
 * MCP 服务器配置参数
 */
public class McpServerParameters {

    /** 服务器名称 */
    private String name;

    /** 服务器版本 */
    private String version;

    /** HTTP 端点路径，如 "/mcp" */
    private String endpoint = "/mcp";

    /** 服务器说明 */
    private String instructions;

    /** Keep-Alive 间隔 */
    private Duration keepAliveInterval = Duration.ofSeconds(30);

    /** 是否禁止 DELETE 方法 */
    private boolean disallowDelete = true;

    /**
     * 创建服务器参数
     *
     * @param name    服务器名称
     * @param version 版本号
     * @return 参数实例
     */
    public static McpServerParameters create(String name, String version) {
        McpServerParameters params = new McpServerParameters();
        params.name = name;
        params.version = version;
        return params;
    }

    /**
     * 设置 HTTP 端点
     */
    public McpServerParameters withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * 设置服务器说明
     */
    public McpServerParameters withInstructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    /**
     * 设置 Keep-Alive 间隔
     */
    public McpServerParameters withKeepAliveInterval(Duration interval) {
        this.keepAliveInterval = interval;
        return this;
    }

    /**
     * 设置是否禁止 DELETE 方法
     */
    public McpServerParameters withDisallowDelete(boolean disallowDelete) {
        this.disallowDelete = disallowDelete;
        return this;
    }

    // ===== Getters =====

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getInstructions() {
        return instructions;
    }

    public Duration getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public boolean isDisallowDelete() {
        return disallowDelete;
    }
}
