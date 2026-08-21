package cn.lunarlanding.qualia.core.retrieval.parser;

import java.util.HashMap;
import java.util.Map;

// 文档数据结构
public class Document {
    private String content;
    private Map<String, Object> metadata;

    public Document(String content) {
        this(content, new HashMap<>());
    }

    public Document(String content, Map<String, Object> metadata) {
        this.content = content;
        this.metadata = metadata;
    }

    // getter和setter
    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
}
