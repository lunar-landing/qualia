package com.lunarlanding.qualia.core.skill;

import java.nio.file.Path;

/**
 * 技能脚本元数据
 * 描述技能中的一个可执行脚本文件
 */
public class SkillScript {
    private final String name;
    private final String description;
    private final Path scriptPath;

    public SkillScript(String name, String description, Path scriptPath) {
        this.name = name;
        this.description = description;
        this.scriptPath = scriptPath;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Path getScriptPath() {
        return scriptPath;
    }
}
