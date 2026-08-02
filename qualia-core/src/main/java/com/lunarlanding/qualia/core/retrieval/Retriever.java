package com.lunarlanding.qualia.core.retrieval;

import java.util.List;

public interface Retriever {

    public List<RetrievalResult> retrieve(String query, int topK);

    public List<RetrievalResult> retrieve(List<String> queries, int topK);

}
