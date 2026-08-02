package com.lunarlanding.qualia.core.knowledge;

/**
 * 知识库配置 —— 封装知识库连接信息，用于创建检索工具。
 *
 * <p>与 {@link com.lunarlanding.qualia.core.mcp.client.McpClientParameters} 设计对齐：
 * 调用方只需构造此对象并传入 Agent，内部自动创建对应的检索工具。</p>
 */
public class KnowledgeBase {

    /** 知识库名称（用于工具命名和描述） */
    private final String name;

    /** 知识库描述（可选，用于工具描述补充） */
    private final String description;

    /** RagFlow 服务地址，如 http://localhost:9000 */
    private final String address;

    /** RagFlow 数据集 ID */
    private final String datasetId;

    /** RagFlow API Key */
    private final String apiKey;

    public KnowledgeBase(String name, String address, String apiKey, String datasetId) {
        this(name, null, address, apiKey, datasetId);
    }

    public KnowledgeBase(String name, String description, String address, String apiKey, String datasetId) {

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }

        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("知识库服务地址不能为空");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("知识库 API Key 不能为空");
        }

        if (datasetId == null || datasetId.isEmpty()) {
            throw new IllegalArgumentException("知识库数据集 ID 不能为空");
        }

        this.name = name;
        this.description = description;
        this.address = address;
        this.apiKey = apiKey;
        this.datasetId = datasetId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAddress() {
        return address;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getDatasetId() {
        return datasetId;
    }

    @Override
    public String toString() {
        return "KnowledgeBase{name='" + name + "', address='" + address + "', datasetId='" + datasetId + "'}";
    }
}
