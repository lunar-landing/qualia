package com.lunarlanding.qualia.core.mcp.server;

import com.lunarlanding.qualia.core.mcp.McpException;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP 服务器 —— 封装本地 MCP 服务器生命周期、工具注册和请求处理。
 * 支持注解式工具注册。
 * 实现 AutoCloseable，推荐使用 try-with-resources 管理生命周期。
 */
public class McpServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);

    /** 服务器配置参数 */
    private final McpServerParameters params;

    /** 注解式工具对象列表 */
    private final List<Object> toolBeans;

    /** 工具注册器 */
    private final McpToolRegistrar registrar;

    /** MCP 同步服务器 */
    private McpSyncServer server;

    /** HTTP 传输提供者 */
    private HttpServletStreamableServerTransportProvider transportProvider;

    /** 服务器是否已关闭 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** 服务器是否已启动 */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * 创建 MCP 服务器（注解式工具）
     *
     * @param params    服务器配置
     * @param toolBeans 包含 @McpTool 注解方法的对象
     */
    public McpServer(McpServerParameters params, Object... toolBeans) {
        this.params = params;
        this.toolBeans = Arrays.asList(toolBeans);
        this.registrar = new McpToolRegistrar();
    }

    /**
     * 启动 MCP 服务器
     */
    public void start() {
        if (closed.get()) {
            throw new McpException("服务器已关闭，无法重新启动");
        }

        if (started.compareAndSet(false, true)) {
            try {
                // 1. 创建 TransportProvider
                transportProvider = HttpServletStreamableServerTransportProvider.builder()
                        .mcpEndpoint(params.getEndpoint())
                        .disallowDelete(params.isDisallowDelete())
                        .keepAliveInterval(params.getKeepAliveInterval())
                        .build();

                // 2. 扫描注解工具
                var toolSpecifications = registrar.scanAnnotations(toolBeans);

                // 3. 构建 MCP 服务器
                server = io.modelcontextprotocol.server.McpServer.sync(transportProvider)
                        .serverInfo(params.getName(), params.getVersion())
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                        .instructions(params.getInstructions())
                        .tools(toolSpecifications)
                        .build();

                logger.info("MCP Server [{}] 已启动，端点: {}，注册 {} 个工具",
                        params.getName(), params.getEndpoint(), toolSpecifications.size());

            } catch (Exception e) {
                started.set(false);
                throw new McpException("MCP 服务器启动失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 获取 TransportProvider（用于自定义 Servlet 注册）
     */
    public HttpServletStreamableServerTransportProvider getTransportProvider() {
        if (transportProvider == null) {
            throw new McpException("服务器未启动，请先调用 start()");
        }
        return transportProvider;
    }

    /**
     * 获取服务器配置
     */
    public McpServerParameters getParams() {
        return params;
    }

    /**
     * 服务器是否已启动
     */
    public boolean isStarted() {
        return started.get() && !closed.get();
    }

    /**
     * 停止服务器
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (server != null) {
                try {
                    logger.info("MCP Server [{}] 已停止", params.getName());
                } catch (Exception e) {
                    logger.warn("MCP Server [{}] 停止时发生异常: {}", params.getName(), e.getMessage());
                }
            }
        }
    }
}
