package com.lunarlanding.qualia.core.tool;

import com.lunarlanding.qualia.core.tool.annotation.AsFunctionTool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * FunctionTool 扫描器
 *
 * <p>负责扫描对象中的注解方法并转换为 {@link FunctionTool} 实例。
 * 支持 {@link AsFunctionTool} 和 {@link AsParameter} 注解。</p>
 */
public class FunctionToolScanner {

    /**
     * 扫描对象中的注解方法并注册到目标列表
     *
     * @param toolProvider 包含 @AsFunctionTool 方法的对象
     * @param target 工具列表
     */
    public static void register(Object toolProvider, List<FunctionTool> target) {
        List<MethodToolAdapter> tools = scan(toolProvider);
        target.addAll(tools);
    }

    /**
     * 扫描对象中的注解方法
     *
     * @param target 包含 @AsFunctionTool 方法的对象
     * @return 工具列表
     */
    public static List<MethodToolAdapter> scan(Object target) {
        List<MethodToolAdapter> tools = new ArrayList<>();

        for (Method method : target.getClass().getDeclaredMethods()) {
            AsFunctionTool annotation = method.getAnnotation(AsFunctionTool.class);
            if (annotation == null) continue;

            method.setAccessible(true);
            tools.add(new MethodToolAdapter(target, method));
        }
        return tools;
    }
}
