package cn.lunarlanding.qualia.core.mcp.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法为 MCP 工具
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpTool {

    /** 工具名称，默认使用方法名的 snake_case 形式 */
    String name() default "";

    /** 工具标题（可选） */
    String title() default "";

    /** 工具描述 */
    String description() default "";
}
