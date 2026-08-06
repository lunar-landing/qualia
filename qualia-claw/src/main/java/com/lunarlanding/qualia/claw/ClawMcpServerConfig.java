package com.lunarlanding.qualia.claw;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP服务器配置
 */
public class ClawMcpServerConfig {

    /** 服务唯一标识 */
    private String name;

    /** 传输方式：streamable-http, http-sse, stdio */
    private String transport;

    /** 服务地址（http-sse / streamable-http 使用） */
    private String url;

    /** 自定义请求头（http-sse / streamable-http 使用） */
    private Map<String, String> headers;

    /** 是否启用（默认 true） */
    private boolean enabled = true;

    public ClawMcpServerConfig() {
        this.headers = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
