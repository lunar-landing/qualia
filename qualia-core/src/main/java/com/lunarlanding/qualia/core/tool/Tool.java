package com.lunarlanding.qualia.core.tool;

import java.util.Map;

/**
 * 工具抽象基类。
 *
 * <p>定义工具类型标识契约和执行契约。
 * 元信息字段（name、description、parameters）由具体子类（如 {@link FunctionTool}）持有并实现。</p>
 */
public abstract class Tool {

    public abstract String execute(Map<String, Object> arguments);
}
