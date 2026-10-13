package cn.lunarlanding.qualia.core.model.chat;

import cn.lunarlanding.qualia.core.model.chat.conf.ResponseFormatType;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天模型接口，用于定义与大语言模型交互的基本方法
 */
public interface ChatModel {

    /**
     * 发送消息并获取响应（默认 TEXT 格式）
     *
     * @param message 输入的消息
     * @return 模型返回的响应数据
     */
    ChatResponse chat(String message);

    /**
     * 流式聊天调用（默认 TEXT 格式）
     * @param message 输入的消息
     * @return 流式响应的 Flux
     */
    Flux<ChatResponse> chatStream(String message);

    /**
     * 发送带有历史记录的消息（默认 TEXT 格式）
     *
     * @param messages 包含历史对话的消息列表
     * @return 模型返回的响应数据
     */
    ChatResponse chat(List<ChatMessage> messages);

    /**
     * 发送带有历史记录的消息，指定响应格式
     *
     * @param messages 包含历史对话的消息列表
     * @param formatType 响应格式类型（TEXT 或 JSON_OBJECT）
     * @return 模型返回的响应数据
     */
    ChatResponse chat(List<ChatMessage> messages, ResponseFormatType formatType);

    /**
     * 流式聊天调用（带历史记录，默认 TEXT 格式）
     * @param messages 包含历史对话的消息列表
     * @return 流式响应的 Flux
     */
    Flux<ChatResponse> chatStream(List<ChatMessage> messages);

    /**
     * 流式聊天调用（带历史记录），指定响应格式
     * @param messages 包含历史对话的消息列表
     * @param formatType 响应格式类型（TEXT 或 JSON_OBJECT）
     * @return 流式响应的 Flux
     */
    Flux<ChatResponse> chatStream(List<ChatMessage> messages, ResponseFormatType formatType);

    /**
     * 流式聊天调用（带工具签名，原生 function calling 协议）
     *
     * <p>模型自主决定调用工具（tool_calls）或直接回答（content），
     * 流式分片中的 tool_calls 按 index 聚合由调用方完成。</p>
     *
     * @param messages 包含历史对话的消息列表
     * @param tools 可用工具签名列表，null/空表示不传工具
     * @return 流式响应的 Flux
     */
    default Flux<ChatResponse> chatStream(List<ChatMessage> messages, List<ToolSpec> tools) {
        return chatStream(messages);
    }

    /**
     * 设置基础URL
     */
    void baseUrl(String baseUrl);

    /**
     * 设置API密钥
     */
    void apiKey(String apiKey);

    /**
     * 获取模型名称
     */
    String modelName();

}
