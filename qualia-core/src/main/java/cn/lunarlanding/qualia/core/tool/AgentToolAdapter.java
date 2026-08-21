package cn.lunarlanding.qualia.core.tool;

import cn.lunarlanding.qualia.core.agent.Agent;
import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;

import java.util.Map;
import java.util.UUID;

/**
 * Agent 工具适配器 - 将子智能体包装为 FunctionTool
 *
 * <p>允许父智能体将另一个 Agent 作为工具调用，实现多智能体协作。
 * 子智能体拥有完全独立的资源（记忆和工具），每次调用自动生成新的会话ID。</p>
 */
public class AgentToolAdapter extends FunctionTool {

    private final Agent agent;

    /**
     * @param agent 子智能体实例
     * @param name 工具名称（如 "research_agent"）
     * @param description 工具描述（如 "专门用于研究任务的智能体"）
     */
    public AgentToolAdapter(Agent agent, String name, String description) {
        super(name, description, new Parameter[]{
            new Parameter("input", "传递给子智能体的用户输入", "string", true)
        });
        this.agent = agent;
    }

    @Override
    public String execute(Map<String, Object> arguments) {

        String input = (String) arguments.get("input");

        if (input == null || input.isEmpty()) {
            return "错误：input 参数不能为空";
        }

        // 自动生成新的会话ID
        String sessionId = UUID.randomUUID().toString();

        try {
            // 调用子智能体
            AgentResponse response = agent.call(sessionId, input);

            // 仅返回最终答案
            if (response.isSuccess() && response.getAnswer() != null) {
                return response.getAnswer();
            } else {
                return "子智能体执行失败：" +
                       (response.getErrorMessage() != null ? response.getErrorMessage() : "未知错误");
            }
        } catch (Exception e) {
            return "子智能体执行异常：" + e.getMessage();
        }
    }
}
