package com.olima.knowledge.ingestion;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.knowledge.DocumentChunkEntity;
import com.olima.knowledge.DocumentChunkRepository;
import com.olima.knowledge.DocumentEntity;
import com.olima.knowledge.DocumentParserService;
import com.olima.knowledge.DocumentRepository;
import com.olima.knowledge.KnowledgeBaseEntity;
import com.olima.knowledge.KnowledgeBaseRepository;
import com.olima.knowledge.config.KnowledgeProperties;
import com.olima.knowledge.dto.Fetched;
import com.olima.knowledge.enums.DocumentProcessingStatus;
import com.olima.knowledge.enums.DocumentStatus;
import com.olima.knowledge.storage.DocumentStorage;
import com.olima.knowledge.util.TextChunker;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class DocumentIngestionProcessor {

  private static final int MAX_ERROR_LENGTH = 1000;

  private final DocumentRepository documentRepository;
  private final DocumentChunkRepository chunkRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final DocumentStorage storage;
  private final DocumentParserService parser;
  private final TextChunker chunker;
  private final ChunkEmbeddingIndexer indexer;
  private final TransactionTemplate tx;
  private final MeterRegistry meterRegistry;
  private final int maxAttempts;

  public DocumentIngestionProcessor(
      DocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      KnowledgeBaseRepository knowledgeBaseRepository,
      DocumentStorage storage,
      DocumentParserService parser,
      TextChunker chunker,
      ChunkEmbeddingIndexer indexer,
      TransactionTemplate tx,
      MeterRegistry meterRegistry,
      KnowledgeProperties properties) {
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.storage = storage;
    this.parser = parser;
    this.chunker = chunker;
    this.indexer = indexer;
    this.tx = tx;
    this.meterRegistry = meterRegistry;
    this.maxAttempts = properties.ingestion().worker().maxAttempts();
  }

  public void process(UUID documentId) {
    String prevDocId = MDC.get("documentId");
    String prevOrgId = MDC.get("orgId");
    MDC.put("documentId", documentId.toString());
    try {
      DocumentEntity document = documentRepository.findById(documentId).orElse(null);
      if (document == null || document.getStatus() != DocumentProcessingStatus.PROCESSING) {
        return;
      }
      KnowledgeBaseEntity kb =
          knowledgeBaseRepository.findById(document.getKnowledgeBaseId()).orElse(null);
      if (kb == null) {
        markFailed(document, "Knowledge base no longer exists");
        return;
      }
      MDC.put("orgId", kb.getOrganizationId().toString());

      long start = System.nanoTime();
      try {
        String text = extract(document);
        List<String> pieces = chunker.chunk(text);
        String citation =
            document.getSourceUrl() != null && !document.getSourceUrl().isBlank()
                ? document.getSourceUrl()
                : document.getTitle();

        List<DocumentChunkEntity> saved =
            tx.execute(
                status -> {
                  chunkRepository.deleteByDocumentId(document.getId());
                  List<DocumentChunkEntity> chunks = new ArrayList<>(pieces.size());
                  for (String piece : pieces) {
                    chunks.add(
                        DocumentChunkEntity.builder()
                            .documentId(document.getId())
                            .organizationId(kb.getOrganizationId())
                            .content(piece)
                            .status(DocumentStatus.ACTIVE)
                            .version(1)
                            .sourceUrl(citation)
                            .build());
                  }
                  List<DocumentChunkEntity> result = chunkRepository.saveAll(chunks);
                  document.setContent(text);
                  document.setStatus(DocumentProcessingStatus.COMPLETED);
                  document.setErrorMessage(null);
                  document.setProcessingStartedAt(null);
                  documentRepository.save(document);
                  return result;
                });

        ChunkEmbeddingResult embedded = indexer.index(saved);
        meterRegistry.counter("olima.documents.ingested", "result", "success").increment();
        log.info(
            "Ingested document {} ('{}'): {} chunk(s), {} embedded, {} failed in {} ms",
            document.getId(),
            document.getTitle(),
            saved.size(),
            embedded.processed(),
            embedded.failed(),
            (System.nanoTime() - start) / 1_000_000);
      } catch (Exception e) {
        handleFailure(document, e);
      }
    } finally {
      if (prevDocId != null) {
        MDC.put("documentId", prevDocId);
      } else {
        MDC.remove("documentId");
      }
      if (prevOrgId != null) {
        MDC.put("orgId", prevOrgId);
      } else {
        MDC.remove("orgId");
      }
    }
  }

  private String extract(DocumentEntity document) {
    if (document.getStorageKey() != null) {
      return parser.extractText(
          storage.read(document.getStorageKey()), document.getFileName(), document.getFileType());
    }
    if (document.getSourceUrl() != null) {
      Fetched fetched = parser.fetch(document.getSourceUrl());
      return parser.extractText(fetched.content(), fetched.finalUrl(), fetched.contentType());
    }
    throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "Document has no source");
  }

  private void handleFailure(DocumentEntity document, Exception e) {
    boolean permanent =
        e instanceof BusinessException be && be.code() == ErrorCode.DOCUMENT_PARSE_FAILED;
    if (permanent || document.getAttempts() >= maxAttempts) {
      log.warn(
          "Ingestion of document {} failed permanently (attempt {}): {}",
          document.getId(),
          document.getAttempts(),
          e.getMessage());
      markFailed(document, e.getMessage());
      return;
    }
    log.warn(
        "Ingestion of document {} failed (attempt {}/{}), will retry: {}",
        document.getId(),
        document.getAttempts(),
        maxAttempts,
        e.getMessage());
    document.setStatus(DocumentProcessingStatus.PENDING);
    document.setErrorMessage(truncate(e.getMessage()));
    document.setProcessingStartedAt(null);
    documentRepository.save(document);
  }

  private void markFailed(DocumentEntity document, String message) {
    document.setStatus(DocumentProcessingStatus.FAILED);
    document.setErrorMessage(truncate(message));
    document.setProcessingStartedAt(null);
    documentRepository.save(document);
    meterRegistry.counter("olima.documents.ingested", "result", "failed").increment();
  }

  private static String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
  }
}
