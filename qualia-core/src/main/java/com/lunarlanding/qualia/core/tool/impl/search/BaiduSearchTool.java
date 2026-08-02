package com.lunarlanding.qualia.core.tool.impl.search;

import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百度搜索工具（免费，无需 API Key，国内可用）
 * 使用百度 HTML 接口进行搜索
 */
public class BaiduSearchTool extends FunctionTool {

    private static final Logger logger = LoggerFactory.getLogger(BaiduSearchTool.class);
    private static final String SEARCH_URL = "https://www.baidu.com/s?wd=";
    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final int TIMEOUT_SECONDS = 15;

    // 正则表达式：提取搜索结果
    // 百度搜索结果的标题和链接
    private static final Pattern RESULT_PATTERN = Pattern.compile(
        "<h3[^>]*class=\"[^\"]*t[^\"]*\"[^>]*>.*?<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
        Pattern.DOTALL
    );
    
    // 百度搜索结果的摘要（页面改版频繁，按优先级尝试多种结构，在单条结果块内就近匹配）
    private static final Pattern[] SNIPPET_PATTERNS = {
        // 2025+ 新版：摘要在 summary-text_xxx 的 span 内（已用真实页面验证）
        Pattern.compile("<span[^>]*class=\"[^\"]*summary-text[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL),
        Pattern.compile("<span[^>]*class=\"[^\"]*content-right[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL),
        Pattern.compile("<div[^>]*class=\"[^\"]*c-abstract[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL),
        Pattern.compile("<(?:span|div)[^>]*class=\"[^\"]*c-line-clamp[^\"]*\"[^>]*>(.*?)</(?:span|div)>", Pattern.DOTALL),
        Pattern.compile("<span[^>]*class=\"[^\"]*c-color-text[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL)
    };
    
    // HTML 标签清理
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final HttpClient httpClient;

    public BaiduSearchTool() {
        this.setName("baidu_search");
        this.setDescription("使用百度进行网络搜索（免费，无需 API Key，国内可用）。返回搜索结果的标题、链接和摘要。");
        this.setParameters(new Parameter[]{
                new Parameter("query", "搜索查询语句", "string", true),
                new Parameter("max_results", "返回结果数量，默认10", "number", false)
        });

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            String query = (String) arguments.get("query");
            if (query == null || query.trim().isEmpty()) {
                return "错误：搜索查询不能为空";
            }

            int maxResults = DEFAULT_MAX_RESULTS;
            Object maxResultsObj = arguments.get("max_results");
            if (maxResultsObj instanceof Number) {
                maxResults = ((Number) maxResultsObj).intValue();
            }

            logger.info("执行百度搜索: {}", query);

            // 构建搜索 URL
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = SEARCH_URL + encodedQuery;

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "错误：搜索请求失败，HTTP 状态码 " + response.statusCode();
            }

            // 解析搜索结果
            List<SearchResult> results = parseSearchResults(response.body(), maxResults);

            if (results.isEmpty()) {
                return "未找到相关结果";
            }

            // 格式化输出
            return formatResults(results);

        } catch (java.net.http.HttpTimeoutException e) {
            logger.error("百度搜索超时", e);
            return "错误：搜索请求超时，请稍后重试";
        } catch (Exception e) {
            logger.error("百度搜索失败", e);
            return "错误：搜索失败 - " + e.getMessage();
        }
    }

    /**
     * 解析搜索结果
     */
    private List<SearchResult> parseSearchResults(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        // 先定位每条结果的标题与位置
        Matcher resultMatcher = RESULT_PATTERN.matcher(html);
        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<int[]> positions = new ArrayList<>();

        while (resultMatcher.find() && urls.size() < maxResults) {
            String url = resultMatcher.group(1);
            String title = cleanHtml(resultMatcher.group(2));

            // 百度的链接可能是重定向链接，需要提取真实 URL
            url = extractRealUrl(url);

            if (!url.isEmpty() && !title.isEmpty()) {
                urls.add(url);
                titles.add(title);
                positions.add(new int[]{resultMatcher.start(), resultMatcher.end()});
            }
        }

        // 摘要在各自结果块内（当前标题到下一标题之间）就近提取，
        // 避免全局两列表按下标硬配对导致的摘要缺失与错位
        for (int i = 0; i < urls.size(); i++) {
            int from = positions.get(i)[1];
            int to = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : Math.min(html.length(), from + 8000);
            String snippet = extractSnippet(html.substring(from, Math.max(from, to)));
            results.add(new SearchResult(titles.get(i), urls.get(i), snippet));
        }

        return results;
    }

    /**
     * 在单条结果块内按优先级提取摘要
     */
    private String extractSnippet(String block) {
        for (Pattern pattern : SNIPPET_PATTERNS) {
            Matcher m = pattern.matcher(block);
            if (m.find()) {
                String snippet = cleanHtml(m.group(1));
                if (!snippet.isEmpty()) {
                    return snippet;
                }
            }
        }
        return "";
    }

    /**
     * 清理 HTML 标签
     */
    private String cleanHtml(String html) {
        if (html == null) {
            return "";
        }
        String text = HTML_TAG_PATTERN.matcher(html).replaceAll("");
        // 反转义常见 HTML 实体
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    /**
     * 提取真实 URL（处理百度的重定向链接）
     */
    private String extractRealUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        // 百度的重定向格式: http://www.baidu.com/link?url=...
        // 我们直接返回这个链接，用户点击时会自动跳转
        // 如果需要获取真实 URL，需要发送 HEAD 请求获取 Location 头
        
        // 如果是相对路径，添加 https://www.baidu.com 前缀
        if (url.startsWith("/")) {
            return "https://www.baidu.com" + url;
        }
        
        // 如果已经是完整 URL，直接返回
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        
        // 其他情况，添加 https:// 前缀
        return "https://" + url;
    }

    /**
     * 格式化搜索结果
     */
    private String formatResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("搜索结果：\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(String.format("%d. %s\n", i + 1, result.title));
            sb.append(String.format("   链接: %s\n", result.url));
            if (!result.snippet.isEmpty()) {
                sb.append(String.format("   摘要: %s\n", result.snippet));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    /**
     * 搜索结果内部类
     */
    private static class SearchResult {
        String title;
        String url;
        String snippet;

        SearchResult(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }
}
