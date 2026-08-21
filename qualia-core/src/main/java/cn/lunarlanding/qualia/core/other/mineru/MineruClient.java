package cn.lunarlanding.qualia.core.other.mineru;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.lunarlanding.qualia.core.other.mineru.model.MineruBatchResult;
import cn.lunarlanding.qualia.core.other.mineru.model.MineruBatchSubmitResult;
import cn.lunarlanding.qualia.core.other.mineru.model.MineruExtractResult;
import cn.lunarlanding.qualia.core.other.mineru.model.MineruFileSubmission;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * MinerU HTTP 客户端。
 *
 * <p>封装 URL 批量提交、批量结果查询和结果 zip 下载，业务侧只需要保存 batchId 和 fullZipUrl。</p>
 */
public class MineruClient {

    private static final int DOWNLOAD_MAX_ATTEMPTS = 4;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(8);

    private final HttpClient httpClient;
    private final HttpClient downloadHttpClient;

    public MineruClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    public MineruClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.downloadHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 提交 URL 批量解析任务。
     *
     * <p>files 中的 url 必须是 MinerU 服务端可访问的公网地址；dataId 会随结果返回，用于业务侧匹配本地文件。</p>
     */
    public MineruBatchSubmitResult submitUrlBatch(String baseUrl,
                                                  String apiKey,
                                                  java.util.List<MineruFileSubmission> files,
                                                  String modelVersion,
                                                  boolean noCache) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("MinerU files 不能为空");
        }

        JSONArray fileArray = new JSONArray();
        for (MineruFileSubmission file : files) {
            JSONObject item = new JSONObject();
            item.put("url", file.url());
            if (file.dataId() != null && !file.dataId().isBlank()) {
                item.put("data_id", file.dataId());
            }
            fileArray.add(item);
        }

        JSONObject body = new JSONObject();
        body.put("files", fileArray);
        if (modelVersion != null && !modelVersion.isBlank()) {
            body.put("model_version", modelVersion);
        }
        body.put("no_cache", noCache);

        JSONObject response = request(baseUrl, apiKey, "POST", "/api/v4/extract/task/batch", body);
        String batchId = firstString(response, "batch_id", "batchId");
        JSONObject data = response.getJSONObject("data");
        if (batchId == null && data != null) {
            batchId = firstString(data, "batch_id", "batchId");
        }
        if (batchId == null || batchId.isBlank()) {
            throw new MineruException("MinerU 未返回 batch_id: " + response.toJSONString());
        }
        return new MineruBatchSubmitResult(batchId);
    }

    /**
     * 查询批量解析结果。
     *
     * <p>MinerU 不同返回结构可能把结果放在 extract_result、extract_results、results 或 data 本身，
     * 这里统一收敛为 MineruBatchResult。</p>
     */
    public MineruBatchResult getBatchResult(String baseUrl, String apiKey, String batchId) {
        JSONObject response = request(baseUrl, apiKey, "GET", "/api/v4/extract-results/batch/" + batchId, null);
        Object data = response.get("data");
        java.util.List<MineruExtractResult> results = new java.util.ArrayList<>();

        if (data instanceof JSONObject dataObject) {
            collectResults(dataObject.get("extract_result"), results);
            collectResults(dataObject.get("extract_results"), results);
            collectResults(dataObject.get("results"), results);
            if (results.isEmpty() && looksLikeResult(dataObject)) {
                results.add(toExtractResult(dataObject));
            }
        } else {
            collectResults(data, results);
        }

        return new MineruBatchResult(results);
    }

    /**
     * 下载 MinerU full zip 到本地文件。
     *
     * <p>下载使用 HTTP/1.1、自动跟随重定向和 .part 临时文件；失败会做有限重试，避免留下半截 zip。</p>
     */
    public Path downloadTo(String url, Path target) {
        ensureParentDirectory(target);
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        MineruException lastFailure = null;

        for (int attempt = 1; attempt <= DOWNLOAD_MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(DOWNLOAD_TIMEOUT)
                    .header("Accept", "application/zip,application/octet-stream,*/*")
                    .header("User-Agent", "Qualia-MinerU-Client/1.0")
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .build();
            try {
                Files.deleteIfExists(temp);
                HttpResponse<Path> response = downloadHttpClient.send(request, HttpResponse.BodyHandlers.ofFile(temp));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    if (Files.size(temp) <= 0) {
                        throw new MineruException("下载 MinerU 结果失败，响应文件为空");
                    }
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                    return target;
                }
                lastFailure = new MineruException("下载 MinerU 结果失败，HTTP " + response.statusCode()
                        + "，attempt=" + attempt + "/" + DOWNLOAD_MAX_ATTEMPTS);
            } catch (IOException e) {
                lastFailure = new MineruException("下载 MinerU 结果失败，attempt=" + attempt + "/"
                        + DOWNLOAD_MAX_ATTEMPTS + "，原因: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MineruException("下载 MinerU 结果被中断", e);
            }
            sleepBeforeRetry(attempt);
        }

        throw lastFailure == null
                ? new MineruException("下载 MinerU 结果失败，未知错误")
                : lastFailure;
    }

    private void sleepBeforeRetry(int attempt) {
        if (attempt >= DOWNLOAD_MAX_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(Duration.ofSeconds(2L * attempt).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MineruException("下载 MinerU 结果重试等待被中断", e);
        }
    }

    private void ensureParentDirectory(Path target) {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new MineruException("创建 MinerU 结果目录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 统一执行 MinerU API 请求并处理 HTTP/code 两层错误。
     */
    private JSONObject request(String baseUrl, String apiKey, String method, String path, JSONObject body) {
        String url = normalizeBaseUrl(baseUrl) + path;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(3))
                    .header("Accept", "*/*");
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(body.toJSONString()));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MineruException("MinerU API 请求失败，HTTP " + response.statusCode() + ": " + response.body());
            }
            JSONObject json = JSON.parseObject(response.body());
            Integer code = json.getInteger("code");
            if (code != null && code != 0) {
                String message = firstString(json, "msg", "message", "error");
                throw new MineruException("MinerU API 返回错误: " + (message == null ? response.body() : message));
            }
            return json;
        } catch (IOException e) {
            throw new MineruException("MinerU API 请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MineruException("MinerU API 请求被中断", e);
        }
    }

    private void collectResults(Object value, java.util.List<MineruExtractResult> results) {
        if (value == null) {
            return;
        }
        if (value instanceof JSONArray array) {
            for (Object item : array) {
                collectResults(item, results);
            }
        } else if (value instanceof JSONObject object) {
            results.add(toExtractResult(object));
        }
    }

    private MineruExtractResult toExtractResult(JSONObject object) {
        JSONObject progress = object.getJSONObject("extract_progress");
        Integer extractedPages = progress == null ? null : progress.getInteger("extracted_pages");
        Integer totalPages = progress == null ? null : progress.getInteger("total_pages");
        return new MineruExtractResult(
                firstString(object, "file_name", "fileName", "name"),
                firstString(object, "data_id", "dataId"),
                firstString(object, "state", "status"),
                firstString(object, "full_zip_url", "fullZipUrl", "zip_url"),
                firstString(object, "err_msg", "error_message", "message"),
                extractedPages,
                totalPages
        );
    }

    private boolean looksLikeResult(JSONObject object) {
        return object.containsKey("state")
                || object.containsKey("status")
                || object.containsKey("full_zip_url")
                || object.containsKey("data_id");
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://mineru.net" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String firstString(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            Object value = object.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

}
