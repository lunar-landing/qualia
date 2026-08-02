# TavilySearch

基于 Tavily API 的网络搜索工具，用于获取实时网络信息。

## 简介

`TavilySearchTool` 是 Qualia 框架内置的搜索工具，封装了 Tavily 搜索 API，提供简洁的接口进行网络搜索。适用于需要获取实时网络信息、新闻或最新资料的场景。

默认返回 10 条结果，搜索主题为 general，HTTP 请求超时 30 秒。

## 集成代码

### 基本使用

```java
TavilySearchTool tool = new TavilySearchTool("tvly-your-api-key");

Map<String, Object> args = Map.of("query", "人工智能最新进展");
String result = tool.execute(args);
```

### Agent 集成

```java
ReActAgent agent = new ReActAgent(chatModel, memory);

TavilySearchTool searchTool = new TavilySearchTool("tvly-your-api-key");
agent.addTool(searchTool);
```

## 参数说明

### 构造参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `apiKey` | String | 是 | Tavily API Key，格式：`tvly-<your_key>` |

### 执行参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | String | 是 | 搜索查询语句 |

### 响应格式

```json
{
  "source": "tavily",
  "query": "搜索关键词",
  "count": 5,
  "items": [
    {
      "title": "结果标题",
      "url": "https://example.com",
      "content": "摘要内容...",
      "score": 0.95
    }
  ],
  "response_time": 1.67
}
```
