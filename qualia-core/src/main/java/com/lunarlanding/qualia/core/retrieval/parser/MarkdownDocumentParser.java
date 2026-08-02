package com.lunarlanding.qualia.core.retrieval.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Markdown文档解析器，用于解析Markdown格式的文档内容
 */
public class MarkdownDocumentParser implements DocumentParser {
    private static final Pattern MARKDOWN_HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)");
    private static final Pattern MARKDOWN_CODE_BLOCK_PATTERN = Pattern.compile("`{3}.*?`{3}", Pattern.DOTALL);
    private static final Pattern MARKDOWN_INLINE_CODE_PATTERN = Pattern.compile("`.*?`");
    private static final Pattern MARKDOWN_BOLD_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*|__(.*?)__");
    private static final Pattern MARKDOWN_ITALIC_PATTERN = Pattern.compile("\\*(.*?)\\*|_(.*?)_");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");

    @Override
    public List<Document> parse(String sourcePath) {
        if (sourcePath == null) {
            throw new IllegalArgumentException("源路径不能为空");
        }

        try {
            // 读取Markdown文件内容
            String markdownContent = Files.readString(Paths.get(sourcePath));

            // 提取文档标题（从第一级或第二级标题）
            String title = extractTitle(markdownContent);

            // 清理Markdown标记，提取纯文本内容
            String textContent = cleanMarkdown(markdownContent);

            // 创建文档元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", sourcePath);
            metadata.put("type", "markdown");
            metadata.put("title", title);
            metadata.put("timestamp", System.currentTimeMillis());

            return Arrays.asList(new Document(textContent, metadata));
        } catch (IOException e) {
            throw new RuntimeException("Markdown文件解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType) ||
               "markdown".equalsIgnoreCase(fileType) ||
               (fileType.toLowerCase().endsWith(".md") || fileType.toLowerCase().endsWith(".markdown"));
    }

    /**
     * 从Markdown内容中提取标题
     */
    private String extractTitle(String markdownContent) {
        if (markdownContent == null || markdownContent.isEmpty()) {
            return "Unknown Title";
        }

        String[] lines = markdownContent.split("\\r?\\n");
        for (String line : lines) {
            var matcher = MARKDOWN_HEADER_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                // 优先返回一级和二级标题
                String headerLevel = matcher.group(1);
                if (headerLevel.length() <= 2) {
                    return matcher.group(2).trim();
                }
            }
        }

        // 如果没有找到合适的标题，返回文件名
        return "Markdown Document";
    }

    /**
     * 清理Markdown标记，提取纯文本内容
     */
    private String cleanMarkdown(String markdownContent) {
        if (markdownContent == null) {
            return "";
        }

        String cleaned = markdownContent;

        // 移除代码块
        cleaned = MARKDOWN_CODE_BLOCK_PATTERN.matcher(cleaned).replaceAll(" ");

        // 移除行内代码
        cleaned = MARKDOWN_INLINE_CODE_PATTERN.matcher(cleaned).replaceAll(" ");

        // 移除粗体标记
        cleaned = MARKDOWN_BOLD_PATTERN.matcher(cleaned).replaceAll("$1$2");

        // 移除斜体标记
        cleaned = MARKDOWN_ITALIC_PATTERN.matcher(cleaned).replaceAll("$1$2");

        // 移除链接标记，保留链接文本
        cleaned = MARKDOWN_LINK_PATTERN.matcher(cleaned).replaceAll("$1");

        // 移除图片标记，保留替代文本
        cleaned = MARKDOWN_IMAGE_PATTERN.matcher(cleaned).replaceAll("$1");

        // 移除标题标记
        cleaned = MARKDOWN_HEADER_PATTERN.matcher(cleaned).replaceAll("$2");

        // 移除强调标记（如*、_、-、+、数字列表点等）
        cleaned = cleaned.replaceAll("^\\s*[\\*\\+\\-]\\s+", ""); // 无序列表
        cleaned = cleaned.replaceAll("^\\s*\\d+\\.\\s+", "");     // 有序列表
        cleaned = cleaned.replaceAll("^\\s*>\\s*", "");           // 引用
        cleaned = cleaned.replaceAll("\\s*\\|\\s*", " ");         // 表格分隔符
        cleaned = cleaned.replaceAll("---+", " ");                // 分隔线

        // 替换多个连续空行为单个换行
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        // 清理多余的空白字符
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }
}
