package cn.lunarlanding.qualia.core.parser;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 文件解析器。
 *
 * <p>解析器职责是忠实转换：保留 Markdown 原文（含代码块、链接、表格），
 * 不做标记清洗——Markdown 本身是纯文本，模型可直接理解，
 * 清洗反而会丢失信息（如代码块内容）。</p>
 *
 * <p>额外提取首个一级/二级标题写入 {@code metadata.title}。</p>
 */
public class MarkdownDocumentParser implements DocumentParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)");
    private static final List<String> EXTENSIONS = List.of(".md", ".markdown");

    @Override
    public boolean supports(String fileName) {
        return DocumentParser.hasExtension(fileName, EXTENSIONS.toArray(new String[0]));
    }

    @Override
    public List<Document> parse(byte[] data) {
        String content = DocumentParser.decodeText(data);
        Map<String, Object> metadata = Map.of(
                "type", "markdown",
                "title", extractTitle(content));
        return List.of(new Document(content, metadata));
    }

    /** 提取首个一级/二级标题作为文档标题，缺省为 "Markdown Document" */
    private String extractTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "Markdown Document";
        }
        for (String line : content.split("\\r?\\n")) {
            Matcher matcher = HEADER_PATTERN.matcher(line.trim());
            if (matcher.find() && matcher.group(1).length() <= 2) {
                return matcher.group(2).trim();
            }
        }
        return "Markdown Document";
    }
}
