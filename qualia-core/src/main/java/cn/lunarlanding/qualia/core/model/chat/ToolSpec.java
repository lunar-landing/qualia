package cn.lunarlanding.qualia.core.model.chat;

/**
 * 发送给模型的工具签名（OpenAI function calling 协议的 tools 数组项）
 *
 * <p>与 {@code cn.lunarlanding.qualia.core.tool.FunctionTool} 解耦：
 * 模型层不反向依赖工具层，由调用方（智能体）完成转换。</p>
 */
public class ToolSpec {

    /** 工具名称 */
    private final String name;

    /** 工具描述 */
    private final String description;

    /** 参数 JSON Schema 字符串 */
    private final String parametersSchema;

    public ToolSpec(String name, String description, String parametersSchema) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParametersSchema() {
        return parametersSchema;
    }
}
