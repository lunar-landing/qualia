package com.lunarlanding.qualia.core.tool.impl.file;

import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 写入文件内容工具
 */
public class WriteTool extends FunctionTool {

    private final Path rootPath;

    public WriteTool(Path rootPath) {
        super(
            "write",
            "写入文件内容，支持覆盖、追加和插入三种模式",
            new Parameter[]{
                new Parameter("path", "文件路径（相对于工作区）", "string", true),
                new Parameter("content", "要写入的内容", "string", true),
                new Parameter("mode", "写入模式：overwrite(覆盖)、append(追加)、insert(插入)，默认overwrite", "string", false),
                new Parameter("line", "插入行号（仅insert模式有效，从1开始）", "integer", false)
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

        String content = (String) arguments.get("content");
        if (content == null) {
            return "错误：content 参数不能为空";
        }

        // 解析写入模式
        String mode = (String) arguments.get("mode");
        if (mode == null || mode.isEmpty()) {
            mode = "overwrite";
        }
        if (!"overwrite".equals(mode) && !"append".equals(mode) && !"insert".equals(mode)) {
            return "错误：不支持的写入模式: " + mode + "，可选值：overwrite、append、insert";
        }

        // 解析行号参数
        Integer line = arguments.containsKey("line") ? ((Number) arguments.get("line")).intValue() : null;

        // 非insert模式时传了line参数，报错
        if (!"insert".equals(mode) && line != null) {
            return "错误：line 参数仅在 mode=insert 时有效";
        }

        Path path = rootPath.resolve(filePath);

        try {
            // 创建父目录（如果不存在）
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            switch (mode) {
                case "overwrite":
                    return doOverwrite(path, content, filePath);
                case "append":
                    return doAppend(path, content, filePath);
                case "insert":
                    return doInsert(path, content, filePath, line);
                default:
                    return "错误：不支持的写入模式: " + mode;
            }
        } catch (IOException e) {
            return "错误：写入文件失败 - " + e.getMessage();
        }
    }

    private String doOverwrite(Path path, String content, String filePath) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return "文件写入成功: " + filePath;
    }

    private String doAppend(Path path, String content, String filePath) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return "内容已追加到文件: " + filePath;
    }

    private String doInsert(Path path, String content, String filePath, Integer line) throws IOException {
        List<String> lines;

        if (Files.exists(path)) {
            lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        } else {
            lines = new ArrayList<>();
        }

        // 确定插入位置
        int insertPos;
        if (line == null || line <= 0) {
            insertPos = 0;
        } else if (line > lines.size()) {
            insertPos = lines.size();
        } else {
            insertPos = line - 1;
        }

        // 将内容按行分割并插入
        String[] newLines = content.split("\n", -1);
        for (int i = 0; i < newLines.length; i++) {
            lines.add(insertPos + i, newLines[i]);
        }

        // 写回文件
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        int actualLine = insertPos + 1;
        return "内容已插入到第 " + actualLine + " 行: " + filePath;
    }
}
