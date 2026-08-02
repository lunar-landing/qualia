package com.lunarlanding.qualia.core.tool.impl.file;

import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 删除文件工具
 */
public class DeleteTool extends FunctionTool {

    private final Path rootPath;

    public DeleteTool(Path rootPath) {
        super(
            "delete",
            "删除工作区内的文件（仅限普通文件，不能删除目录）",
            new Parameter[]{
                new Parameter("path", "文件路径（相对于工作区）", "string", true)
            }
        );
        this.rootPath = rootPath.toAbsolutePath().normalize();
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String filePath = (String) arguments.get("path");
        if (filePath == null || filePath.isEmpty()) {
            return "错误：path 参数不能为空";
        }

        Path path = rootPath.resolve(filePath).normalize();

        // 安全检查：删除属高危操作，路径必须在工作区内
        if (!path.startsWith(rootPath)) {
            return "错误：路径超出工作区范围 - " + filePath;
        }

        if (!Files.exists(path)) {
            return "错误：文件不存在 - " + filePath;
        }

        if (!Files.isRegularFile(path)) {
            return "错误：路径不是普通文件，不能删除目录 - " + filePath;
        }

        try {
            Files.delete(path);
            return "文件已删除: " + filePath;
        } catch (IOException e) {
            return "错误：删除文件失败 - " + e.getMessage();
        }
    }
}

