package cn.lunarlanding.qualia.core.retrieval.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 文本文件解析器
public class TextDocumentParser implements DocumentParser {

    @Override
    public List<Document> parse(String sourcePath) {
        try {
            String content = Files.readString(Paths.get(sourcePath));
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", sourcePath);
            metadata.put("type", "txt");
            return Arrays.asList(new Document(content, metadata));
        } catch (IOException e) {
            throw new RuntimeException("文本文件解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType);
    }
}
