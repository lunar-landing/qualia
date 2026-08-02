package com.lunarlanding.qualia.core.tool;

import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;
import com.lunarlanding.qualia.core.tool.annotation.AsFunctionTool;
import com.lunarlanding.qualia.core.tool.annotation.AsParameter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * FunctionTool 适配器
 *
 * <p>将标注了 {@code @AsFunctionTool} 的普通 Java 方法适配为 {@link FunctionTool} 实例。
 * 通过反射执行目标方法。</p>
 */
public class MethodToolAdapter extends FunctionTool {

    private static final Map<Class<?>, Function<String, Object>> TYPE_CONVERTERS = Map.of(
            int.class, Integer::parseInt,
            Integer.class, Integer::parseInt,
            long.class, Long::parseLong,
            Long.class, Long::parseLong,
            boolean.class, Boolean::parseBoolean,
            Boolean.class, Boolean::parseBoolean,
            double.class, Double::parseDouble,
            Double.class, Double::parseDouble,
            float.class, Float::parseFloat,
            Float.class, Float::parseFloat
    );

    private final Object target;
    private final Method method;

    /**
     * 创建 FunctionToolAdapter，内部解析 @AsFunctionTool 和 @AsParameter 注解
     *
     * @param target 包含注解方法的对象实例
     * @param method 标注了 @AsFunctionTool 的方法
     */
    public MethodToolAdapter(Object target, Method method) {
        super();
        this.target = target;
        this.method = method;

        // 内部解析注解
        AsFunctionTool annotation = method.getAnnotation(AsFunctionTool.class);
        if (annotation == null) {
            throw new IllegalArgumentException("方法 " + method.getName() + " 未标注 @AsFunctionTool");
        }

        this.setName(annotation.name());
        this.setDescription(annotation.description());
        this.setParameters(parseParameters(method));
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            // 获取方法参数列表
            java.lang.reflect.Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];

            // 按参数顺序从 arguments Map 中取值
            for (int i = 0; i < params.length; i++) {
                Object value = arguments.get(params[i].getName());
                args[i] = convertType(value, params[i].getType());
            }

            Object result = method.invoke(target, args);
            return Objects.toString(result, "");

        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 类型转换：将 Map 中的值转换为方法参数类型
     */
    private Object convertType(Object value, Class<?> type) {
        if (value == null) {
            return null;
        }

        Function<String, Object> converter = TYPE_CONVERTERS.get(type);
        if (converter != null) {
            return converter.apply(value.toString());
        }

        return value;
    }

    /**
     * 获取目标对象
     */
    public Object getTarget() {
        return target;
    }

    /**
     * 获取目标方法
     */
    public Method getMethod() {
        return method;
    }

    /**
     * 解析方法参数上的 @AsParameter 注解
     * 未标注的参数跳过（不作为工具参数）
     */
    private static Parameter[] parseParameters(Method method) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        List<Parameter> result = new ArrayList<>();

        for (int i = 0; i < params.length; i++) {
            AsParameter ap = params[i].getAnnotation(AsParameter.class);
            if (ap == null) continue;

            String name = params[i].getName();
            String type = mapType(method.getParameterTypes()[i]);

            result.add(new Parameter(name, ap.description(), type, ap.required()));
        }

        return result.toArray(new Parameter[0]);
    }

    /**
     * Java 类型映射为 JSON Schema 类型
     */
    private static String mapType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "number";
        if (type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == long.class || type == Long.class) return "number";
        if (type == double.class || type == Double.class) return "number";
        return "string"; // 默认
    }
}
