package com.lunarlanding.qualia.core.tool.impl.file;

import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 系统命令执行工具
 * 支持执行git、npm等系统命令
 */
public class BashTool extends FunctionTool {

    /** 系统本地编码（中文 Windows 为 GBK），用作非 UTF-8 输出的回退解码 */
    private static final Charset NATIVE_CHARSET = detectNativeCharset();

    private final Path rootPath;

    public BashTool(Path rootPath) {
        super(
            "bash",
            "执行系统命令，包括git、npm等",
            new Parameter[]{
                new Parameter("command", "要执行的命令", "string", true),
                new Parameter("working_directory", "工作目录（相对于工作区，默认为工作区根目录）", "string", false),
                new Parameter("timeout", "超时时间（秒），默认30", "integer", false)
            }
        );
        this.rootPath = rootPath;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        if (command == null || command.isEmpty()) {
            return "错误：command 参数不能为空";
        }

        String workingDir = (String) arguments.get("working_directory");
        Path workDir;
        if (workingDir == null || workingDir.isEmpty()) {
            workDir = rootPath;
        } else {
            workDir = rootPath.resolve(workingDir);
        }

        // 安全检查：工作目录必须在工作区内
        if (!workDir.startsWith(rootPath)) {
            return "错误：工作目录超出工作区范围";
        }

        Integer timeout = arguments.containsKey("timeout") ? ((Number) arguments.get("timeout")).intValue() : 30;

        try {
            // 创建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // 根据操作系统设置命令
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // 先将会话代码页切到 UTF-8，避免 cmd 默认 GBK 输出导致中文乱码
                processBuilder.command("cmd", "/c", "chcp 65001 >nul & " + command);
            } else {
                processBuilder.command("sh", "-c", command);
            }
            
            processBuilder.directory(workDir.toFile());
            processBuilder.redirectErrorStream(true);

            // 启动进程
            Process process = processBuilder.start();
            
            // 读取原始字节，结束后按行智能解码（兼容 UTF-8 与 GBK 混合输出）
            ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                in.transferTo(rawOutput);
            }

            // 等待进程完成
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：命令执行超时（" + timeout + "秒）";
            }

            String output = decodeOutput(rawOutput.toByteArray());

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return "命令执行成功:\n" + output;
            } else {
                return "命令执行失败，退出码: " + exitCode + "\n" + output;
            }

        } catch (IOException e) {
            return "错误：命令执行失败 - " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "错误：命令执行被中断";
        }
    }

    /**
     * 按行智能解码进程输出：每行先严格按 UTF-8 试解，失败则回退系统本地编码。
     * Windows 下 cmd 内置命令跟随代码页输出 UTF-8，而部分程序（如 JDK 17 及以下的 Java 子进程）
     * 无视代码页仍按 GBK 输出，两种编码可能混在同一段输出里，逐行判断才能各自解对。
     */
    private static String decodeOutput(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length);
        int lineStart = 0;
        for (int i = 0; i <= bytes.length; i++) {
            if (i == bytes.length || bytes[i] == '\n') {
                int lineEnd = i;
                // 去掉行尾 \r，统一换行风格
                if (lineEnd > lineStart && bytes[lineEnd - 1] == '\r') {
                    lineEnd--;
                }
                if (lineEnd > lineStart) {
                    text.append(decodeLine(bytes, lineStart, lineEnd - lineStart));
                }
                if (i < bytes.length) {
                    text.append('\n');
                }
                lineStart = i + 1;
            }
        }
        return text.toString();
    }

    private static String decodeLine(byte[] bytes, int offset, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, offset, length, NATIVE_CHARSET);
        }
    }

    private static Charset detectNativeCharset() {
        // JDK 17+ 提供 native.encoding；更早版本退而取 sun.jnu.encoding；都拿不到则用 JVM 默认字符集
        String encoding = System.getProperty("native.encoding", System.getProperty("sun.jnu.encoding"));
        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (Exception ignored) {
                // 无效编码名，回退默认字符集
            }
        }
        return Charset.defaultCharset();
    }
}
