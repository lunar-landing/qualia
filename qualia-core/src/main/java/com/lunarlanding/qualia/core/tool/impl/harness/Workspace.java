package com.lunarlanding.qualia.core.tool.impl.harness;

import java.nio.file.Path;

/**
 * 工作区接口
 * 定义文件系统边界和路径操作规范
 * 
 * 实现类：
 * - LocalWorkspace：本地文件系统
 * - DockerWorkspace：Docker 容器（待实现）
 * - RemoteWorkspace：远程服务器（待实现）
 */
public interface Workspace {
    
    /**
     * 获取工作区根路径
     */
    Path getRootPath();
    
    /**
     * 解析相对路径为绝对路径（带安全校验）
     * 
     * @param relativePath 相对路径
     * @return 绝对路径
     * @throws SecurityException 如果路径超出工作区范围
     */
    Path resolve(String relativePath);
    
    /**
     * 将绝对路径转换为相对路径
     * 
     * @param absolutePath 绝对路径
     * @return 相对路径
     */
    String relativize(Path absolutePath);
    
    /**
     * 检查路径是否在工作区内
     * 
     * @param path 待检查的路径
     * @return true 如果路径在工作区内
     */
    boolean isWithinWorkspace(Path path);
    
    /**
     * 获取配置目录（.qualia）
     */
    default Path getConfigDir() {
        return getRootPath().resolve(".qualia");
    }
    
    /**
     * 获取 Memory 目录（.qualia/memory）
     */
    default Path getMemoryDir() {
        return getConfigDir().resolve("memory");
    }
    
    /**
     * 获取 Skills 目录（.qualia/skills）
     *
     * 实现可返回 null 表示禁用工作区级技能（如技能统一由产品级全局目录管理）
     */
    default Path getSkillsDir() {
        return getConfigDir().resolve("skills");
    }
    
    /**
     * 获取 AGENT.md 文件路径（.qualia/AGENT.md）
     *
     * 实现可返回 null 表示禁用工作区级提示词（如人设由产品配置接管）
     */
    default Path getAgentFile() {
        return getConfigDir().resolve("AGENT.md");
    }
}
