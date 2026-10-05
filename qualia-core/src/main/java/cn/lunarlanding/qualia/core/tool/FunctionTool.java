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
     * 是否只读工具（无副作用，如查询/检索类）。
     *
     * <p>默认 {@code false}：注解注册的自定义工具、MCP 远端工具等未知工具
     * 一律视为可能有副作用，只读问答模式（CallOptions.readOnly）下不进入
     * prompt 工具列表，执行前也会被拦截。无副作用的工具覆写返回 {@code true}。</p>
     */
    public boolean isReadOnly() {
        return false;
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
