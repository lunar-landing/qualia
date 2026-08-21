package cn.lunarlanding.qualia.core.store;

import cn.lunarlanding.qualia.core.retrieval.parser.Document;
import cn.lunarlanding.qualia.core.retrieval.RetrievalResult;

import java.util.List;

public interface VectorStore {

    void addDocument(String filePath);

    void addDocuments(List<Document> documents);

    List<RetrievalResult> similaritySearch(String query, int topK);
}
