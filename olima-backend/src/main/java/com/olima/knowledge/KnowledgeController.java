package com.olima.knowledge;

import com.olima.common.web.Paging;
import com.olima.knowledge.dto.DocumentResponse;
import com.olima.knowledge.dto.EmbeddingBackfillResponse;
import com.olima.knowledge.dto.EmbeddingStatusResponse;
import com.olima.knowledge.dto.KnowledgeBaseRequest;
import com.olima.knowledge.dto.KnowledgeBaseResponse;
import com.olima.knowledge.dto.TextDocumentRequest;
import com.olima.knowledge.dto.UrlIngestRequest;
import com.olima.security.tenant.TenantAccess;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController implements KnowledgeApi {

  private final KnowledgeBaseService knowledgeBaseService;
  private final DocumentService documentService;
  private final EmbeddingMaintenanceService embeddingMaintenanceService;
  private final TenantAccess tenant;
  private final Paging paging;

  @Override
  @GetMapping("/bases")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<List<KnowledgeBaseResponse>> findKnowledgeBases(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return Paging.ok(
        knowledgeBaseService.findKnowledgeBases(
            organizationId, paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
  }

  @Override
  @PostMapping("/bases")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  public ResponseEntity<KnowledgeBaseResponse> createKnowledgeBase(
      @RequestBody @Valid KnowledgeBaseRequest request) {
    UUID orgId = tenant.effectiveOrganization(request.organizationId(), true);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(knowledgeBaseService.createKnowledgeBase(orgId, request));
  }

  @Override
  @GetMapping("/bases/{id}")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  public ResponseEntity<KnowledgeBaseResponse> findKnowledgeBase(@PathVariable UUID id) {
    return ResponseEntity.ok(knowledgeBaseService.findKnowledgeBase(id));
  }

  @Override
  @PutMapping("/bases/{id}")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  public ResponseEntity<KnowledgeBaseResponse> updateKnowledgeBase(
      @PathVariable UUID id, @RequestBody @Valid KnowledgeBaseRequest request) {
    return ResponseEntity.ok(knowledgeBaseService.updateKnowledgeBase(id, request));
  }

  @Override
  @DeleteMapping("/bases/{id}")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  public ResponseEntity<Void> deleteKnowledgeBase(@PathVariable UUID id) {
    knowledgeBaseService.deleteKnowledgeBase(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/bases/{id}/documents")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  public ResponseEntity<List<DocumentResponse>> findDocuments(@PathVariable UUID id) {
    return ResponseEntity.ok(documentService.findDocuments(id));
  }

  @Override
  @PostMapping("/bases/{id}/documents")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  public ResponseEntity<DocumentResponse> createTextDocument(
      @PathVariable UUID id, @RequestBody @Valid TextDocumentRequest request) {
    return ResponseEntity.accepted().body(documentService.createTextDocument(id, request));
  }

  @Override
  @PostMapping("/bases/{id}/documents/upload")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  public ResponseEntity<DocumentResponse> uploadDocument(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title) {
    return ResponseEntity.accepted().body(documentService.uploadDocument(id, file, title));
  }

  @Override
  @PostMapping("/bases/{id}/documents/url")
  @PreAuthorize("@tenant.canAccess('KNOWLEDGE_BASE', #id)")
  public ResponseEntity<DocumentResponse> uploadDocumentFromUrl(
      @PathVariable UUID id, @RequestBody @Valid UrlIngestRequest request) {
    return ResponseEntity.accepted()
        .body(documentService.uploadDocumentFromUrl(id, request.url(), request.title()));
  }

  @Override
  @DeleteMapping("/documents/{id}")
  @PreAuthorize("@tenant.canAccess('DOCUMENT', #id)")
  public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
    documentService.deleteDocument(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PostMapping("/documents/{id}/reindex")
  @PreAuthorize("@tenant.canAccess('DOCUMENT', #id)")
  public ResponseEntity<DocumentResponse> reindexDocument(@PathVariable UUID id) {
    return ResponseEntity.accepted().body(documentService.reindexDocument(id));
  }

  @Override
  @PostMapping("/chunks/backfill-embeddings")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  public ResponseEntity<EmbeddingBackfillResponse> backfillEmbeddings(
      @RequestParam(required = false) UUID organizationId,
      @RequestParam(defaultValue = "100") int limit) {
    return ResponseEntity.ok(
        embeddingMaintenanceService.backfillEmbeddings(
            tenant.effectiveOrganization(organizationId, false), limit));
  }

  @Override
  @GetMapping("/embeddings/status")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  public ResponseEntity<EmbeddingStatusResponse> embeddingStatus(
      @RequestParam(required = false) UUID organizationId) {
    return ResponseEntity.ok(
        embeddingMaintenanceService.embeddingStatus(
            tenant.effectiveOrganization(organizationId, false)));
  }
}
