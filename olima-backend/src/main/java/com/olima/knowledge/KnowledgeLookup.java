package com.olima.knowledge;

import com.olima.common.error.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class KnowledgeLookup {

  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final DocumentRepository documentRepository;

  KnowledgeBaseEntity requireKnowledgeBase(UUID id) {
    return knowledgeBaseRepository
        .findById(id)
        .orElseThrow(
            () -> {
              NotFoundException e = new NotFoundException("get.knowledge_base_not_found");
              log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
              return e;
            });
  }

  KnowledgeBaseEntity requireKnowledgeBase(UUID id, UUID orgId) {
    KnowledgeBaseEntity kb = requireKnowledgeBase(id);
    if (orgId != null && !orgId.equals(kb.getOrganizationId())) {
      NotFoundException e = new NotFoundException("get.knowledge_base_not_found");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    return kb;
  }

  DocumentEntity requireDocument(UUID id) {
    return documentRepository
        .findById(id)
        .orElseThrow(
            () -> {
              NotFoundException e = new NotFoundException("get.document_not_found");
              log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
              return e;
            });
  }

  DocumentEntity requireDocument(UUID id, UUID orgId) {
    DocumentEntity doc = requireDocument(id);
    if (orgId != null) {
      UUID docOrgId = documentRepository.findOrganizationIdById(id).orElse(null);
      if (!orgId.equals(docOrgId)) {
        NotFoundException e = new NotFoundException("get.document_not_found");
        log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        throw e;
      }
    }
    return doc;
  }
}
