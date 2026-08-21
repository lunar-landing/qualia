package cn.lunarlanding.qualia.core.tool.impl.internet;

import com.alibaba.fastjson.JSON;
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
 * HTTP请求工具，用于调用外部HTTP服务
 */
public class HttpTool extends FunctionTool {

    private static final Logger logger = LoggerFactory.getLogger(HttpTool.class);

    private final HttpClient httpClient;

    public HttpTool() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.setName("http_request");
        this.setDescription("发送HTTP请求，支持GET/POST/PUT/DELETE等方法，用于调用外部API服务");
        this.setParameters(new Parameter[]{
                new Parameter("url", "请求地址URL", "string", true),
                new Parameter("headers", "请求头，JSON对象格式，如{\"Content-Type\":\"application/json\"}", "object", false),
                new Parameter("method", "HTTP方法: GET/POST/PUT/DELETE，默认GET", "string", false),
                new Parameter("body", "请求体，用于POST/PUT请求", "string", false)
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            // 获取URL
            String url = (String) arguments.get("url");

            if (url == null || url.trim().isEmpty()) {
                return "错误：URL不能为空";
            }

            String method = "GET";
            if (arguments.containsKey("method") && arguments.get("method") != null) {
                method = ((String) arguments.get("method")).toUpperCase();
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30));
            if (arguments.containsKey("headers") && arguments.get("headers") != null) {
                Object headersObj = arguments.get("headers");
                if (headersObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> headers = (Map<String, Object>) headersObj;
                    for (Map.Entry<String, Object> entry : headers.entrySet()) {
                        if (entry.getValue() != null) {
                            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                    }
                }
            }

            // 设置默认Content-Type（如果有body且未设置headers）
            if (("POST".equals(method) || "PUT".equals(method)) && !arguments.containsKey("headers")) {
                requestBuilder.header("Content-Type", "application/json");
            }

            String body = null;
            if (arguments.containsKey("body") && arguments.get("body") != null) {
                body = (String) arguments.get("body");
            }

            HttpRequest.BodyPublisher bodyPublisher = body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();

            switch (method) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    requestBuilder.POST(bodyPublisher);
                    break;
                case "PUT":
                    requestBuilder.PUT(bodyPublisher);
                    break;
                case "DELETE":
                    if (body != null) {
                        requestBuilder.method("DELETE", bodyPublisher);
                    } else {
                        requestBuilder.DELETE();
                    }
                    break;
                case "PATCH":
                    requestBuilder.method("PATCH", bodyPublisher);
                    break;
                case "HEAD":
                    requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                    break;
                case "OPTIONS":
                    requestBuilder.method("OPTIONS", HttpRequest.BodyPublishers.noBody());
                    break;
                default:
                    return "错误：不支持的HTTP方法: " + method;
            }

            // 发送请求
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            logger.info("HTTP请求完成: status={}, url={}", statusCode, url);

            // 构建包含状态码的响应
            JSONObject result = new JSONObject();
            result.put("status", statusCode);
            result.put("method", method);
            result.put("url", url);

            if (statusCode >= 200 && statusCode < 300) {
                // 成功响应
                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    try {
                        // 尝试解析为JSON以验证格式
                        Object parsedBody = JSON.parse(responseBody);
                        System.err.println(parsedBody);
                        result.put("data", parsedBody);
                    } catch (Exception e) {
                        // 非JSON格式，直接返回原始内容
                        result.put("data", responseBody);
                    }
                } else {
                    result.put("data", null);
                    result.put("message", "响应体为空");
                }
            } else {
                // 错误响应
                result.put("error", true);
                result.put("message", "HTTP错误: " + statusCode);
                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    result.put("details", responseBody);
                }
            }

            return result.toJSONString();

        } catch (Exception e) {
            logger.error("HTTP请求执行失败", e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("error", true);
            errorResult.put("message", e.getMessage());
            return errorResult.toJSONString();
        }
    }
}
