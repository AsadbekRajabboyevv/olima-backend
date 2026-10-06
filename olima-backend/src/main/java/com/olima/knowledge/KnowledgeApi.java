package com.olima.knowledge;

import com.olima.knowledge.dto.DocumentResponse;
import com.olima.knowledge.dto.EmbeddingBackfillResponse;
import com.olima.knowledge.dto.EmbeddingStatusResponse;
import com.olima.knowledge.dto.KnowledgeBaseRequest;
import com.olima.knowledge.dto.KnowledgeBaseResponse;
import com.olima.knowledge.dto.TextDocumentRequest;
import com.olima.knowledge.dto.UrlIngestRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/v1/knowledge")
public interface KnowledgeApi {

  @GetMapping("/bases")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<List<KnowledgeBaseResponse>> findKnowledgeBases(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @PostMapping("/bases")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  ResponseEntity<KnowledgeBaseResponse> createKnowledgeBase(
      @RequestBody @Valid KnowledgeBaseRequest request);

  @GetMapping("/bases/{id}")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  ResponseEntity<KnowledgeBaseResponse> findKnowledgeBase(@PathVariable UUID id);

  @PutMapping("/bases/{id}")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  ResponseEntity<KnowledgeBaseResponse> updateKnowledgeBase(
      @PathVariable UUID id, @RequestBody @Valid KnowledgeBaseRequest request);

  @DeleteMapping("/bases/{id}")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  ResponseEntity<Void> deleteKnowledgeBase(@PathVariable UUID id);

  @GetMapping("/bases/{id}/documents")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  ResponseEntity<List<DocumentResponse>> findDocuments(@PathVariable UUID id);

  @PostMapping("/bases/{id}/documents")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  ResponseEntity<DocumentResponse> createTextDocument(
      @PathVariable UUID id, @RequestBody @Valid TextDocumentRequest request);

  @PostMapping("/bases/{id}/documents/upload")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  ResponseEntity<DocumentResponse> uploadDocument(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title);

  @PostMapping("/bases/{id}/documents/url")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  ResponseEntity<DocumentResponse> uploadDocumentFromUrl(
      @PathVariable UUID id, @RequestBody @Valid UrlIngestRequest request);

  @DeleteMapping("/documents/{id}")
  @PreAuthorize("@tenant.canAccess('DOCUMENT', #id)")
  ResponseEntity<Void> deleteDocument(@PathVariable UUID id);

  @PostMapping("/documents/{id}/reindex")
  @PreAuthorize("@tenant.canAccess('DOCUMENT', #id)")
  ResponseEntity<DocumentResponse> reindexDocument(@PathVariable UUID id);

  @PostMapping("/chunks/backfill-embeddings")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  ResponseEntity<EmbeddingBackfillResponse> backfillEmbeddings(
      @RequestParam(required = false) UUID organizationId,
      @RequestParam(defaultValue = "100") int limit);

  @GetMapping("/embeddings/status")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  ResponseEntity<EmbeddingStatusResponse> embeddingStatus(
      @RequestParam(required = false) UUID organizationId);
}
