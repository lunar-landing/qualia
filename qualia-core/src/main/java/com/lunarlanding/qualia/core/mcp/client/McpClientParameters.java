package com.lunarlanding.qualia.core.mcp.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端连接参数配置
 * 支持 STDIO、HTTP_SSE 和 Streamable HTTP 三种传输方式
 */
public class McpClientParameters {

    /**
     * 传输类型
     */
    public enum TransportType {
        /** 标准输入输出（本地进程） */
        STDIO,
        /** Streamable HTTP 传输（远程服务，如 DashScope MCP） */
        STREAMABLE_HTTP,
        /** HTTP SSE 传输（远程服务） */
        HTTP_SSE
    }

    /** 传输类型 */
    private TransportType transportType;

    /** 服务器名称 */
    private String name;

    /** STDIO: 启动命令 */
    private String command;

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 30;

    /** HTTP_SSE: 自定义请求头 */
    private Map<String, String> headers;

    /** STDIO: 命令参数 */
    private List<String> args;

    /** HTTP_SSE: 服务端 URL */
    private String url;

    /**
     * 创建 STDIO 传输参数
     */
    public static McpClientParameters stdio(String command, List<String> args) {
        McpClientParameters params = new McpClientParameters();
        params.transportType = TransportType.STDIO;
        params.command = command;
        params.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
        return params;
    }

    /**
     * 创建 HTTP_SSE 传输参数
     */
    public static McpClientParameters httpSse(String url) {
        McpClientParameters params = new McpClientParameters();
        params.transportType = TransportType.HTTP_SSE;
        params.url = url;
        params.headers = new HashMap<>();
        return params;
    }

    /**
     * 创建 Streamable HTTP 传输参数（适用于 DashScope MCP 等端点）
     */
    public static McpClientParameters streamableHttp(String url) {
        McpClientParameters params = new McpClientParameters();
        params.transportType = TransportType.STREAMABLE_HTTP;
        params.url = url;
        params.headers = new HashMap<>();
        return params;
    }

    /**
     * 设置服务器名称
     */
    public McpClientParameters withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * 设置连接超时
     */
    public McpClientParameters withConnectTimeout(int seconds) {
        this.connectTimeoutSeconds = seconds;
        return this;
    }

    /**
     * 添加自定义请求头（仅 HTTP_SSE）
     */
    public McpClientParameters withHeader(String key, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(key, value);
        return this;
    }

    /**
     * 批量设置自定义请求头（仅 HTTP_SSE）
     */
    public McpClientParameters withHeaders(Map<String, String> headers) {
        if (headers != null) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.putAll(headers);
        }
        return this;
    }

    // ===== Getters =====

    public TransportType getTransportType() {
        return transportType;
    }

    public String getName() {
        return name;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }
}
