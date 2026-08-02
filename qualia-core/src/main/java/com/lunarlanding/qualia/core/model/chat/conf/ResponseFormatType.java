package com.lunarlanding.qualia.core.model.chat.conf;

/**
 * 响应格式类型枚举
 * 用于指定模型返回内容的格式
 */
public enum ResponseFormatType {

    /**
     * 输出文字回复（默认）
     */
    TEXT("text"),

    /**
     * 输出标准格式的JSON字符串
     * 需要在提示词中明确指示模型输出JSON
     */
    JSON_OBJECT("json_object");

    private final String value;

    ResponseFormatType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值查找对应的枚举
     * @param value 字符串值（"text" 或 "json_object"）
     * @return 对应的枚举值，未找到则返回 null
     */
    public static ResponseFormatType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ResponseFormatType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
