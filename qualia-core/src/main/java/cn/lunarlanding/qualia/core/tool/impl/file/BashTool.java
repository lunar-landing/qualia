package cn.lunarlanding.qualia.core.tool.impl.file;

import cn.lunarlanding.qualia.core.tool.FunctionTool;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 系统命令执行工具
 * 支持执行git、npm等系统命令
 */
public class BashTool extends FunctionTool {

    /** 系统本地编码（中文 Windows 为 GBK），用作非 UTF-8 输出的回退解码 */
    private static final Charset NATIVE_CHARSET = detectNativeCharset();

    private static final String CLIXML_MARKER = "#< CLIXML";
    private static final Pattern CLIXML_ELEMENT = Pattern.compile("<S [^>]*>(.*?)</S>");
    private static final Pattern CLIXML_ESCAPE = Pattern.compile("_x([0-9A-Fa-f]{4})_");

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
                // Windows 下改用 PowerShell 而非 cmd：PS 内置 ls/cat/pwd/rm 等系统级别名，
                // 对模型的 Unix 命令习惯兼容度远高于 cmd，无需自维护命令转译层
                // 用 & { } 2>&1 收集输出（含并入的错误记录），再由 Out-String 以固定宽度强制渲染为纯文本：
                // 1) ErrorRecord 被渲染为文本而非流向 stderr，避免 PS 5.1 重定向宿主下的 CLIXML XML 污染；
                // 2) 表格类输出不依赖伪控制台宽度（重定向下宽度为 0 时 pwd/Measure 等会渲染为空）。
                // 注意 $? 必须在 scriptblock 内部先捕获——出块后任何赋值/管道都会把它重置为 true
                String script = "$ProgressPreference='SilentlyContinue';"
                        // 抑制进度流：非交互宿主下进度会以 CLIXML XML 混入 stderr 污染输出
                        + "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                        + "$OutputEncoding=[Text.Encoding]::UTF8;"
                        // native 命令输出统一按 UTF-8 编解码，避免中文 Windows 默认 GBK 导致乱码
                        + "$__qout = & { " + command + "; $global:__qok = $? } 2>&1; "
                        // 宽度 120 与 PS 控制台默认一致：过宽会导致表格末列被拉伸填充（产生大量空格垃圾）
                        + "$__qok = $global:__qok; $__qout | Out-String -Width 120"
                        // 退出码合成：native 命令失败在 PS 里默认仍以 0 退出，需透传 $LASTEXITCODE；
                        // 纯 cmdlet 失败（如 Get-Content 不存在的文件）不产生 $LASTEXITCODE，用 $? 兜底
                        + "; $__qec = $LASTEXITCODE; if ($null -eq $__qec) { if ($__qok) { $__qec = 0 } else { $__qec = 1 } }; exit $__qec";
                // 用 -EncodedCommand（UTF-16LE Base64）传递，规避 -Command 多层引号转义问题
                String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
                processBuilder.command("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
            } else {
                // macOS/Linux 走原生 sh，Unix 命令天然兼容，无需适配
                processBuilder.command("sh", "-c", command);
            }
            
            processBuilder.directory(workDir.toFile());
            processBuilder.redirectErrorStream(true);

            // 启动进程
            Process process = processBuilder.start();

            // 立即关闭子进程 stdin：父进程不与其交互，读到 EOF 的交互式命令（如 Windows 下不带参数的 date、
            // 等待确认的 del）会按默认行为正常结束，否则会一直阻塞等待输入直至超时
            process.getOutputStream().close();

            // 异步读取进程输出，避免阻塞主线程导致超时机制失效
            ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try (InputStream in = process.getInputStream()) {
                    in.transferTo(rawOutput);
                } catch (IOException ignored) {
                    // 进程被强制销毁时流读取可能抛异常，忽略即可
                }
            });

            // 主线程等待进程完成（带超时）
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                readFuture.get(5, TimeUnit.SECONDS); // 等待读取线程结束

                // 附上超时前已产生的输出，便于模型判断命令卡在哪里（如交互式输入提示）
                String partialOutput = stripClixml(decodeOutput(rawOutput.toByteArray())).trim();
                if (partialOutput.isEmpty()) {
                    return "错误：命令执行超时（" + timeout + "秒）";
                }
                return "错误：命令执行超时（" + timeout + "秒），已捕获输出:\n" + partialOutput;
            }

            // 进程正常结束，等待输出读取完成
            readFuture.get(10, TimeUnit.SECONDS);

            String output = stripClixml(decodeOutput(rawOutput.toByteArray()));

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
        } catch (ExecutionException | TimeoutException e) {
            return "错误：读取进程输出异常 - " + e.getMessage();
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

    /** PS 5.1 重定向宿主下错误流会序列化为 CLIXML（"#< CLIXML" 前缀 + <Objs> XML），
     *  解析其中 <S> 文本元素还原为可读行（覆盖脚本级 Out-String 包裹不了的路径，如解析错误）；无标记时原样返回。 */
    private static String stripClixml(String output) {
        int marker = output.indexOf(CLIXML_MARKER);
        if (marker < 0) {
            return output;
        }
        StringBuilder text = new StringBuilder(output.substring(0, marker));
        Matcher element = CLIXML_ELEMENT.matcher(output.substring(marker));
        while (element.find()) {
            String line = decodeClixmlText(element.group(1));
            if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                text.append('\n');
            }
            text.append(line);
        }
        return text.toString();
    }

    private static String decodeClixmlText(String s) {
        // _xHHHH_ 是 CLIXML 对控制字符/换行的十六进制转义（如 _x000D__x000A_ = \r\n）
        StringBuffer sb = new StringBuffer(s.length());
        Matcher escape = CLIXML_ESCAPE.matcher(s);
        while (escape.find()) {
            escape.appendReplacement(sb, Character.toString((char) Integer.parseInt(escape.group(1), 16)));
        }
        escape.appendTail(sb);
        return sb.toString()
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
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
