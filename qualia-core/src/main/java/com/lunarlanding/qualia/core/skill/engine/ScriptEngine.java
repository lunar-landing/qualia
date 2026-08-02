package com.lunarlanding.qualia.core.skill.engine;

import java.nio.file.Path;
import java.util.Map;

/**
 * 脚本执行器接口
 */
public interface ScriptEngine {

    /**
     * 执行脚本
     *
     * @param scriptPath 脚本文件路径
     * @param args       传入脚本的参数
     * @return 执行结果
     */
    ScriptResult execute(Path scriptPath, Map<String, Object> args);

    /**
     * 获取解释器名称
     *
     * @return 解释器名称，如 "python"
     */
    String getInterpreterName();

    /**
     * 检查解释器是否可用
     *
     * @return true 如果解释器可用
     */
    boolean isAvailable();

    /**
     * 脚本执行结果
     */
    record ScriptResult(boolean success, String output, String error, int exitCode) {

        /**
         * 创建成功结果
         *
         * @param output 脚本输出内容
         * @return 成功的执行结果
         */
        public static ScriptResult success(String output) {
            return new ScriptResult(true, output, null, 0);
        }

        /**
         * 创建失败结果
         *
         * @param error   错误信息
         * @param exitCode 退出码
         * @return 失败的执行结果
         */
        public static ScriptResult failure(String error, int exitCode) {
            return new ScriptResult(false, null, error, exitCode);
        }

        /**
         * 创建超时结果
         *
         * @return 超时的执行结果
         */
        public static ScriptResult timeout() {
            return new ScriptResult(false, null, "脚本执行超时", -1);
        }
    }
}
