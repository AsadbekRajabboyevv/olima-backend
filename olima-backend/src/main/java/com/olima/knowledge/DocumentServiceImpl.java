package com.olima.knowledge;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.common.http.OutboundUrlPolicy;
import com.olima.knowledge.config.KnowledgeProperties;
import com.olima.knowledge.dto.DocumentResponse;
import com.olima.knowledge.dto.TextDocumentRequest;
import com.olima.knowledge.enums.DocumentProcessingStatus;
import com.olima.knowledge.ingestion.DocumentQueuedEvent;
import com.olima.knowledge.storage.DocumentStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

  private final DocumentRepository documentRepository;
  private final DocumentChunkRepository documentChunkRepository;
  private final EmbeddingService embeddingService;
  private final DocumentStorage storage;
  private final OutboundUrlPolicy urlPolicy;
  private final ApplicationEventPublisher events;
  private final KnowledgeProperties properties;
  private final KnowledgeLookup lookup;

  @Override
  @Transactional(readOnly = true)
  public List<DocumentResponse> findDocuments(UUID knowledgeBaseId) {
    lookup.requireKnowledgeBase(knowledgeBaseId);
    List<DocumentEntity> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId);
    if (docs.isEmpty()) return List.of();
    List<UUID> docIds = docs.stream().map(DocumentEntity::getId).toList();
    Map<UUID, long[]> counts = new HashMap<>();
    if (embeddingService.isVectorStoreAvailable()) {
      for (Object[] r : documentChunkRepository.countByDocument(docIds)) {
        counts.put((UUID) r[0], new long[] {((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
      }
    } else {
      for (Object[] r : documentChunkRepository.countChunksByDocument(docIds)) {
        counts.put((UUID) r[0], new long[] {((Number) r[1]).longValue(), 0});
      }
    }
    return docs.stream().map(d -> {
      long[] c = counts.getOrDefault(d.getId(), new long[] {0, 0});
      return DocumentResponse.of(d, c[0], c[1]);
    }).toList();
  }

  @Override
  @Transactional
  public DocumentResponse uploadDocument(UUID knowledgeBaseId, MultipartFile file, String title) {
    KnowledgeBaseEntity kb = lookup.requireKnowledgeBase(knowledgeBaseId);
    if (file == null || file.isEmpty()) {
      IllegalArgumentException e = new IllegalArgumentException("Uploaded file is empty");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    String orig = file.getOriginalFilename();
    String fileName = orig != null && !orig.isBlank() ? orig : "document";
    String extension = extension(fileName);
    List<String> allowed = properties.ingestion().allowedExtensions();
    if (!allowed.isEmpty() && (extension == null || !allowed.contains(extension))) {
      IllegalArgumentException e =
          new IllegalArgumentException("File type is not supported. Allowed: " + String.join(", ", allowed));
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw new UncheckedIOException("Failed to read uploaded file", e);
    }
    DocumentEntity doc = DocumentEntity.builder()
        .knowledgeBaseId(knowledgeBaseId)
        .title(resolveTitle(title, fileName, kb))
        .fileName(fileName)
        .fileType(file.getContentType())
        .fileSize(file.getSize()).build();
    return enqueueStored(doc, content, extension);
  }

  @Override
  @Transactional
  public DocumentResponse uploadDocumentFromUrl(UUID knowledgeBaseId, String url, String title) {
    lookup.requireKnowledgeBase(knowledgeBaseId);
    String trimmedUrl = urlPolicy.requireAllowed(url).toString();
    DocumentEntity doc = DocumentEntity.builder()
        .knowledgeBaseId(knowledgeBaseId)
        .title(title != null && !title.isBlank() ? title.trim() : trimmedUrl)
        .sourceUrl(trimmedUrl)
        .fileType("text/html")
        .status(DocumentProcessingStatus.PENDING).build();
    return enqueue(documentRepository.save(doc));
  }

  @Override
  @Transactional
  public DocumentResponse createTextDocument(UUID knowledgeBaseId, TextDocumentRequest request) {
    lookup.requireKnowledgeBase(knowledgeBaseId);
    if (request.content().length() > properties.ingestion().maxExtractedChars()) {
      IllegalArgumentException e = new IllegalArgumentException(
          "Text is too long (max " + properties.ingestion().maxExtractedChars() + " characters)");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    byte[] content = request.content().getBytes(StandardCharsets.UTF_8);
    DocumentEntity doc = DocumentEntity.builder()
        .knowledgeBaseId(knowledgeBaseId).title(request.title().trim())
        .fileName(request.title().trim() + ".txt").fileType("text/plain; charset=UTF-8")
        .fileSize((long) content.length).build();
    return enqueueStored(doc, content, "txt");
  }

  @Override
  @Transactional
  public void deleteDocument(UUID documentId) {
    DocumentEntity document = documentRepository.findById(documentId).orElse(null);
    if (document == null) {
      log.debug("Document {} already deleted or not found, skipping", documentId);
      return;
    }
    String storageKey = document.getStorageKey();
    try {
      documentRepository.delete(document);
      documentRepository.flush();
    } catch (OptimisticLockingFailureException e) {
      log.warn("Document {} was deleted concurrently: {}", documentId, e.getMessage());
      return;
    }
    if (storageKey != null) {
      afterCommit(() -> storage.delete(storageKey));
    }
  }

  @Override
  @Transactional
  public DocumentResponse reindexDocument(UUID documentId) {
    DocumentEntity doc = lookup.requireDocument(documentId);
    if (doc.getStatus() == DocumentProcessingStatus.PROCESSING) {
      BusinessException e = new BusinessException(ErrorCode.INVALID_STATE, "Document is being processed");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (doc.getStorageKey() == null && doc.getSourceUrl() == null) {
      BusinessException e = new BusinessException(
          ErrorCode.INVALID_STATE, "Original file of this document was not kept; upload it again");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    doc.setStatus(DocumentProcessingStatus.PENDING);
    doc.setAttempts(0);
    doc.setErrorMessage(null);
    return enqueue(documentRepository.save(doc));
  }

  private DocumentResponse enqueueStored(DocumentEntity doc, byte[] content, String extension) {
    String hash = sha256(content);
    if (properties.ingestion().rejectDuplicates()
        && documentRepository.existsByKnowledgeBaseIdAndContentHash(doc.getKnowledgeBaseId(), hash)) {
      BusinessException e = new BusinessException(
          ErrorCode.DUPLICATE_DOCUMENT, "This file is already in the knowledge base");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    String key = storage.save(content, extension);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) {
          if (status != STATUS_COMMITTED) storage.delete(key);
        }
      });
    }
    doc.setStorageKey(key);
    doc.setContentHash(hash);
    doc.setStatus(DocumentProcessingStatus.PENDING);
    return enqueue(documentRepository.save(doc));
  }

  private DocumentResponse enqueue(DocumentEntity saved) {
    events.publishEvent(new DocumentQueuedEvent(saved.getId()));
    return DocumentResponse.of(saved, 0, 0);
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { action.run(); }
      });
    } else {
      action.run();
    }
  }

  String resolveTitle(String title, String fileName, KnowledgeBaseEntity kb) {
    if (title != null && !title.isBlank()) return title.trim();
    String defaultTitle =
        kb.getName() != null && !kb.getName().isBlank() ? kb.getName() + " hujjati" : "Rasmiy hujjat";
    String cleaned =
        stripExtension(fileName).replaceAll("^\\d{8,}[_\\-\\s]*", "").replace('_', ' ').replace('-', ' ').trim();
    String lower = cleaned.toLowerCase(Locale.ROOT);
    if (lower.isBlank() || List.of("document", "doc", "file", "fayl", "hujjat", "data").contains(lower)) {
      return defaultTitle;
    }
    return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
  }

  static String stripExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  static String extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : null;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
