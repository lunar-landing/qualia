# DuckDuckGoSearchTool 文档

基于 DuckDuckGo 搜索引擎的网络搜索工具。

## 简介

`DuckDuckGoSearchTool` 是 Qualia 框架内置的网络搜索工具，基于 DuckDuckGo 搜索引擎的 HTML 接口实现。完全免费，无需 API Key，隐私保护最好，但需要代理才能在国内使用。

## 特点

- ⚠️ 国内需要代理才能访问
- ✅ 隐私保护最好，不追踪用户
- ✅ 不存储用户搜索历史
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
// DuckDuckGo 搜索（需要代理，隐私保护）
DuckDuckGoSearchTool duckduckgoTool = new DuckDuckGoSearchTool();
Map<String, Object> args = Map.of("query", "privacy-focused tools");
String result = duckduckgoTool.execute(args);
```

## 使用场景

- 隐私敏感搜索
- 匿名搜索（不希望被追踪）
- 隐私工具研究
- 安全技术研究
- 无广告搜索

## 隐私保护特性

| 特性 | 说明 |
|------|------|
| 不追踪用户 | 不记录 IP 地址、浏览器指纹等信息 |
| 不存储历史 | 不保存用户搜索历史记录 |
| 无个性化广告 | 不根据搜索历史推送个性化广告 |
| 无过滤气泡 | 不根据用户偏好过滤搜索结果 |
| HTTPS 加密 | 默认使用 HTTPS 加密连接 |

## 注意事项

- **DuckDuckGo 在国内无法直接访问，需要配置代理**
- 默认返回10条结果，可以通过 `max_results` 调整
- 搜索结果不包含个性化广告

## 代理配置

如果需要使用 DuckDuckGoSearchTool，可以通过以下方式配置代理：

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

DuckDuckGoSearchTool tool = new DuckDuckGoSearchTool();
```

## 错误处理

- 搜索请求失败：返回错误信息和 HTTP 状态码
- 网络超时：返回错误信息"搜索请求超时"
- 代理问题：返回错误信息"需要代理才能访问 DuckDuckGo"

## 相关工具

- [BaiduSearchTool](./baidu-search.md) - 基于百度搜索引擎的搜索工具
- [BingSearchTool](./bing-search.md) - 基于必应搜索引擎的搜索工具
- [GoogleSearchTool](./google-search.md) - 基于 Google 搜索引擎的搜索工具
- [TavilySearchTool](./tavily-search.md) - 基于 Tavily API 的搜索工具（需要 API Key）
- [WebFetchTool](./web-fetch.md) - 网页内容抓取工具