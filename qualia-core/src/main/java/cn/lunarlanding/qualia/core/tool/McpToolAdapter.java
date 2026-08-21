package cn.lunarlanding.qualia.core.tool;

import cn.lunarlanding.qualia.core.mcp.client.McpClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具代理 —— 将远程 MCP Server 的一个 Tool 桥接为 qualia 的 ITool。
 * 每个 McpTool 对应远端 MCP 服务器上的一个工具
 */
public class McpToolAdapter extends FunctionTool {

    /** MCP 工具名称 */
    private final String mcpToolName;

    /** 所属 MCP 服务器的连接 */
    private final McpClient connection;

    /**
     * @param mcpTool         MCP SDK 返回的 Tool 描述
     * @param connection      该工具所属的 MCP 服务器连接
     */
    @SuppressWarnings("unchecked")
    public McpToolAdapter(McpSchema.Tool mcpTool, McpClient connection) {
        super();

        this.mcpToolName = mcpTool.name();
        this.connection = connection;

        // 填充 ITool 的元信息
        this.setName(mcpTool.name());
        this.setDescription(mcpTool.description() != null ? mcpTool.description() : "");

        // 将 MCP JSON Schema 参数转换为 qualia Parameter 数组
        // 注意：0.18.0 中 JsonSchema.properties() 返回 Map<String, Object>（原始 Map）
        McpSchema.JsonSchema inputSchema = mcpTool.inputSchema();
        if (inputSchema != null && inputSchema.properties() != null) {
            List<Parameter> paramList = new ArrayList<>();
            for (Map.Entry<String, Object> entry : inputSchema.properties().entrySet()) {
                String paramName = entry.getKey();
                Object rawSchema = entry.getValue();

                String type = "string";
                String description = new String();

                // 解析嵌套的 JSON Schema（为原始 Map<String, Object>）
                if (rawSchema instanceof Map<?, ?> schemaMap) {
                    Object typeObj = schemaMap.get("type");
                    if (typeObj instanceof String s) {
                        type = s;
                    }
                    Object descObj = schemaMap.get("description");
                    if (descObj instanceof String s) {
                        description = s;
                    }
                }

                boolean required = inputSchema.required() != null && inputSchema.required().contains(paramName);
                paramList.add(new Parameter(paramName, description, type, required));
            }
            this.setParameters(paramList.toArray(new Parameter[0]));
        }
    }

    /**
     * 执行 MCP 工具调用 —— 委托给远端 MCP 服务器
     */
    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            McpSchema.CallToolResult result = connection.callTool(mcpToolName, arguments);
            if (result == null) {
                return "MCP 工具 " + mcpToolName + " 返回了空结果";
            }
            return extractContent(result);
        } catch (Exception e) {
            return "MCP 工具调用失败: " + e.getMessage();
        }
    }

    /**
     * 提取 MCP 调用结果中的文本内容
     */
    private String extractContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return result.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent text) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(text.text());
            } else {
                if (sb.length() > 0) sb.append("\n");
                sb.append(content.toString());
            }
        }
        return sb.toString();
    }

    /**
     * 返回 MCP 工具名称
     */
    public String getMcpToolName() {
        return mcpToolName;
    }

    /**
     * 返回所属连接
     */
    public McpClient getConnection() {
        return connection;
    }
}
