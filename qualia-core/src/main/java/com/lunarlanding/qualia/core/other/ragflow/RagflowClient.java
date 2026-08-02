package com.lunarlanding.qualia.core.other.ragflow;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lunarlanding.qualia.core.other.ragflow.model.RagflowDocumentInfo;
import com.lunarlanding.qualia.core.other.ragflow.model.RagflowDocumentParsingStatus;
import com.lunarlanding.qualia.core.other.ragflow.model.RagflowManualChunk;
import com.lunarlanding.qualia.core.other.ragflow.model.RagflowRemoteChunk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RagFlow 知识库管理客户端。
 *
 * <p>当前主要服务于 MinerU + AI 分段链路：创建原文档、切换 manual 模式、清空旧 chunks、
 * 写入新的 manual chunks。</p>
 */
public class RagflowClient {

    private static final String MULTIPART_FORM_DATA = "multipart/form-data";
    private static final int IO_RETRY_TIMES = 3;
    private static final long IO_RETRY_BACKOFF_MS = 800L;

    private final HttpClient httpClient;

    public RagflowClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    public RagflowClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 按文件名查找 RagFlow 文档 ID。
     *
     * <p>业务侧用于历史记录补绑定 documentId，只有唯一匹配时才应继续覆盖 chunks。</p>
     */
    public java.util.List<String> findDocumentIds(String address, String apiKey, String datasetId, String fileName) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (RagflowDocumentInfo document : listDocuments(address, apiKey, datasetId, fileName)) {
            if (fileName.equals(document.name()) && document.id() != null && !document.id().isBlank()) {
                ids.add(document.id());
            }
        }
        return ids;
    }

    public java.util.List<RagflowDocumentInfo> listDocuments(String address, String apiKey, String datasetId, String name) {
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId + "/documents";
        if (name != null && !name.isBlank()) {
            url += "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        }
        JSONObject response = request(address, apiKey, "GET", url, null);

        java.util.List<RagflowDocumentInfo> result = new java.util.ArrayList<>();
        for (JSONObject document : extractDocuments(response.get("data"))) {
            result.add(toDocumentInfo(document));
        }
        return result;
    }

    public void deleteDocuments(String address, String apiKey, String datasetId, java.util.List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        JSONObject body = new JSONObject();
        body.put("ids", documentIds);
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId + "/documents";
        request(address, apiKey, "DELETE", url, body);
    }

    public String uploadDocument(String address, String apiKey, String datasetId, File file, String fileName) {
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId + "/documents";
        try {
            return uploadDocument(address, apiKey, url, file, fileName, "file", Map.of());
        } catch (RagflowException firstFailure) {
            try {
                return uploadDocument(address, apiKey, url, file, fileName, "files", Map.of());
            } catch (RagflowException ignored) {
                throw firstFailure;
            }
        }
    }

    /**
     * 上传原始文档并尽量设置为 manual chunk 模式。
     *
     * <p>不同 RagFlow 版本的 multipart 字段名和 chunk_method 支持不完全一致，因此包含 file/files
     * 与后置更新的兼容路径。</p>
     */
    public String uploadDocumentForManualChunks(String address, String apiKey, String datasetId, File file, String fileName) {
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId + "/documents";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("chunk_method", "manual");
        try {
            String documentId;
            try {
                documentId = uploadDocument(address, apiKey, url, file, fileName, "file", fields);
            } catch (RagflowException firstFailure) {
                try {
                    documentId = uploadDocument(address, apiKey, url, file, fileName, "files", fields);
                } catch (RagflowException ignored) {
                    throw firstFailure;
                }
            }
            tryUpdateChunkMethod(address, apiKey, datasetId, documentId);
            return documentId;
        } catch (RagflowException manualFieldFailure) {
            try {
                String documentId = uploadDocument(address, apiKey, datasetId, file, fileName);
                tryUpdateChunkMethod(address, apiKey, datasetId, documentId);
                return documentId;
            } catch (RagflowException fallbackFailure) {
                fallbackFailure.addSuppressed(manualFieldFailure);
                throw fallbackFailure;
            }
        }
    }

    /**
     * 将已有文档切到 manual chunk 模式。
     */
    public void updateDocumentChunkMethodToManual(String address, String apiKey, String datasetId, String documentId) {
        tryUpdateChunkMethod(address, apiKey, datasetId, documentId);
    }

    /**
     * Uses RagFlow's native document switch. RagFlow updates both the document status and
     * every indexed chunk's available_int field without deleting or rebuilding chunks.
     */
    public void updateDocumentEnabled(String address,
                                      String apiKey,
                                      String datasetId,
                                      String documentId,
                                      boolean enabled) {
        JSONObject body = new JSONObject();
        body.put("enabled", enabled);
        String documentUrl = normalizeAddress(address) + "/api/v1/datasets/" + datasetId
                + "/documents/" + documentId;
        try {
            request(address, apiKey, "PATCH", documentUrl, body);
        } catch (RagflowException patchFailure) {
            try {
                request(address, apiKey, "PUT", documentUrl, body);
            } catch (RagflowException putFailure) {
                JSONObject fallbackBody = new JSONObject();
                fallbackBody.put("doc_ids", java.util.List.of(documentId));
                fallbackBody.put("status", enabled ? "1" : "0");
                String fallbackUrl = normalizeAddress(address) + "/api/v1/datasets/" + datasetId
                        + "/documents/batch-update-status";
                try {
                    request(address, apiKey, "POST", fallbackUrl, fallbackBody);
                } catch (RagflowException ignored) {
                    patchFailure.addSuppressed(putFailure);
                    throw patchFailure;
                }
            }
        }
    }

    public void startParsingDocuments(String address, String apiKey, String datasetId, java.util.List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        JSONObject body = new JSONObject();
        body.put("document_ids", documentIds);
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId + "/chunks";
        request(address, apiKey, "POST", url, body);
    }

    public RagflowDocumentParsingStatus getDocumentParsingStatus(String address,
                                                                 String apiKey,
                                                                 String datasetId,
                                                                 String documentId) {
        String encodedId = URLEncoder.encode(documentId, StandardCharsets.UTF_8);
        String listUrl = normalizeAddress(address) + "/api/v1/datasets/" + datasetId
                + "/documents?id=" + encodedId + "&page=1&page_size=100";
        JSONObject response = request(address, apiKey, "GET", listUrl, null);
        JSONObject document = findDocument(response.get("data"), documentId);
        if (document == null) {
            return null;
        }
        return toDocumentParsingStatus(document);
    }

    public String createManualDocument(String address, String apiKey, String datasetId, String fileName) {
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId + "/documents";
        RagflowException createFailure = null;

        JSONObject manualBody = new JSONObject();
        manualBody.put("name", fileName);
        manualBody.put("chunk_method", "manual");
        try {
            JSONObject response = request(address, apiKey, "POST", url, manualBody);
            return extractDocumentId(response);
        } catch (RagflowException manualCreateFailure) {
            createFailure = manualCreateFailure;
        }

        JSONObject emptyBody = new JSONObject();
        emptyBody.put("name", fileName);
        emptyBody.put("type", "empty");
        try {
            JSONObject response = request(address, apiKey, "POST", url, emptyBody);
            String documentId = extractDocumentId(response);
            tryUpdateChunkMethod(address, apiKey, datasetId, documentId);
            return documentId;
        } catch (RagflowException emptyCreateFailure) {
            emptyCreateFailure.addSuppressed(createFailure);
            createFailure = emptyCreateFailure;
        }

        try {
            return uploadPlaceholderManualDocument(address, apiKey, datasetId, url, fileName);
        } catch (RagflowException fallbackFailure) {
            fallbackFailure.addSuppressed(createFailure);
            throw fallbackFailure;
        }
    }

    /**
     * 向指定 RagFlow 文档追加一条 manual chunk。
     *
     * <p>RagFlow API 只写入 content 和 important_keywords，标题、页码等扩展字段保存在本地 chunk 表中。</p>
     */
    public String addChunk(String address,
                           String apiKey,
                           String datasetId,
                           String documentId,
                           RagflowManualChunk chunk) {
        JSONObject body = new JSONObject();
        body.put("content", chunk.getContent());

        java.util.List<String> keywords = chunk.getImportantKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            body.put("important_keywords", keywords);
        }

        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId
                + "/documents/" + documentId + "/chunks";
        JSONObject response = request(address, apiKey, "POST", url, body);
        return extractChunkId(response);
    }

    /**
     * Updates one existing manual chunk without replacing its identity.
     */
    public void updateChunk(String address,
                            String apiKey,
                            String datasetId,
                            String documentId,
                            String chunkId,
                            RagflowManualChunk chunk) {
        JSONObject body = new JSONObject();
        body.put("content", chunk.getContent());

        java.util.List<String> keywords = chunk.getImportantKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            body.put("important_keywords", keywords);
        }

        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId
                + "/documents/" + documentId + "/chunks/" + chunkId;
        try {
            request(address, apiKey, "PATCH", url, body);
        } catch (RagflowException firstFailure) {
            try {
                request(address, apiKey, "PUT", url, body);
            } catch (RagflowException ignored) {
                throw firstFailure;
            }
        }
    }

    /**
     * 删除文档下的全部 chunks，用于 AI/Excel 分段覆盖前清空旧内容。
     */
    public void deleteAllChunks(String address, String apiKey, String datasetId, String documentId) {
        java.util.List<String> chunkIds = listChunkIds(address, apiKey, datasetId, documentId);
        if (chunkIds.isEmpty()) {
            return;
        }
        deleteChunks(address, apiKey, datasetId, documentId, chunkIds);
    }

    public java.util.List<String> listChunkIds(String address, String apiKey, String datasetId, String documentId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (RagflowRemoteChunk chunk : listChunks(address, apiKey, datasetId, documentId)) {
            ids.add(chunk.id());
        }
        return ids;
    }

    public java.util.List<RagflowRemoteChunk> listChunks(String address,
                                                        String apiKey,
                                                        String datasetId,
                                                        String documentId) {
        java.util.List<RagflowRemoteChunk> result = new java.util.ArrayList<>();
        int page = 1;
        int pageSize = 100;
        while (true) {
            String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId
                    + "/documents/" + documentId + "/chunks?page=" + page + "&page_size=" + pageSize;
            JSONObject response = request(address, apiKey, "GET", url, null);
            java.util.List<JSONObject> chunks = extractChunks(response.get("data"));
            for (JSONObject chunk : chunks) {
                String id = firstString(chunk, "id", "chunk_id", "chunkId");
                if (id != null) {
                    result.add(new RagflowRemoteChunk(id, firstString(chunk, "content")));
                }
            }
            if (chunks.size() < pageSize) {
                break;
            }
            page++;
        }
        return result;
    }

    public void deleteChunks(String address,
                             String apiKey,
                             String datasetId,
                             String documentId,
                             java.util.List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        int batchSize = 100;
        for (int i = 0; i < chunkIds.size(); i += batchSize) {
            deleteChunkBatch(address, apiKey, datasetId, documentId,
                    chunkIds.subList(i, Math.min(i + batchSize, chunkIds.size())));
        }
    }

    private String uploadDocument(String address,
                                  String apiKey,
                                  String url,
                                  File file,
                                  String fileName,
                                  String fieldName,
                                  Map<String, String> fields) {
        String boundary = "----QualiaBoundary" + UUID.randomUUID().toString().replace("-", "");
        try {
            byte[] body = buildMultipartBody(boundary, file, fileName, fieldName, fields);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MULTIPART_FORM_DATA + "; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = sendWithRetry(httpRequest, HttpResponse.BodyHandlers.ofString(), normalizeAddress(address));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RagflowException("RagFlow 文档上传失败，HTTP " + response.statusCode() + ": " + response.body());
            }
            JSONObject responseJson = parseJsonResponse(response.body(), url);
            Integer code = responseJson.getInteger("code");
            if (code != null && code != 0) {
                String message = responseJson.getString("message");
                throw new RagflowException("RagFlow 文档上传返回错误: " + (message == null ? response.body() : message));
            }
            return extractDocumentId(responseJson);
        } catch (IOException e) {
            throw new RagflowException("读取待上传文件失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RagflowException("RagFlow 文档上传被中断", e);
        }
    }

    private String uploadPlaceholderManualDocument(String address, String apiKey, String datasetId, String url, String fileName) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ragflow-manual-", ".txt");
            Files.writeString(tempFile, "Manual chunks placeholder.\n", StandardCharsets.UTF_8);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("chunk_method", "manual");
            String documentId;
            try {
                documentId = uploadDocument(
                        address, apiKey, url, tempFile.toFile(), manualPlaceholderFilename(fileName), "file", fields);
            } catch (RagflowException firstFailure) {
                try {
                    documentId = uploadDocument(
                            address, apiKey, url, tempFile.toFile(), manualPlaceholderFilename(fileName), "files", fields);
                } catch (RagflowException ignored) {
                    throw firstFailure;
                }
            }

            tryUpdateChunkMethod(address, apiKey, datasetId, documentId);
            try {
                deleteAllChunks(address, apiKey, datasetId, documentId);
            } catch (Exception ignored) {
                // RagFlow may delay parsing the uploaded placeholder; manual chunks can still be appended.
            }
            return documentId;
        } catch (IOException e) {
            throw new RagflowException("创建 RagFlow 占位文档失败: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void deleteChunkBatch(String address,
                                  String apiKey,
                                  String datasetId,
                                  String documentId,
                                  java.util.List<String> chunkIds) {
        JSONObject body = new JSONObject();
        body.put("chunk_ids", chunkIds);
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId
                + "/documents/" + documentId + "/chunks";
        try {
            request(address, apiKey, "DELETE", url, body);
        } catch (RagflowException firstFailure) {
            JSONObject fallbackBody = new JSONObject();
            fallbackBody.put("ids", chunkIds);
            try {
                request(address, apiKey, "DELETE", url, fallbackBody);
            } catch (RagflowException ignored) {
                throw firstFailure;
            }
        }
    }

    private void tryUpdateChunkMethod(String address, String apiKey, String datasetId, String documentId) {
        JSONObject body = new JSONObject();
        body.put("chunk_method", "manual");
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId + "/documents/" + documentId;
        try {
            request(address, apiKey, "PUT", url, body);
        } catch (Exception ignored) {
            // Some RagFlow versions infer manual mode for empty documents or do not support this update endpoint.
        }
    }

    private JSONObject request(String address, String apiKey, String method, String url, JSONObject body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");

            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.method("DELETE", body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toJSONString()));
            } else {
                builder.method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toJSONString()));
            }

            HttpResponse<String> response = sendWithRetry(builder.build(), HttpResponse.BodyHandlers.ofString(), normalizeAddress(address));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RagflowException("RagFlow API 调用失败，HTTP " + response.statusCode() + ": " + response.body());
            }

            JSONObject responseJson = parseJsonResponse(response.body(), url);
            Integer code = responseJson.getInteger("code");
            if (code != null && code != 0) {
                String message = responseJson.getString("message");
                if (message == null || message.isBlank()) {
                    message = responseJson.getString("msg");
                }
                throw new RagflowException("RagFlow API 返回错误: " + (message == null ? response.body() : message));
            }
            return responseJson;
        } catch (IOException e) {
            throw new RagflowException("无法连接 RagFlow 服务 " + normalizeAddress(address) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RagflowException("RagFlow API 调用被中断", e);
        }
    }

    private <T> HttpResponse<T> sendWithRetry(HttpRequest request,
                                              HttpResponse.BodyHandler<T> bodyHandler,
                                              String normalizedAddress) throws IOException, InterruptedException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= IO_RETRY_TIMES; attempt++) {
            try {
                return httpClient.send(request, bodyHandler);
            } catch (IOException e) {
                lastException = e;
                if (attempt >= IO_RETRY_TIMES) {
                    break;
                }
                sleepBeforeRetry();
            }
        }
        throw lastException;
    }

    private void sleepBeforeRetry() throws InterruptedException {
        Thread.sleep(IO_RETRY_BACKOFF_MS);
    }

    private JSONObject parseJsonResponse(String responseBody, String url) {
        String body = responseBody == null ? "" : responseBody.stripLeading();
        if (body.isEmpty()) {
            throw new RagflowException("RagFlow API 返回空响应: " + url);
        }
        if (!body.startsWith("{") && !body.startsWith("[")) {
            throw new RagflowException("RagFlow API 返回非 JSON 响应: " + url
                    + ", preview=" + previewResponse(body));
        }
        try {
            return JSON.parseObject(body);
        } catch (Exception e) {
            throw new RagflowException("RagFlow API JSON 解析失败: " + url
                    + ", preview=" + previewResponse(body), e);
        }
    }

    private String previewResponse(String body) {
        String normalized = body
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        int maxLength = 160;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private byte[] buildMultipartBody(String boundary,
                                      File file,
                                      String fileName,
                                      String fieldName,
                                      Map<String, String> fields) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String lineBreak = "\r\n";
        String safeName = fileName == null || fileName.isBlank() ? file.getName() : fileName;

        if (fields != null) {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                outputStream.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
                outputStream.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"" + lineBreak + lineBreak)
                        .getBytes(StandardCharsets.UTF_8));
                outputStream.write((field.getValue() == null ? "" : field.getValue()).getBytes(StandardCharsets.UTF_8));
                outputStream.write(lineBreak.getBytes(StandardCharsets.UTF_8));
            }
        }

        outputStream.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + safeName + "\"" + lineBreak)
                .getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Type: application/octet-stream" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        outputStream.write(Files.readAllBytes(file.toPath()));
        outputStream.write(lineBreak.getBytes(StandardCharsets.UTF_8));
        outputStream.write(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));
        return outputStream.toByteArray();
    }

    private java.util.List<JSONObject> extractDocuments(Object data) {
        java.util.List<JSONObject> documents = new java.util.ArrayList<>();
        if (data instanceof JSONArray array) {
            collectObjects(array, documents);
            return documents;
        }
        if (data instanceof JSONObject object) {
            Object docs = firstPresent(object, "docs", "documents", "items", "list");
            if (docs instanceof JSONArray array) {
                collectObjects(array, documents);
            } else {
                documents.add(object);
            }
        }
        return documents;
    }

    private java.util.List<JSONObject> extractChunks(Object data) {
        java.util.List<JSONObject> chunks = new java.util.ArrayList<>();
        if (data instanceof JSONArray array) {
            collectObjects(array, chunks);
            return chunks;
        }
        if (data instanceof JSONObject object) {
            Object nested = firstPresent(object, "chunks", "items", "list", "docs");
            if (nested instanceof JSONArray array) {
                collectObjects(array, chunks);
            } else if (firstString(object, "id", "chunk_id") != null) {
                chunks.add(object);
            }
        }
        return chunks;
    }

    private JSONObject findDocument(Object data, String documentId) {
        java.util.List<JSONObject> documents = extractDocuments(data);
        if (documents.isEmpty()) {
            return null;
        }
        for (JSONObject document : documents) {
            String id = firstString(document, "id", "document_id");
            if (documentId.equals(id)) {
                return document;
            }
        }
        if (documents.size() == 1 && firstString(documents.get(0), "id", "document_id") == null) {
            return documents.get(0);
        }
        return null;
    }

    private RagflowDocumentInfo toDocumentInfo(JSONObject document) {
        return new RagflowDocumentInfo(
                firstString(document, "id", "document_id"),
                firstString(document, "name"),
                firstString(document, "type"),
                firstString(document, "chunk_method", "parser_id"),
                firstString(document, "run"),
                firstString(document, "status", "parse_status", "process_status"),
                readDouble(document, "progress", "process_progress", "percent"),
                firstString(document, "progress_msg", "process_msg", "message", "error_message")
        );
    }

    private RagflowDocumentParsingStatus toDocumentParsingStatus(JSONObject document) {
        return new RagflowDocumentParsingStatus(
                firstString(document, "id", "document_id"),
                firstString(document, "run"),
                firstString(document, "status", "parse_status", "process_status"),
                readDouble(document, "progress", "process_progress", "percent"),
                firstString(document, "progress_msg", "process_msg", "message", "error_message")
        );
    }

    private void collectObjects(JSONArray array, java.util.List<JSONObject> objects) {
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (item instanceof JSONObject object) {
                objects.add(object);
            }
        }
    }

    private Object firstPresent(JSONObject object, String... keys) {
        for (String key : keys) {
            if (object.containsKey(key)) {
                return object.get(key);
            }
        }
        return null;
    }

    private String extractDocumentId(JSONObject response) {
        Object data = response.get("data");
        if (data instanceof JSONObject object) {
            String id = firstString(object, "id", "document_id");
            if (id != null) {
                return id;
            }
        }
        if (data instanceof JSONArray array && !array.isEmpty() && array.get(0) instanceof JSONObject object) {
            String id = firstString(object, "id", "document_id");
            if (id != null) {
                return id;
            }
        }
        String id = firstString(response, "id", "document_id");
        if (id != null) {
            return id;
        }
        throw new RagflowException("RagFlow 创建文档响应中未找到 document_id: " + response.toJSONString());
    }

    private String extractChunkId(JSONObject response) {
        Object data = response.get("data");
        if (data instanceof JSONObject object) {
            String id = firstString(object, "id", "chunk_id", "chunkId");
            if (id != null) {
                return id;
            }
            id = extractChunkIdFromNestedObject(object);
            if (id != null) {
                return id;
            }
        }
        if (data instanceof JSONArray array && !array.isEmpty() && array.get(0) instanceof JSONObject object) {
            String id = firstString(object, "id", "chunk_id", "chunkId");
            if (id != null) {
                return id;
            }
        }
        String id = firstString(response, "id", "chunk_id", "chunkId");
        if (id != null) {
            return id;
        }
        return null;
    }

    private String extractChunkIdFromNestedObject(JSONObject object) {
        for (String key : java.util.List.of("chunk", "doc", "item")) {
            Object nested = object.get(key);
            if (nested instanceof JSONObject nestedObject) {
                String id = firstString(nestedObject, "id", "chunk_id", "chunkId");
                if (id != null) {
                    return id;
                }
            }
        }
        for (String key : java.util.List.of("chunks", "items", "list", "docs")) {
            Object nested = object.get(key);
            if (nested instanceof JSONArray array && !array.isEmpty() && array.get(0) instanceof JSONObject nestedObject) {
                String id = firstString(nestedObject, "id", "chunk_id", "chunkId");
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Double readDouble(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Double.parseDouble(text.trim().replace("%", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private String manualPlaceholderFilename(String fileName) {
        String base = fileName == null || fileName.isBlank() ? "manual-chunks" : fileName.trim();
        int dotIndex = base.lastIndexOf('.');
        if (dotIndex > 0) {
            base = base.substring(0, dotIndex);
        }
        return base + "-manual-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
    }

    /**
     * 创建数据集。
     *
     * @param address       RagFlow 服务地址
     * @param apiKey        API Key
     * @param name          数据集名称
     * @param description   数据集描述（可选）
     * @param embeddingModel 向量模型名称（可选）
     * @return 创建的数据集 ID
     */
    public String createDataset(String address, String apiKey, String name,
                                String description, String embeddingModel) {
        String url = normalizeAddress(address) + "/api/v1/datasets";
        JSONObject body = new JSONObject();
        body.put("name", name);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            body.put("embedding_model", embeddingModel);
        }

        JSONObject response = request(address, apiKey, "POST", url, body);
        JSONObject data = response.getJSONObject("data");
        if (data == null || data.getString("id") == null) {
            throw new RagflowException("创建数据集失败: 响应中无 dataset id");
        }
        return data.getString("id");
    }

    /**
     * 删除数据集。
     *
     * @param address   RagFlow 服务地址
     * @param apiKey    API Key
     * @param datasetId 数据集 ID
     */
    public void deleteDataset(String address, String apiKey, String datasetId) {
        String url = normalizeAddress(address) + "/api/v1/datasets/" + datasetId;
        request(address, apiKey, "DELETE", url, null);
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String normalized = address.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
