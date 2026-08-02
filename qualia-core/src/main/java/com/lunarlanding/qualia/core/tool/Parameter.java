package com.lunarlanding.qualia.core.tool;

import lombok.Data;

@Data
public class Parameter {

    /**
     * 参数名称
     * */
    private String name;

    /**
     * 描述
     * */
    private String description;

    /**
     * 参数类型
     * */
    private String type;

    /**
     * 是否必填
     * */
    private Boolean required;

    public Parameter(String name, String description, String type, Boolean required) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
    }

    public Parameter() {}
}
