package cn.lunarlanding.qualia.core.other.ragflow.model;

public record RagflowDocumentParsingStatus(String documentId,
                                           String run,
                                           String status,
                                           Double progress,
                                           String message) {
}
