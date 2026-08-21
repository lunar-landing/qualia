package cn.lunarlanding.qualia.core.tool.impl.file;

import cn.lunarlanding.qualia.core.tool.FunctionTool;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件内容搜索工具
 * 支持正则表达式匹配，返回文件路径+行号+匹配内容
 */
public class GrepTool extends FunctionTool {

    private final Path rootPath;

    public GrepTool(Path rootPath) {
        super(
            "grep",
            "搜索文件内容，支持正则表达式匹配",
            new Parameter[]{
                new Parameter("pattern", "正则表达式模式", "string", true),
                new Parameter("path", "搜索路径（相对于工作区，默认为工作区根目录）", "string", false),
                new Parameter("glob", "文件过滤模式（如*.java）", "string", false),
                new Parameter("max_results", "最大结果数，默认100", "integer", false)
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

        String glob = (String) arguments.get("glob");
        Integer maxResults = arguments.containsKey("max_results") ? ((Number) arguments.get("max_results")).intValue() : 100;

        try {
            Pattern pattern = Pattern.compile(patternStr);
            List<String> results = new ArrayList<>();
            
            // 遍历目录
            Files.walkFileTree(searchDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 检查文件过滤模式
                    if (glob != null && !glob.isEmpty()) {
                        String fileName = file.getFileName().toString();
                        if (!matchesGlob(fileName, glob)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    
                    // 跳过二进制文件
                    if (isBinaryFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    
                    // 搜索文件内容
                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            Matcher matcher = pattern.matcher(lines.get(i));
                            if (matcher.find()) {
                                String relativePath = rootPath.relativize(file).toString();
                                results.add(relativePath + ":" + (i + 1) + ": " + lines.get(i).trim());
                                
                                if (results.size() >= maxResults) {
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        }
                    } catch (IOException e) {
                        // 忽略无法读取的文件
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
            });
            
            if (results.isEmpty()) {
                return "未找到匹配的内容";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(results.size()).append(" 个匹配:\n");
            for (String result : results) {
                sb.append(result).append("\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            return "错误：搜索失败 - " + e.getMessage();
        }
    }
    
    /**
     * 简单的glob模式匹配
     */
    private boolean matchesGlob(String fileName, String glob) {
        // 将glob模式转换为正则表达式
        String regex = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return fileName.matches(regex);
    }
    
    /**
     * 检查是否为二进制文件
     */
    private boolean isBinaryFile(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            for (byte b : bytes) {
                if (b == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
