package com.lunarlanding.qualia.core.mcp;

/**
 * MCP 相关异常
 */
public class McpException extends RuntimeException {
    public McpException(String message) {
        super(message);
    }
    public McpException(String message, Throwable cause) {
        super(message, cause);
    }

}
