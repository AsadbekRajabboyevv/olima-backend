package com.olima.knowledge.dto;

import com.olima.knowledge.DocumentEntity;
import com.olima.knowledge.enums.DocumentProcessingStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID knowledgeBaseId,
    String title,
    String sourceUrl,
    String fileName,
    String fileType,
    Long fileSize,
    DocumentProcessingStatus status,
    String errorMessage,
    Instant createdAt,
    long chunkCount,
    long embeddedChunkCount) {
  public static DocumentResponse of(DocumentEntity d, long chunkCount, long embeddedChunkCount) {
    return new DocumentResponse(
        d.getId(),
        d.getKnowledgeBaseId(),
        d.getTitle(),
        d.getSourceUrl(),
        d.getFileName(),
        d.getFileType(),
        d.getFileSize(),
        d.getStatus(),
        d.getErrorMessage(),
        d.getCreatedAt(),
        chunkCount,
        embeddedChunkCount);
  }
}
