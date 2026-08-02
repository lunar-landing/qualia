package com.lunarlanding.qualia.core.skill.engine;

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
 * Node.js 脚本执行器
 */
public class NodeJsScriptEngine implements ScriptEngine {

    private static final Logger logger = LoggerFactory.getLogger(NodeJsScriptEngine.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String nodeCommand;
    private final int timeoutSeconds;

    public NodeJsScriptEngine() {
        this("node", DEFAULT_TIMEOUT_SECONDS);
    }

    public NodeJsScriptEngine(String nodeCommand) {
        this(nodeCommand, DEFAULT_TIMEOUT_SECONDS);
    }

    public NodeJsScriptEngine(String nodeCommand, int timeoutSeconds) {
        this.nodeCommand = nodeCommand;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ScriptResult execute(Path scriptPath, Map<String, Object> args) {
        try {
            // 构建命令
            List<String> command = new ArrayList<>();
            command.add(nodeCommand);
            command.add(scriptPath.toString());

            logger.debug("执行 Node 脚本: {}", command);

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
                logger.error("Node 脚本执行超时: {}", scriptPath);
                return ScriptResult.timeout();
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("Node 脚本执行失败: {} - exitCode: {}, error: {}",
                    scriptPath, exitCode, error);
                return ScriptResult.failure(error.trim(), exitCode);
            }

            logger.debug("Node 脚本执行成功: {}", scriptPath);
            return ScriptResult.success(output.trim());

        } catch (Exception e) {
            logger.error("执行 Node 脚本异常: {}", scriptPath, e);
            return ScriptResult.failure(e.getMessage(), -1);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(nodeCommand, "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (Exception e) {
            logger.debug("Node 解释器不可用: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public String getInterpreterName() {
        return "node";
    }
}
