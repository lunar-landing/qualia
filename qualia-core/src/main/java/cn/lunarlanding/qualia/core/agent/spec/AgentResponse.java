package cn.lunarlanding.qualia.core.agent.spec;

import com.alibaba.fastjson.annotation.JSONField;
import cn.lunarlanding.qualia.core.model.chat.ChatUsage;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行响应结果
 */
public class AgentResponse {

    /**
     * 最终答案
     */
    private String answer;

    /**
     * 深度思考内容（推理模型专用）
     */
    private String reasoningContent;

    /**
     * 所有执行步骤
     */
    private List<AgentStep> steps;

    /**
     * 总步骤数
     */
    private int totalSteps;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 响应类型：step - ReAct步骤阶段, answer - 最终答案阶段
     */
    private String responseType;

    /**
     * Token 用量统计
     */
    private ChatUsage usage;

    /**
     * Knowledge sources used to produce the final answer.
     */
    private List<KnowledgeSource> sources;

    public AgentResponse() {
        this.steps = new ArrayList<>();
        this.success = true;
    }

    public AgentResponse(List<AgentStep> steps, String answer, long durationMs) {
        this();
        this.steps = steps;
        this.answer = answer;
        this.totalSteps = steps.size();
        this.durationMs = durationMs;
    }

    /**
     * 获取所有思考步骤（不参与序列化）
     */
    @JSONField(serialize = false)
    public List<AgentStep> getThoughtSteps() {
        List<AgentStep> thoughtSteps = new ArrayList<>();
        for (AgentStep step : steps) {
            if (step.getStepType() == AgentStep.StepType.THOUGHT) {
                thoughtSteps.add(step);
            }
        }
        return thoughtSteps;
    }

    /**
     * 获取所有行动步骤（不参与序列化）
     */
    @JSONField(serialize = false)
    public List<AgentStep> getActionSteps() {
        List<AgentStep> actionSteps = new ArrayList<>();
        for (AgentStep step : steps) {
            if (step.getStepType() == AgentStep.StepType.ACTION) {
                actionSteps.add(step);
            }
        }
        return actionSteps;
    }

    /**
     * 添加步骤
     */
    public void addStep(AgentStep step) {
        this.steps.add(step);
        this.totalSteps = this.steps.size();
    }

    // Getters and Setters

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps;
        this.totalSteps = steps != null ? steps.size() : 0;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public ChatUsage getUsage() {
        return usage;
    }

    public void setUsage(ChatUsage usage) {
        this.usage = usage;
    }

    public List<KnowledgeSource> getSources() {
        return sources;
    }

    public void setSources(List<KnowledgeSource> sources) {
        this.sources = sources;
    }
}
