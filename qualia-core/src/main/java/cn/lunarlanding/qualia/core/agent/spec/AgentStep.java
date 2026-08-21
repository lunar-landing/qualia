package cn.lunarlanding.qualia.core.agent.spec;

import lombok.Data;

import java.util.Map;

/**
 * Agent 执行步骤
 */
@Data
public class AgentStep {

    /**
     * 步骤内容
     */
    private String content;

    /**
     * 步骤类型
     */
    private StepType stepType;

    /**
     * 工具名称（仅 ACTION 类型有值）
     */
    private String toolName;

    /**
     * 工具参数（仅 ACTION 类型有值）
     */
    private Map<String, Object> toolArgs;

    /**
     * 时间戳
     */
    private long timestamp;


    /**
     * Agent 步骤类型枚举
     */
    public enum StepType {

        /**
         * 思考步骤
         */
        THOUGHT,

        /**
         * 行动步骤（调用工具）
         */
        ACTION,

        /**
         * 观察步骤（工具返回结果）
         */
        OBSERVATION,

        /**
         * 回答
         */
        ANSWER,

        /**
         * 错误
         */
        ERROR,

        /**
         * 上下文压缩
         */
        COMPRESS

    }
}
