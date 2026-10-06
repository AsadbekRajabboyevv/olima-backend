package com.olima.knowledge;

import com.olima.common.BaseEntity;
import com.olima.knowledge.enums.DocumentProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity extends BaseEntity {
  private UUID knowledgeBaseId;
  private String title;
  private String content;
  private String sourceUrl;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "file_type")
  private String fileType;

  @Column(name = "file_size")
  private Long fileSize;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private DocumentProcessingStatus status = DocumentProcessingStatus.PENDING;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "storage_key")
  private String storageKey;

  @Column(name = "content_hash")
  private String contentHash;

  @Builder.Default private int attempts = 0;

  @Column(name = "processing_started_at")
  private Instant processingStartedAt;
}
