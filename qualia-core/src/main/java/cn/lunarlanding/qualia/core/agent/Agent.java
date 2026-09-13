package cn.lunarlanding.qualia.core.agent;

import cn.lunarlanding.qualia.core.agent.spec.AgentResponse;
import cn.lunarlanding.qualia.core.model.chat.Attachment;
import reactor.core.publisher.Flux; 

import java.util.List;

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
     * 流式运行智能体（携带附件：图片直传视觉模型，文档解析正文以文字注入）
     *
     * <p>图片 base64 与附件正文仅在当轮请求中发给模型，记忆中只保留占位符文本。</p>
     *
     * @param sessionId   会话ID，用于记忆存储
     * @param input       用户输入（可为空字符串，表示纯附件提问）
     * @param attachments 随消息发送的附件列表
     * @return Flux<AgentResponse> 流式响应
     */
    default Flux<AgentResponse> callStream(String sessionId, String input, List<Attachment> attachments) {
        throw new UnsupportedOperationException("当前智能体不支持附件输入");
    }

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
