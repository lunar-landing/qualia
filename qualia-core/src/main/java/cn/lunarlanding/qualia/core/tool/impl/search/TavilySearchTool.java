package cn.lunarlanding.qualia.core.tool.impl.search;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.lunarlanding.qualia.core.tool.FunctionTool;
import cn.lunarlanding.qualia.core.tool.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Tavily 搜索工具
 * 基于 Tavily API 的网络搜索适配器
 */
public class TavilySearchTool extends FunctionTool {

    private static final Logger logger = LoggerFactory.getLogger(TavilySearchTool.class);
    private static final String ENDPOINT = "https://api.tavily.com/search";
    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final String DEFAULT_TOPIC = "general";
    private final String apiKey;
    private final HttpClient httpClient;

    /**
     * 构造函数
     *
     * @param apiKey Tavily API Key（格式：tvly-<your_key>）
     */
    public TavilySearchTool(String apiKey) {
        this.setName("tavily_search");
        this.setDescription("使用 Tavily API 进行网络搜索。当需要获取实时网络信息、新闻或最新资料时使用此工具。");
        this.setParameters(new Parameter[]{
                new Parameter("query", "搜索查询语句，描述你想查找的内容", "string", true)
        });

        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        clientBuilder.connectTimeout(Duration.ofSeconds(30));
        this.httpClient = clientBuilder.build();
        this.apiKey = apiKey;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.trim().isEmpty()) {
            return "错误：搜索查询不能为空";
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("query", query);
            requestBody.put("max_results", DEFAULT_MAX_RESULTS);
            requestBody.put("topic", DEFAULT_TOPIC);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            requestBuilder.uri(URI.create(ENDPOINT));
            requestBuilder.timeout(Duration.ofSeconds(30));
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.header("Authorization", "Bearer " + apiKey);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()));
            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            logger.info("Tavily 搜索完成: status={}, query={}", statusCode, query);

            if (statusCode >= 200 && statusCode < 300) {
                return formatResults(responseBody, query);
            } else {
                JSONObject errorResult = new JSONObject();
                errorResult.put("error", true);
                errorResult.put("message", "Tavily API 错误: " + statusCode);
                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    errorResult.put("details", responseBody);
                }
                return errorResult.toJSONString();
            }

        } catch (Exception e) {
            logger.error("Tavily 搜索执行失败", e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("error", true);
            errorResult.put("message", e.getMessage());
            return errorResult.toJSONString();
        }
    }

    /**
     * 格式化搜索结果
     */
    private String formatResults(String responseBody, String query) {
        try {
            JSONObject response = JSON.parseObject(responseBody);
            JSONArray results = response.getJSONArray("results");

            JSONObject output = new JSONObject();
            output.put("source", "tavily");
            output.put("query", query);

            if (results != null && !results.isEmpty()) {
                output.put("count", results.size());
                JSONArray items = new JSONArray();

                for (int i = 0; i < results.size(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    JSONObject item = new JSONObject();
                    item.put("title", result.getString("title"));
                    item.put("url", result.getString("url"));
                    item.put("content", result.getString("content"));
                    item.put("score", result.getBigDecimal("score"));
                    items.add(item);
                }
                output.put("items", items);
            } else {
                output.put("count", 0);
                output.put("items", new JSONArray());
            }

            Double responseTime = response.getDouble("response_time");
            if (responseTime != null) {
                output.put("response_time", responseTime);
            }

            return output.toJSONString();
        } catch (Exception e) {
            logger.warn("解析 Tavily 响应失败，返回原始内容", e);
            return responseBody;
        }
    }

    public String getApiKey() {
        return apiKey;
    }
}
