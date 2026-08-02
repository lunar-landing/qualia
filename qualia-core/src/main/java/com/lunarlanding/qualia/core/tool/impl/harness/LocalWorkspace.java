package com.lunarlanding.qualia.core.tool.impl.harness;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地工作区实现
 * 基于本地文件系统的工作区
 */
public class LocalWorkspace implements Workspace {
    
    private final Path rootPath;
    
    /**
     * 创建本地工作区
     * 
     * @param rootPath 工作区根路径（字符串）
     */
    public LocalWorkspace(String rootPath) {
        this.rootPath = Paths.get(rootPath).toAbsolutePath().normalize();
    }
    
    /**
     * 创建本地工作区
     * 
     * @param rootPath 工作区根路径（Path 对象）
     */
    public LocalWorkspace(Path rootPath) {
        this.rootPath = rootPath.toAbsolutePath().normalize();
    }
    
    @Override
    public Path getRootPath() {
        return rootPath;
    }
    
    @Override
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return rootPath;
        }
        
        Path resolved = rootPath.resolve(relativePath).normalize();
        
        // 安全校验：防止路径穿越攻击
        if (!resolved.startsWith(rootPath)) {
            throw new SecurityException("路径超出工作区范围: " + relativePath);
        }
        
        return resolved;
    }
    
    @Override
    public String relativize(Path absolutePath) {
        return rootPath.relativize(absolutePath).toString();
    }
    
    @Override
    public boolean isWithinWorkspace(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(rootPath);
    }
    
    @Override
    public String toString() {
        return "LocalWorkspace{rootPath=" + rootPath + "}";
    }
}
