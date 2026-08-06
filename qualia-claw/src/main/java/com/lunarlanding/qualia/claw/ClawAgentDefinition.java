package com.lunarlanding.qualia.claw;

import java.util.List;

/**
 * 智能体定义：每个智能体绑定一个独立工作区，是 qualia-claw 的最小工位单元
 */
public class ClawAgentDefinition {

    /** 智能体唯一标识（UUID） */
    private String id;

    /** 显示名称 */
    private String name;

    /** 展示表情（如 🦞 / 📊），前端头像用 */
    private String emoji;

    /** 职能角色描述，叠加到 system prompt（如"资深 HR，负责招聘与员工关系"） */
    private String role;

    /** 工作区绝对路径 */
    private String workspacePath;

    /** 所用模型名称（可选，缺省用全局 defaultModel） */
    private String model;

    /** 引用的全局技能白名单（按名称）；null = 引用全部（存量智能体），空列表 = 不引用 */
    private List<String> skills;

    /** 引用的全局 MCP 服务器白名单（按名称）；null = 引用全部（存量智能体），空列表 = 不引用 */
    private List<String> mcpServers;

    /** 创建时间戳 */
    private long createdAt;

    public ClawAgentDefinition() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(List<String> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
