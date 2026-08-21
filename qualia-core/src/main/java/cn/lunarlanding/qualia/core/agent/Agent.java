package cn.lunarlanding.qualia.core.agent;

import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;
import reactor.core.publisher.Flux; 

/**
 * 智能体接口，定义智能体的基本能力
 */
public interface Agent {

    /**
     * 运行智能体并处理输入
     *
     * @param input 用户输入
     * @return 智能体的响应结果，包含完整执行步骤和最终答案
     */
    AgentResponse call(String sessionId, String input);

    /**
     * 流式运行智能体（带会话ID）
     *
     * @param sessionId 会话ID，用于记忆存储
     * @param input     用户输入
     * @return Flux<AgentResponse> 流式响应
     */
    Flux<AgentResponse> callStream(String sessionId, String input);

    /**
     * 获取智能体描述
     *
     * @return 智能体描述
     */
    String description();

    /**
     * 获取智能体名称
     *
     * @return 智能体名称
     */
    String name();

}
