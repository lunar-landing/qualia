package com.lunarlanding.qualia.core.model.chat;

import com.lunarlanding.qualia.core.model.chat.conf.ResponseFormatType;
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
