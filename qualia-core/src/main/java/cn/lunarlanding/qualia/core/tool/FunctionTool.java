package cn.lunarlanding.qualia.core.tool;

import lombok.Data;

/**
 * 本地函数工具抽象基类
 *
 * <p>持有函数工具特有的元信息字段：name、description、params。
 * 后续如需扩展 MCP 类工具，可参考本类模式新增对应抽象层。</p>
 */
@Data
public abstract class FunctionTool extends Tool {

    private String name;
    private String description;
    private Parameter[] parameters;

    public FunctionTool() {}

    public FunctionTool(String name, String description, Parameter[] parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    /**
     * 生成该工具在系统 prompt 中的描述文本
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(name).append("\n");
        sb.append("描述: ").append(description).append("\n");
        if (parameters != null && parameters.length > 0) {
            sb.append("参数: ");
            for (int j = 0; j < parameters.length; j++) {
                if (j > 0) sb.append(", ");
                Parameter param = parameters[j];
                sb.append(param.getName()).append("(").append(param.getType()).append(")");
                if (Boolean.TRUE.equals(param.getRequired())) {
                    sb.append("[必填]");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
