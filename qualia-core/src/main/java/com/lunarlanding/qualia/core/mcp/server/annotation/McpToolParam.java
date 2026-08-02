package com.lunarlanding.qualia.core.mcp.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法参数为 MCP 工具参数
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface McpToolParam {

    /** 是否必填，默认 true */
    boolean required() default true;

    /** 参数描述 */
    String description() default "";
}
