package cn.lunarlanding.qualia.core.tool.impl.file;

import cn.lunarlanding.qualia.core.tool.FunctionTool;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 基于文本匹配的编辑工具
 * 支持唯一文本匹配替换，非行号定位
 */
public class ReplaceTool extends FunctionTool {

    private final Path rootPath;

    public ReplaceTool(Path rootPath) {
        super(
            "edit",
            "基于文本匹配替换文件内容，支持唯一匹配和全局替换",
            new Parameter[]{
                new Parameter("path", "文件路径（相对于工作区）", "string", true),
                new Parameter("old_text", "要替换的原文文本", "string", true),
                new Parameter("new_text", "替换后的文本", "string", true),
                new Parameter("replace_all", "是否替换所有匹配项，默认false", "boolean", false)
            }
        );
        this.rootPath = rootPath;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String filePath = (String) arguments.get("path");
        if (filePath == null || filePath.isEmpty()) {
            return "错误：path 参数不能为空";
        }

        String oldText = (String) arguments.get("old_text");
        if (oldText == null || oldText.isEmpty()) {
            return "错误：old_text 参数不能为空";
        }

        String newText = (String) arguments.get("new_text");
        if (newText == null) {
            return "错误：new_text 参数不能为空";
        }

        Boolean replaceAll = (Boolean) arguments.get("replace_all");
        boolean isReplaceAll = Boolean.TRUE.equals(replaceAll);

        Path path = rootPath.resolve(filePath);

        if (!Files.exists(path)) {
            return "错误：文件不存在 - " + filePath;
        }

        if (!Files.isRegularFile(path)) {
            return "错误：路径是目录 - " + filePath;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String content = String.join("\n", lines);
            
            // 检查old_text是否存在
            if (!content.contains(oldText)) {
                return "错误：未找到要替换的文本";
            }
            
            // 检查唯一性（除非replace_all=true）
            if (!isReplaceAll) {
                int count = countOccurrences(content, oldText);
                if (count > 1) {
                    return "错误：找到 " + count + " 处匹配，请使用 replace_all=true 或提供更精确的文本";
                }
            }
            
            // 执行替换
            String newContent;
            if (isReplaceAll) {
                newContent = content.replace(oldText, newText);
            } else {
                newContent = content.replaceFirst(java.util.regex.Pattern.quote(oldText), java.util.regex.Matcher.quoteReplacement(newText));
            }
            
            // 写回文件
            Files.write(path, newContent.getBytes(StandardCharsets.UTF_8));
            
            int count = countOccurrences(content, oldText);
            return "替换成功，共替换 " + count + " 处";
            
        } catch (IOException e) {
            return "错误：读取或写入文件失败 - " + e.getMessage();
        }
    }
    
    /**
     * 计算子字符串出现的次数
     */
    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
