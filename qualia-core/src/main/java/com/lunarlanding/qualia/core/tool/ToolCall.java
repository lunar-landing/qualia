package com.lunarlanding.qualia.core.tool;

import java.util.Map;

/**
 * 从 LLM 响应中解析出的单次工具调用指令
 *
 * @param toolName  工具名称
 * @param arguments 工具参数
 */
public record ToolCall(String toolName, Map<String, Object> arguments) {}
