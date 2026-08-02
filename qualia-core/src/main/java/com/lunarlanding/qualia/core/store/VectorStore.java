package com.lunarlanding.qualia.core.store;

import com.lunarlanding.qualia.core.retrieval.parser.Document;
import com.lunarlanding.qualia.core.retrieval.RetrievalResult;

import java.util.List;

public interface VectorStore {

    void addDocument(String filePath);

    void addDocuments(List<Document> documents);

    List<RetrievalResult> similaritySearch(String query, int topK);
}
