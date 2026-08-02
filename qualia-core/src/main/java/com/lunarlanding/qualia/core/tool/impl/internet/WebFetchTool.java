package com.lunarlanding.qualia.core.tool.impl.internet;

import com.alibaba.fastjson.JSONObject;
import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页内容抓取工具
 * 用于获取指定URL的网页内容并转换为纯文本
 */
public class WebFetchTool extends FunctionTool {

    private static final Logger logger = LoggerFactory.getLogger(WebFetchTool.class);
    private static final int DEFAULT_MAX_LENGTH = 10000;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("(?is)<(script|style|noscript)[^>]*>.*?</\\1>");
    // 块级标签转换行，保留段落结构（否则正文会被压成一整行）
    private static final Pattern BR_PATTERN = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_CLOSE_PATTERN = Pattern.compile("(?i)</(p|div|li|h[1-6]|tr|blockquote|pre|section|article|table|ul|ol|dd|dt)>");
    // 行内空白压缩（不含换行，换行由 BLANK_LINE_PATTERN 单独收敛）
    private static final Pattern INLINE_SPACE_PATTERN = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("(\\r?\\n\\s*){3,}");
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    // 正文区域优先级：article > main > body，减少导航/页脚噪声
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("(?is)<article[^>]*>(.*?)</article>");
    private static final Pattern MAIN_PATTERN = Pattern.compile("(?is)<main[^>]*>(.*?)</main>");
    private static final Pattern BODY_PATTERN = Pattern.compile("(?is)<body[^>]*>(.*)</body>");
    // 从 Content-Type 头或 HTML meta 中提取编码
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset\\s*=\\s*[\"']?([\\w-]+)");
    // meta description/keywords（SPA 空壳页的内容兜底）
    private static final Pattern META_DESC_PATTERN = Pattern.compile("(?is)<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']+)[\"']|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']description[\"']");
    private static final Pattern META_KEYWORDS_PATTERN = Pattern.compile("(?is)<meta[^>]+name=[\"']keywords[\"'][^>]+content=[\"']([^\"']+)[\"']|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']keywords[\"']");

    private final HttpClient httpClient;

    public WebFetchTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        
        this.setName("web_fetch");
        this.setDescription("抓取指定URL的网页内容。用于阅读在线文档、获取网页信息。返回网页标题与正文纯文本（保留段落）。");
        this.setParameters(new Parameter[]{
                new Parameter("url", "要抓取的网页URL", "string", true),
                new Parameter("max_length", "返回内容的最大字符数，默认10000", "number", false)
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            String url = (String) arguments.get("url");
            if (url == null || url.trim().isEmpty()) {
                return "错误：URL不能为空";
            }

            int maxLength = DEFAULT_MAX_LENGTH;
            if (arguments.containsKey("max_length") && arguments.get("max_length") != null) {
                try {
                    maxLength = ((Number) arguments.get("max_length")).intValue();
                    if (maxLength <= 0) {
                        maxLength = DEFAULT_MAX_LENGTH;
                    }
                } catch (Exception e) {
                    // 使用默认值
                }
            }

            logger.info("开始抓取网页: url={}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (compatible; QualiaCode/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            // 按字节读取，再根据 Content-Type/meta 解析编码，避免 GBK 网页乱码
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int statusCode = response.statusCode();
            String contentType = response.headers().firstValue("content-type").orElse("text/html");
            String body = decodeBody(response.body(), contentType);

            logger.info("网页抓取完成: status={}, url={}, contentType={}", statusCode, url, contentType);

            JSONObject result = new JSONObject();
            result.put("url", url);
            result.put("status", statusCode);
            result.put("content_type", contentType);

            if (statusCode >= 200 && statusCode < 300) {
                if (body != null && !body.trim().isEmpty()) {
                    String title = extractTitle(body);
                    if (!title.isEmpty()) {
                        result.put("title", title);
                    }
                    String textContent = extractText(body, contentType);
                    int fullLength = textContent.length();

                    // SPA 空壳页：正文几乎为空时按 description > keywords > title 逐级兜底，保证 content 不为空
                    if (fullLength < 80) {
                        String fallback = extractMetaContent(body, META_DESC_PATTERN);
                        if (fallback.isEmpty()) {
                            String keywords = extractMetaContent(body, META_KEYWORDS_PATTERN);
                            if (!keywords.isEmpty()) {
                                fallback = "关键词: " + keywords;
                            }
                        }
                        if (fallback.isEmpty() && !title.isEmpty()) {
                            fallback = title;
                        }
                        if (!fallback.isEmpty()) {
                            textContent = fallback;
                            fullLength = fallback.length();
                        }
                        result.put("message", "该页面由 JavaScript 动态渲染（SPA），服务端 HTML 不含正文，仅能提取标题与元信息。建议改用搜索工具获取相关内容，或尝试该站点的文档子页/API 地址。");
                    }

                    // 截断到指定长度
                    if (fullLength > maxLength) {
                        textContent = textContent.substring(0, maxLength) + "\n\n[内容已截断，总长度: " + fullLength + " 字符]";
                    }

                    result.put("content", textContent);
                    result.put("original_length", body.length());
                    result.put("extracted_length", fullLength);
                } else {
                    result.put("content", "");
                    result.put("message", "网页内容为空");
                }
            } else {
                result.put("error", true);
                result.put("message", "HTTP错误: " + statusCode);
                if (body != null && !body.trim().isEmpty()) {
                    result.put("details", body.substring(0, Math.min(body.length(), 1000)));
                }
            }

            return result.toJSONString();

        } catch (Exception e) {
            logger.error("网页抓取失败: url={}", arguments.get("url"), e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("error", true);
            errorResult.put("message", e.getMessage());
            return errorResult.toJSONString();
        }
    }

    /**
     * 按 Content-Type/meta 声明的编码解码响应体，默认 UTF-8
     */
    private String decodeBody(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        Charset charset = detectCharset(contentType);
        if (charset == null) {
            // 头里没声明：先用 Latin-1 粗解前 4KB 找 <meta charset>（字节不丢失）
            String head = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.ISO_8859_1);
            charset = detectCharset(head);
        }
        return new String(bytes, charset != null ? charset : StandardCharsets.UTF_8);
    }

    private Charset detectCharset(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = CHARSET_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (Exception e) {
                // 非法编码名，忽略
            }
        }
        return null;
    }

