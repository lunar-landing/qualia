package com.lunarlanding.qualia.core.mcp.server;

import com.lunarlanding.qualia.core.mcp.server.annotation.McpTool;
import com.lunarlanding.qualia.core.mcp.server.annotation.McpToolParam;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具注册器
 * 负责扫描 @McpTool 注解方法并转换为 MCP 工具规格
 */
public class McpToolRegistrar {

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 扫描注解式工具
     *
     * @param toolBeans 包含 @McpTool 注解方法的对象列表
     * @return 工具规格列表
     */
    public List<McpServerFeatures.SyncToolSpecification> scanAnnotations(List<Object> toolBeans) {
        List<McpServerFeatures.SyncToolSpecification> specifications = new ArrayList<>();
        for (Object bean : toolBeans) {
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
            ReflectionUtils.doWithMethods(targetClass, method -> {
                McpTool annotation = method.getAnnotation(McpTool.class);
                if (annotation != null) {
                    specifications.add(toSpecification(bean, method, annotation));
                }
            });
        }
        return specifications;
    }

    private McpServerFeatures.SyncToolSpecification toSpecification(Object bean, Method method, McpTool annotation) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(toolName(method, annotation))
                .title(annotation.title().isBlank() ? null : annotation.title())
                .description(annotation.description())
                .inputSchema(inputSchema(method))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        Object result = invoke(bean, method, request.arguments());
                        return McpSchema.CallToolResult.builder()
                                .addTextContent(result == null ? "null" : String.valueOf(result))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        Throwable cause = unwrap(e);
                        return McpSchema.CallToolResult.builder()
                                .addTextContent(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())
                                .isError(true)
                                .build();
                    }
                })
                .build();
    }

    private Object invoke(Object bean, Method method, Map<String, Object> arguments) throws Exception {
        Method invocableMethod = ReflectionUtils.findMethod(bean.getClass(), method.getName(), method.getParameterTypes());
        if (invocableMethod == null) {
            invocableMethod = method;
        }
        ReflectionUtils.makeAccessible(invocableMethod);
        return invocableMethod.invoke(bean, buildArguments(method, arguments));
    }

    private Object[] buildArguments(Method method, Map<String, Object> arguments) {
        Parameter[] parameters = method.getParameters();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        Object[] values = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String name = parameterNames != null ? parameterNames[i] : parameters[i].getName();
            Object raw = arguments == null ? null : arguments.get(name);
            values[i] = convert(raw, parameters[i].getType());
        }
        return values;
    }

    private Object convert(Object raw, Class<?> targetType) {
        if (raw == null) {
            return null;
        }
        if (targetType.isInstance(raw)) {
            return raw;
        }
        String text = String.valueOf(raw);
        if (targetType == String.class) {
            return text;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return text.isBlank() ? null : Integer.parseInt(text);
        }
        if (targetType == Long.class || targetType == long.class) {
            return text.isBlank() ? null : Long.parseLong(text);
        }
        if (targetType == Double.class || targetType == double.class) {
            return text.isBlank() ? null : Double.parseDouble(text);
        }
        if (targetType == BigDecimal.class) {
            return text.isBlank() ? null : new BigDecimal(text);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return text.isBlank() ? null : Boolean.parseBoolean(text);
        }
        return raw;
    }

    private McpSchema.JsonSchema inputSchema(Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String name = parameterNames != null ? parameterNames[i] : parameter.getName();
            McpToolParam annotation = parameter.getAnnotation(McpToolParam.class);
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", jsonType(parameter.getType()));
            if (annotation != null && !annotation.description().isBlank()) {
                property.put("description", annotation.description());
            }
            properties.put(name, property);
            if (annotation == null || annotation.required()) {
                required.add(name);
            }
        }

        return new McpSchema.JsonSchema("object", properties, required, false, null, null);
    }

    private String jsonType(Class<?> type) {
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "integer";
        }
        if (type == BigDecimal.class || type == Double.class || type == double.class || type == Float.class || type == float.class) {
            return "number";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        return "string";
    }

    private String toolName(Method method, McpTool annotation) {
        if (!annotation.name().isBlank()) {
            return annotation.name();
        }
        return camelToSnake(method.getName());
    }

    private String camelToSnake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private Throwable unwrap(Exception e) {
        if (e instanceof InvocationTargetException invocationTargetException && invocationTargetException.getTargetException() != null) {
            return invocationTargetException.getTargetException();
        }
        return e;
    }
}
