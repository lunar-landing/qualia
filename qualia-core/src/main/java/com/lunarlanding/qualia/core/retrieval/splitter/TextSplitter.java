package com.lunarlanding.qualia.core.retrieval.splitter;

import java.util.List;

public interface TextSplitter {
    List<String> split(String text);
}
