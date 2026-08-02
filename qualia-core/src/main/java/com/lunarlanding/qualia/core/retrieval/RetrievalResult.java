package com.lunarlanding.qualia.core.retrieval;

import java.util.Map;

public class RetrievalResult {
    private String content;
    private double score;
    private Map<String, Object> metadata;

    public RetrievalResult(String content, double score, Map<String, Object> metadata) {
        this.content = content;
        this.score = score;
        this.metadata = metadata;
    }

    // getter方法
    public String getContent() { return content; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public Map<String, Object> getMetadata() { return metadata; }
}
