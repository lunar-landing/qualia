# HttpTool

通用 HTTP 请求工具，用于调用外部 API 服务。

## 简介

`HttpTool` 是 Qualia 框架内置的 HTTP 工具，支持 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS 方法。适用于调用第三方 API、Webhook 回调等场景。

默认超时 30 秒，POST/PUT 请求自动设置 `Content-Type: application/json`。

## 集成代码

### 基本使用

```java
HttpTool tool = new HttpTool();

// GET 请求
Map<String, Object> args = Map.of("url", "https://api.example.com/data");
String result = tool.execute(args);

// POST 请求
Map<String, Object> postArgs = Map.of("url", "https://api.example.com/data", "method", "POST", "body", "{'key': 'value'}");
String postResult = tool.execute(postArgs);
```

### Agent 集成

```java
ReActAgent agent = new ReActAgent(chatModel, memory);

HttpTool httpTool = new HttpTool();
agent.addTool(httpTool);
```

## 参数说明

### 构造参数

无，使用默认配置。

### 执行参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | String | 是 | 请求地址 URL |
| `method` | String | 否 | HTTP 方法：GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS，默认 GET |
| `headers` | Map | 否 | 请求头，JSON 对象格式 |
| `body` | String | 否 | 请求体，用于 POST/PUT 请求 |

### 响应格式

```json
{
  "status": 200,
  "method": "GET",
  "url": "https://api.example.com/data",
  "data": { }
}
```
