package cn.lunarlanding.qualia.core.skill.engine;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Python 脚本执行器
 */
public class PythonScriptEngine implements ScriptEngine {

    private static final Logger logger = LoggerFactory.getLogger(PythonScriptEngine.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String pythonCommand;
    private final int timeoutSeconds;

    public PythonScriptEngine() {
        this("python", DEFAULT_TIMEOUT_SECONDS);
    }

    public PythonScriptEngine(String pythonCommand) {
        this(pythonCommand, DEFAULT_TIMEOUT_SECONDS);
    }

    public PythonScriptEngine(String pythonCommand, int timeoutSeconds) {
        this.pythonCommand = pythonCommand;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ScriptResult execute(Path scriptPath, Map<String, Object> args) {
        try {
            // 构建命令
            List<String> command = new ArrayList<>();
            command.add(pythonCommand);
            command.add(scriptPath.toString());

            logger.debug("执行 Python 脚本: {}", command);

            // 将参数序列化为 JSON
            String jsonArgs = args != null ? JSON.toJSONString(args) : "{}";

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // 写入参数到 stdin
            try (var os = process.getOutputStream()) {
                os.write(jsonArgs.getBytes(StandardCharsets.UTF_8));
            }

            // 读取输出
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            // 等待执行完成
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("Python 脚本执行超时: {}", scriptPath);
                return ScriptResult.timeout();
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("Python 脚本执行失败: {} - exitCode: {}, error: {}",
                    scriptPath, exitCode, error);
                return ScriptResult.failure(error.trim(), exitCode);
            }

            logger.debug("Python 脚本执行成功: {}", scriptPath);
            return ScriptResult.success(output.trim());

        } catch (Exception e) {
            logger.error("执行 Python 脚本异常: {}", scriptPath, e);
            return ScriptResult.failure(e.getMessage(), -1);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonCommand, "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (Exception e) {
            logger.debug("Python 解释器不可用: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public String getInterpreterName() {
        return "python";
    }
}
