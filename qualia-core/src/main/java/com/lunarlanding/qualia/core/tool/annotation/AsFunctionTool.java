package com.lunarlanding.qualia.core.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注方法作为 FunctionTool
 * 
 * <p>将普通 Java 方法转换为可被 ReActAgent 调用的工具。
 * 方法必须返回 String 类型。</p>
 * 
 * <pre>
 * {@literal @}AsFunctionTool(name = "get_weather", description = "获取天气信息")
 * public String getWeather(
 *     {@literal @}AsParameter(description = "城市名称") String city
 * ) {
 *     return "{\"temp\": 25}";
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AsFunctionTool {
    
    /**
     * 工具名称，如 "get_weather"
     */
    String name();
    
    /**
     * 工具描述，帮助模型理解工具用途
     */
    String description();
}
