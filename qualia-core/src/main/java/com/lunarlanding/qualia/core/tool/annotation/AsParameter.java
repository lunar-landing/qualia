package com.lunarlanding.qualia.core.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注参数作为工具参数
 * 
 * <p>与 {@link AsFunctionTool} 配合使用，定义工具的输入参数。
 * 未标注此注解的方法参数将被忽略（不作为工具参数）。</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AsParameter {
    
    /**
     * 参数描述，帮助模型理解参数用途
     */
    String description();
    
    /**
     * 是否必需，默认 true
     */
    boolean required() default true;
}
