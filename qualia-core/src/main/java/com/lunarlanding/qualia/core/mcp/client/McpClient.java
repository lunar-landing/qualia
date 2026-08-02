package com.lunarlanding.qualia.core.mcp.client;

import com.lunarlanding.qualia.core.mcp.McpException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;
import io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.McpServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP 客户端 —— 封装与单个 MCP 服务器的连接生命周期、工具发现和 RPC 调用。
 * 支持 STDIO（本地进程）、HTTP_SSE 和 Streamable HTTP 三种传输方式。
 * 实现 AutoCloseable，推荐使用 try-with-resources 管理生命周期。
 */
public class McpClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpClient.class);

    /**
     * 在类加载时通过反射直接注册 JacksonMcpJsonMapperSupplier，
     * 彻底绕过 SPI ServiceLoader，避免运行环境中找不到 SPI 实现的错误。
     */
    static {
        try {
            // 触发 McpJsonDefaults 的静态字段初始化
            new McpJsonDefaults();

            // 通过反射获取 mcpMapperServiceLoader 并注入 Supplier
            Field mapperLoaderField = McpJsonDefaults.class.getDeclaredField("mcpMapperServiceLoader");
            mapperLoaderField.setAccessible(true);
            @SuppressWarnings("unchecked")
            McpServiceLoader<McpJsonMapperSupplier, McpJsonMapper> mapperLoader = (McpServiceLoader<McpJsonMapperSupplier, McpJsonMapper>) mapperLoaderField.get(null);
            mapperLoader.setSupplier(new JacksonMcpJsonMapperSupplier());

            // 同样注入 JsonSchemaValidatorSupplier，避免 McpClient.build() 时 SPI 查找失败
            Field validatorLoaderField = McpJsonDefaults.class.getDeclaredField("mcpValidatorServiceLoader");
            validatorLoaderField.setAccessible(true);
            @SuppressWarnings("unchecked")
            McpServiceLoader<JsonSchemaValidatorSupplier, JsonSchemaValidator> validatorLoader = (McpServiceLoader<JsonSchemaValidatorSupplier, JsonSchemaValidator>) validatorLoaderField.get(null);
            validatorLoader.setSupplier(new JacksonJsonSchemaValidatorSupplier());

            logger.info("MCP JsonMapper & JsonSchemaValidator suppliers registered via reflection (SPI bypass)");
        } catch (Exception e) {
            logger.warn("Failed to register MCP suppliers via reflection: {}", e.getMessage());
        }
    }

    /** 连接参数 */
    private final McpClientParameters params;

    /** MCP 同步客户端 */
    private McpSyncClient client;

    /** 从该服务器发现的所有工具（MCP 协议格式） */
    private final List<McpSchema.Tool> tools = new ArrayList<>();

    /** 连接是否已关闭 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * @param params 连接参数
     */
    public McpClient(McpClientParameters params) {
        this.params = params;
    }

    /**
     * 建立连接并发现工具
     */
    public void connect() {

        if (closed.get()) {
            throw new McpException("连接已关闭，无法重新连接");
        }

        if (client != null) {
            logger.warn("MCP 连接已建立，跳过重复连接: {}", params.getName());
            return;
        }

        try {

            switch (params.getTransportType()) {
                case STDIO -> connectStdio();
                case STREAMABLE_HTTP -> connectStreamableHttp();
                case HTTP_SSE -> connectHttpSse();
            }

            // 发现工具（存储原始 MCP 工具，转换逻辑在 Agent 层）
            McpSchema.ListToolsResult result = client.listTools();
            if (result != null && result.tools() != null) {
                for (McpSchema.Tool mcpTool : result.tools()) {
                    tools.add(mcpTool);
                    logger.info("MCP [{}] 发现工具: {}", params.getName(), mcpTool.name());
                }
            }
            logger.info("MCP [{}] 连接成功，共发现 {} 个工具", params.getName(), tools.size());

        } catch (Throwable e) {
            // ServiceConfigurationError（如 No McpJsonMapperSupplier available）继承 Error 而非 Exception，
            // 必须用 Throwable 才能捕获，否则会直接穿透到上层。
            throw new McpException("MCP 连接失败: " + params.getName() + ":" + e.getMessage());
        }
    }

    /**
     * 通过 STDIO 连接（本地进程）
     */
    private void connectStdio() {
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(params.getCommand());
        if (params.getArgs() != null) {
            commandWithArgs.addAll(params.getArgs());
        }

        ServerParameters serverParams = ServerParameters.builder(commandWithArgs.get(0))
                .args(commandWithArgs.subList(1, commandWithArgs.size()))
                .build();

        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        StdioClientTransport transport = new StdioClientTransport(serverParams, jsonMapper);

        client = io.modelcontextprotocol.client.McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(params.getConnectTimeoutSeconds()))
                .build();

        logger.info("MCP [{}] 通过 STDIO 连接: {}", params.getName(), params.getCommand());
    }

    /**
     * 通过 HTTP_SSE 连接（远程服务）
     */
    private void connectHttpSse() {
        URI uri = URI.create(params.getUrl());
        String baseUri = uri.getScheme() + "://" + uri.getAuthority();
        String ssePath = uri.getPath();

        var builder = HttpClientSseClientTransport.builder(baseUri)
                .sseEndpoint(ssePath);

        // 注入自定义请求头（如 Authorization）
        if (params.getHeaders() != null && !params.getHeaders().isEmpty()) {
            builder.customizeRequest(httpRequest -> {
                for (Map.Entry<String, String> entry : params.getHeaders().entrySet()) {
                    httpRequest.header(entry.getKey(), entry.getValue());
                }
            });
        }

        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        HttpClientSseClientTransport transport = builder.jsonMapper(jsonMapper).build();

        client = io.modelcontextprotocol.client.McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(params.getConnectTimeoutSeconds()))
                .build();

        logger.info("MCP [{}] 通过 HTTP_SSE 连接: {}", params.getName(), params.getUrl());
    }

    /**
     * 通过 Streamable HTTP 连接（远程服务，如 DashScope MCP）
     */
    private void connectStreamableHttp() {
        // 将完整 URL 拆分为 baseUri（scheme + authority）和 endpoint（path）
        // 例如: https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp
        //   → baseUri = https://dashscope.aliyuncs.com
        //   → endpoint = /api/v1/mcps/WebSearch/mcp
        URI uri = URI.create(params.getUrl());
        String baseUri = uri.getScheme() + "://" + uri.getAuthority();
        String endpoint = uri.getPath();

        var builder = HttpClientStreamableHttpTransport.builder(baseUri)
                .endpoint(endpoint);

        // 注入自定义请求头（如 Authorization）
        if (params.getHeaders() != null && !params.getHeaders().isEmpty()) {
            builder.customizeRequest(httpRequest -> {
                for (Map.Entry<String, String> entry : params.getHeaders().entrySet()) {
                    httpRequest.header(entry.getKey(), entry.getValue());
                }
            });
        }
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        HttpClientStreamableHttpTransport transport = builder
                .jsonMapper(jsonMapper)
                .connectTimeout(Duration.ofSeconds(params.getConnectTimeoutSeconds()))
                .build();

        client = io.modelcontextprotocol.client.McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(params.getConnectTimeoutSeconds()))
                .build();

        logger.info("MCP [{}] 通过 Streamable HTTP 连接: {}", params.getName(), params.getUrl());
    }

    /**
     * 调用远程 MCP 工具
     *
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 调用结果
     */
    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        if (client == null) {
            throw new McpException("MCP 连接未建立，请先调用 connect()");
        }
        if (closed.get()) {
            throw new McpException("MCP 连接已关闭");
        }
        return client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
    }

    /**
     * 获取从该服务器发现的所有工具（MCP 协议格式）
     */
    public List<McpSchema.Tool> getTools() {
        return new ArrayList<>(tools);
    }

    /**
     * 获取发现的工具数量
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * 获取连接参数
     */
    public McpClientParameters getParams() {
        return params;
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return client != null && !closed.get();
    }

    /**
     * 关闭连接
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (client != null) {
                try {
                    client.close();
                    logger.info("MCP [{}] 连接已关闭", params.getName());
                } catch (Exception e) {
                    logger.warn("MCP [{}] 关闭连接时发生异常: {}", params.getName(), e.getMessage());
                }
            }
        }
    }
}