    /**
     * 提取页面标题
     */
    private String extractTitle(String html) {
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            String title = HTML_TAG_PATTERN.matcher(m.group(1)).replaceAll("");
            return INLINE_SPACE_PATTERN.matcher(decodeHtmlEntities(title)).replaceAll(" ").trim();
        }
        return "";
    }

    /**
     * 提取 meta 内容（description/keywords，兼容 name/content 属性顺序颠倒）
     */
    private String extractMetaContent(String html, Pattern pattern) {
        Matcher m = pattern.matcher(html);
        if (m.find()) {
            String content = m.group(1) != null ? m.group(1) : m.group(2);
            return INLINE_SPACE_PATTERN.matcher(decodeHtmlEntities(content)).replaceAll(" ").trim();
        }
        return "";
    }

    /**
     * 从HTML中提取纯文本（保留段落换行）
     */
    private String extractText(String html, String contentType) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // 优先取正文区域，减少导航/侧栏/页脚噪声
        String text = pickContentRegion(html);

        // 移除script、style、noscript及其内容
        text = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll("");

        // 移除HTML注释
        text = text.replaceAll("(?s)<!--.*?-->", "");

        // 块级边界转换行，再移除剩余标签
        text = BR_PATTERN.matcher(text).replaceAll("\n");
        text = BLOCK_CLOSE_PATTERN.matcher(text).replaceAll("\n\n");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");

        // 解码常见的HTML实体
        text = decodeHtmlEntities(text);

        // 行内空白压缩（保留换行），逐行去首尾空格，多空行收敛为一个
        text = INLINE_SPACE_PATTERN.matcher(text).replaceAll(" ");
        text = text.replaceAll("(?m)^ +| +$", "");
        text = BLANK_LINE_PATTERN.matcher(text).replaceAll("\n\n");

        return text.trim();
    }

    /**
     * 正文区域优先级：article > main > body > 全文
     */
    private String pickContentRegion(String html) {
        Matcher m = ARTICLE_PATTERN.matcher(html);
        if (m.find() && m.group(1).length() > 200) {
            return m.group(1);
        }
        m = MAIN_PATTERN.matcher(html);
        if (m.find() && m.group(1).length() > 200) {
            return m.group(1);
        }
        m = BODY_PATTERN.matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        return html;
    }

    /**
     * 解码HTML实体
     */
    private String decodeHtmlEntities(String text) {
        return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&hellip;", "...")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&copy;", "©")
                .replace("&reg;", "®")
                .replace("&trade;", "™");
    }
}
