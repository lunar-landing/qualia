# RagflowTool

基于 RAGFlow API 的知识库检索工具，用于从文档知识库中检索相关内容。

## 简介

`RagflowTool` 是 Qualia 框架内置的知识库检索工具，封装了 RAGFlow API，支持查询优化和结果重排序。适用于查询专业知识、文档内容或历史资料的场景。

默认返回 8 条检索结果。

## 集成代码

### 基本使用

```java
RagflowTool tool = new RagflowTool("http://localhost:9000", "your-api-key", List.of("dataset-id-1", "dataset-id-2"), null);

Map<String, Object> args = Map.of("query", "保险理赔流程");
String result = tool.execute(args);
```

### Agent 集成

```java
ReActAgent agent = new ReActAgent(chatModel, memory);

RagflowTool ragflowTool = new RagflowTool("http://localhost:9000", "your-api-key", List.of("dataset-id-1"), model);
agent.addTool(ragflowTool);
```

## 参数说明

### 构造参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `address` | String | 是 | RagFlow 服务地址 |
| `apiKey` | String | 是 | RagFlow API Key |
| `datasetIds` | List\<String\> | 是 | 数据集 ID 列表 |
| `model` | RerankModel | 否 | 结果重排序模型，默认 null |

### 执行参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | String | 是 | 检索查询语句 |

### 响应格式

```json
{
  "source": "ragflow",
  "count": 5,
  "items": [
    {
      "title": "文档名称",
      "score": 0.95,
      "content": "检索到的文档片段内容..."
    }
  ]
}
```
