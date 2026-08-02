package com.lunarlanding.qualia.core.retrieval.splitter.impl;

import com.lunarlanding.qualia.core.retrieval.splitter.TextSplitter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Markdown 标题分割器
 * 根据 Markdown 的标题层级（# ## ### 等）进行逻辑分割。
 * 它不依赖固定的 chunkSize，而是根据文档自身的结构进行拆分。
 */
public class MarkdownHeadingTextSplitter implements TextSplitter {

    private final List<String> headersToSplitOn;
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    /**
     * 默认构造函数：在所有级别的标题处进行分割
     */
    public MarkdownHeadingTextSplitter() {
        this(Arrays.asList("#", "##", "###", "####", "#####", "######"));
    }

    /**
     * 指定要分割的标题级别
     * @param headersToSplitOn 标题前缀列表，如 ["#", "##"]
     */
    public MarkdownHeadingTextSplitter(List<String> headersToSplitOn) {
        this.headersToSplitOn = headersToSplitOn;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        Matcher matcher = HEADER_PATTERN.matcher(text);
        int lastMatchEnd = 0;
        String currentHeaderInfo = "";

        while (matcher.find()) {
            String headerLevel = matcher.group(1);
            String headerContent = matcher.group(2);

            // 检查该级别的标题是否在我们要分割的列表中
            if (headersToSplitOn.contains(headerLevel)) {
                // 将上一个标题到当前标题之间的内容作为一个分块
                if (matcher.start() > lastMatchEnd) {
                    String content = text.substring(lastMatchEnd, matcher.start()).trim();
                    if (!content.isEmpty()) {
                        // 逻辑增强：在内容前面加上当前的标题上下文，防止语义丢失
                        String chunkWithContext = currentHeaderInfo.isEmpty() ? content : currentHeaderInfo + "\n" + content;
                        chunks.add(chunkWithContext);
                    }
                }

                // 更新当前的标题信息，作为下一个分块的上下文
                currentHeaderInfo = headerLevel + " " + headerContent;
                lastMatchEnd = matcher.end();
            }
        }

        // 添加最后剩余的内容
        if (lastMatchEnd < text.length()) {
            String lastContent = text.substring(lastMatchEnd).trim();
            if (!lastContent.isEmpty()) {
                String chunkWithContext = currentHeaderInfo.isEmpty() ? lastContent : currentHeaderInfo + "\n" + lastContent;
                chunks.add(chunkWithContext);
            }
        }

        return chunks;
    }
}
