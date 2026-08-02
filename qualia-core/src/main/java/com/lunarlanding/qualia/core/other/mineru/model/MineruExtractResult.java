package com.lunarlanding.qualia.core.other.mineru.model;

public record MineruExtractResult(String fileName,
                                  String dataId,
                                  String state,
                                  String fullZipUrl,
                                  String errorMessage,
                                  Integer extractedPages,
                                  Integer totalPages) {
}
