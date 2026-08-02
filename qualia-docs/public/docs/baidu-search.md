# BaiduSearchTool 文档

基于百度搜索引擎的网络搜索工具。

## 简介

`BaiduSearchTool` 是 Qualia 框架内置的网络搜索工具，基于百度搜索引擎的 HTML 接口实现。完全免费，无需 API Key，适合国内用户使用。

## 特点

- ✅ 国内可用，访问速度快
- ✅ 中文搜索结果准确
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
// 百度搜索（国内可用）
BaiduSearchTool baiduTool = new BaiduSearchTool();
Map<String, Object> args = Map.of("query", "人工智能最新进展");
String result = baiduTool.execute(args);
```

## 使用场景

- 中文技术文档搜索
- 国内新闻资讯
- 中文问答社区（知乎、CSDN）
- 本地化信息（产品、服务、公司）

## 注意事项

- 百度有反爬虫机制，过于频繁的请求可能被限制
- 搜索结果可能包含百度推广内容
- 默认返回10条结果，可以通过 `max_results` 调整

## 错误处理

- 搜索请求失败：返回错误信息和 HTTP 状态码
- 网络超时：返回错误信息"搜索请求超时"
- 频率限制：返回错误信息"请求过于频繁，请稍后再试"

## 相关工具

- [BingSearchTool](./bing-search.md) - 基于必应搜索引擎的搜索工具
- [GoogleSearchTool](./google-search.md) - 基于 Google 搜索引擎的搜索工具
- [DuckDuckGoSearchTool](./duckduckgo-search.md) - 基于 DuckDuckGo 搜索引擎的搜索工具
- [TavilySearchTool](./tavily-search.md) - 基于 Tavily API 的搜索工具（需要 API Key）
- [WebFetchTool](./web-fetch.md) - 网页内容抓取工具