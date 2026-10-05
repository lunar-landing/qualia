package cn.lunarlanding.qualia.core.agent;

import lombok.Data;

/**
 * 单次调用的会话选项（请求级参数，非 Agent 实例状态）
 *
 * <p>字段为 null 时取默认值；随每次 {@code callStream} 传入，在方法入口立即解析为局部变量，
 * 单实例多会话并行时互不污染（与 detectedLanguage 的请求级传递先例一致）。
 * 后续正交演进（如 maxIterations 覆盖）直接加字段，不破坏旧重载。</p>
 */
@Data
public class CallOptions {

    /** 只读问答模式：仅查询类工具（isReadOnly=true）进入 prompt 工具列表，其余工具执行前被拦截 */
    private Boolean readOnly;

    public static CallOptions readOnly() {
        CallOptions options = new CallOptions();
        options.setReadOnly(true);
        return options;
    }
}
