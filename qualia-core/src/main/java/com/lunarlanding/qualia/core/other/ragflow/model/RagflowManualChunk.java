package com.lunarlanding.qualia.core.other.ragflow.model;

import java.util.ArrayList;
import java.util.List;

public class RagflowManualChunk {

    private String content;
    private List<String> importantKeywords = new ArrayList<>();
    private String title;
    private String effectiveDate;
    private String sourceFile;
    private Integer pageNumber;
    private Integer chunkIndex;
    private String fileName;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImportantKeywords() {
        return importantKeywords;
    }

    public void setImportantKeywords(List<String> importantKeywords) {
        this.importantKeywords = importantKeywords == null ? new ArrayList<>() : importantKeywords;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
