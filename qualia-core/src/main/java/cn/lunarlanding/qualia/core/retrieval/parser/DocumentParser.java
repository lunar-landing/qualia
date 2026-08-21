package cn.lunarlanding.qualia.core.retrieval.parser;

import java.util.List;

public interface DocumentParser {

    List<Document> parse(String sourcePath);

    boolean supports(String fileType);

}
