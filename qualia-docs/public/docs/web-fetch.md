# WebFetchTool

网页内容抓取工具，用于获取指定 URL 的网页内容并转换为纯文本。

## 简介

`WebFetchTool` 是 Qualia 框架内置的网页抓取工具，用于获取在线文档、技术博客、API 文档等网页内容。抓取的 HTML 内容会被自动转换为纯文本，移除脚本和样式标签，解码 HTML 实体。

默认超时 30 秒，支持自动重定向，最大内容长度 50000 字符。

## 集成代码

### 基本使用

```java
WebFetchTool tool = new WebFetchTool();

// 抓取网页内容
Map<String, Object> args = Map.of("url", "https://example.com/article");
String result = tool.execute(args);

// 限制返回内容长度
Map<String, Object> args = Map.of("url", "https://example.com/article", "max_length", 10000);
String result = tool.execute(args);
```

### Agent 集成

```java
ReActAgent agent = new ReActAgent(chatModel, memory);

WebFetchTool webFetchTool = new WebFetchTool();
agent.addTool(webFetchTool);
```

## 参数说明

### 构造参数

无，使用默认配置。

### 执行参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | String | 是 | 要抓取的网页 URL |
| `max_length` | Integer | 否 | 返回内容的最大字符数，默认 50000 |

### 响应格式

```json
{
  "url": "https://example.com/article",
  "status": 200,
  "content_type": "text/html; charset=utf-8",
  "content": "网页的纯文本内容...",
  "original_length": 125000,
  "extracted_length": 45000
}
```

### 错误响应

```json
{
  "error": true,
  "message": "HTTP错误: 404"
}
```

## HTML 处理

工具会自动处理以下 HTML 内容：

| 处理项 | 说明 |
|--------|------|
| Script/Style 标签 | 完全移除标签及其内容 |
| HTML 注释 | 移除 `<!-- -->` 注释 |
| HTML 标签 | 移除所有标签，保留文本内容 |
| HTML 实体 | 解码 `&amp;`、`&lt;`、`&gt;`、`&nbsp;` 等 |
| 空白字符 | 规范化多余的空格和空行 |

## 使用场景

- **阅读在线文档**：抓取技术文档、API 参考等内容
- **获取技术博客**：获取博客文章内容用于学习参考
- **收集参考资料**：批量获取网页内容用于分析

## 注意事项

1. 部分网站可能限制爬虫访问，返回 403 错误
2. 动态渲染的页面（JavaScript 生成内容）无法抓取
3. 大型网页内容会被截断到 `max_length` 限制
4. 请求超时为 30 秒，超时会返回错误信息
