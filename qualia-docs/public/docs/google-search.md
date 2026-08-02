# GoogleSearchTool 文档

基于 Google 搜索引擎的网络搜索工具。

## 简介

`GoogleSearchTool` 是 Qualia 框架内置的网络搜索工具，基于 Google 搜索引擎的 HTML 接口实现。完全免费，无需 API Key，搜索结果最全面，但需要代理才能在国内使用。

## 特点

- ⚠️ 国内需要代理才能访问
- ✅ 搜索结果最全面
- ✅ 英文搜索结果最好
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
// Google 搜索（需要代理）
GoogleSearchTool googleTool = new GoogleSearchTool();
Map<String, Object> args = Map.of("query", "latest AI research papers");
String result = googleTool.execute(args);
```

## 使用场景

- 学术论文搜索（Google Scholar）
- 英文技术文档
- 国际新闻资讯
- 开源项目搜索（GitHub、GitLab）
- 最新技术动态

## 注意事项

- **Google 在国内无法直接访问，需要配置代理**
- Google 有严格的反爬虫机制
- 搜索结果可能包含 Google 广告内容
- 默认返回10条结果，可以通过 `max_results` 调整

## 代理配置

如果需要使用 GoogleSearchTool，可以通过以下方式配置代理：

### 方式1：JVM 参数

```bash
java -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 \
     -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 \
     -jar your-app.jar
```

### 方式2：代码配置

```java
System.setProperty("http.proxyHost", "127.0.0.1");
System.setProperty("http.proxyPort", "7890");
System.setProperty("https.proxyHost", "127.0.0.1");
System.setProperty("https.proxyPort", "7890");

GoogleSearchTool tool = new GoogleSearchTool();
```

## 错误处理

- 搜索请求失败：返回错误信息和 HTTP 状态码
- 网络超时：返回错误信息"搜索请求超时"
- 代理问题：返回错误信息"需要代理才能访问 Google"

## 相关工具

- [BaiduSearchTool](./baidu-search.md) - 基于百度搜索引擎的搜索工具
- [BingSearchTool](./bing-search.md) - 基于必应搜索引擎的搜索工具
- [DuckDuckGoSearchTool](./duckduckgo-search.md) - 基于 DuckDuckGo 搜索引擎的搜索工具
- [TavilySearchTool](./tavily-search.md) - 基于 Tavily API 的搜索工具（需要 API Key）
- [WebFetchTool](./web-fetch.md) - 网页内容抓取工具