package com.lunarlanding.qualia.core.model.embedding;

import java.util.List;

/**
 * 嵌入模型接口，用于定义与嵌入模型交互的基本方法
 */
public interface EmbeddingModel {

    /**
     * 生成文本嵌入向量
     *
     * @param input 输入的文本
     * @return 向量列表
     */
    List<Float> embed(String input);

    /**
     * 生成文本嵌入向量（批量）
     *
     * @param inputs 输入的文本列表
     * @return 向量列表的列表
     */
    List<List<Float>> embedBatch(String[] inputs);

    /**
     * 生成文本嵌入向量（指定模型和维度）
     *
     * @param input 输入的文本
     * @param model 模型名称
     * @param dimensions 向量维度
     * @return 向量列表
     */
    List<Float> embed(String input, String model, Integer dimensions);

    /**
     * 设置API密钥
     */
    void apiKey(String apiKey);

    /**
     * 设置模型名称
     */
    void modelName(String modelName);

    /**
     * 设置基础URL
     */
    void baseUrl(String baseUrl);

    /**
     * 获取模型名称
     */
    String modelName();
}
