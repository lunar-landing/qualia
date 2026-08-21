package cn.lunarlanding.qualia.core.tool.impl.file;

import cn.lunarlanding.qualia.core.tool.FunctionTool;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 读取文件内容工具
 */
public class ReadTool extends FunctionTool {

    private final Path rootPath;

    public ReadTool(Path rootPath) {
        super(
            "read",
            "读取文件内容，支持指定行范围",
            new Parameter[]{
                new Parameter("path", "文件路径（相对于工作区）", "string", true),
                new Parameter("begin", "起始行号（可选，从1开始）", "integer", false),
                new Parameter("end", "结束行号（可选，包含该行）", "integer", false)
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

        Path path = rootPath.resolve(filePath);

        if (!Files.exists(path)) {
            return "错误：文件不存在 - " + filePath;
        }

        if (!Files.isRegularFile(path)) {
            return "错误：路径是目录 - " + filePath;
        }

        try {

            List<String> allLines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
            Integer startLine = arguments.containsKey("begin") ? ((Number) arguments.get("begin")).intValue() : null;
            Integer endLine = arguments.containsKey("end") ? ((Number) arguments.get("end")).intValue() : null;

            List<String> selectedLines;
            if (startLine != null || endLine != null) {
                int start = (startLine != null) ? Math.max(1, startLine) : 1;
                int end = (endLine != null) ? Math.min(allLines.size(), endLine) : allLines.size();

                if (start > allLines.size()) {
                    return "错误：起始行号超出文件范围（文件共 " + allLines.size() + " 行）";
                }

                selectedLines = allLines.subList(start - 1, end);
            } else {
                selectedLines = allLines;
            }

            StringBuilder sb = new StringBuilder();
            int lineNum = (startLine != null) ? Math.max(1, startLine) : 1;
            for (String line : selectedLines) {
                sb.append(String.format("%6d→%s%n", lineNum, line));
                lineNum++;
            }

            return sb.toString();
        } catch (IOException e) {
            return "错误：读取文件失败 - " + e.getMessage();
        }
    }
}
