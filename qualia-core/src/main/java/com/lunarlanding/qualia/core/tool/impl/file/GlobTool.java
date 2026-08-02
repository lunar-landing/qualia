package com.lunarlanding.qualia.core.tool.impl.file;

import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 文件路径搜索工具
 * 支持glob模式匹配
 */
public class GlobTool extends FunctionTool {

    private final Path rootPath;

    public GlobTool(Path rootPath) {
        super(
            "glob",
            "搜索文件路径，支持glob模式匹配",
            new Parameter[]{
                new Parameter("pattern", "glob模式（如**/*.java）", "string", true),
                new Parameter("path", "搜索路径（相对于工作区，默认为工作区根目录）", "string", false)
            }
        );
        this.rootPath = rootPath;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String patternStr = (String) arguments.get("pattern");
        if (patternStr == null || patternStr.isEmpty()) {
            return "错误：pattern 参数不能为空";
        }

        String searchPath = (String) arguments.get("path");
        Path searchDir;
        if (searchPath == null || searchPath.isEmpty()) {
            searchDir = rootPath;
        } else {
            searchDir = rootPath.resolve(searchPath);
        }

        // 安全检查：搜索路径必须在工作区内
        if (!searchDir.startsWith(rootPath)) {
            return "错误：搜索路径超出工作区范围";
        }

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + patternStr);
            List<String> results = new ArrayList<>();
            
            // 遍历目录
            Files.walkFileTree(searchDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 获取相对路径
                    Path relativePath = rootPath.relativize(file);
                    
                    // 检查是否匹配glob模式
                    if (matcher.matches(relativePath)) {
                        results.add(relativePath.toString());
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
            });
            
            // 排序结果
            Collections.sort(results);
            
            if (results.isEmpty()) {
                return "未找到匹配的文件";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(results.size()).append(" 个文件:\n");
            for (String result : results) {
                sb.append(result).append("\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            return "错误：搜索失败 - " + e.getMessage();
        }
    }
}
