# BingSearchTool 文档

基于必应搜索引擎的网络搜索工具。

## 简介

`BingSearchTool` 是 Qualia 框架内置的网络搜索工具，基于必应搜索引擎的 HTML 接口实现。完全免费，无需 API Key，国内可用，中英文兼顾。

## 特点

- ✅ 国内可用，访问速度快
- ✅ 中英文搜索结果都好
- ✅ 完全免费，无需 API Key
- ✅ 15秒超时保护

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | String | 是 | 搜索查询语句 |
| `max_results` | Integer | 否 | 返回结果数量，默认 10 |

## 响应格式

```
搜索结果：

1. 结果标题
   链接: https://example.com/article
   摘要: 结果摘要内容...

2. 结果标题
   链接: https://example.com/another
   摘要: 结果摘要内容...
```

## 示例

```java
// 必应搜索（国内可用）
BingSearchTool bingTool = new BingSearchTool();
Map<String, Object> args = Map.of("query", "Spring Boot best practices");
String result = bingTool.execute(args);
```

## 使用场景

- 中英文技术文档搜索
- 国际新闻资讯
- 学术论文搜索
- 技术问答社区（Stack Overflow、GitHub）

## 注意事项

- 使用必应国内版（cn.bing.com），国内访问无需代理
- 搜索结果质量介于百度和 Google 之间
- 默认返回10条结果，可以通过 `max_results` 调整

## 错误处理

- 搜索请求失败：返回错误信息和 HTTP 状态码
- 网络超时：返回错误信息"搜索请求超时"
- 频率限制：返回错误信息"请求过于频繁，请稍后再试"

## 相关工具

- [BaiduSearchTool](./baidu-search.md) - 基于百度搜索引擎的搜索工具
- [GoogleSearchTool](./google-search.md) - 基于 Google 搜索引擎的搜索工具
- [DuckDuckGoSearchTool](./duckduckgo-search.md) - 基于 DuckDuckGo 搜索引擎的搜索工具
- [TavilySearchTool](./tavily-search.md) - 基于 Tavily API 的搜索工具（需要 API Key）
- [WebFetchTool](./web-fetch.md) - 网页内容抓取工具