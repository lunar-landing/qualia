package cn.lunarlanding.qualia.core.other.ragflow.model;

public record RagflowDocumentInfo(String id,
                                  String name,
                                  String type,
                                  String chunkMethod,
                                  String run,
                                  String status,
                                  Double progress,
                                  String message) {
}
