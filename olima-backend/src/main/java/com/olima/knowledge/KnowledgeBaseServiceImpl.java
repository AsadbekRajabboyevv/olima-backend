package com.olima.knowledge;

import com.olima.knowledge.dto.KnowledgeBaseRequest;
import com.olima.knowledge.dto.KnowledgeBaseResponse;
import com.olima.knowledge.storage.DocumentStorage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final DocumentRepository documentRepository;
  private final DocumentStorage storage;
  private final KnowledgeLookup lookup;

  @Override
  @Transactional(readOnly = true)
  public Page<KnowledgeBaseResponse> findKnowledgeBases(UUID orgId, Pageable pageable) {
    return knowledgeBaseRepository
        .findByOrganizationId(orgId, pageable)
        .map(KnowledgeBaseResponse::of);
  }

  @Override
  @Transactional(readOnly = true)
  public KnowledgeBaseResponse findKnowledgeBase(UUID id) {
    return KnowledgeBaseResponse.of(lookup.requireKnowledgeBase(id));
  }

  @Override
  @Transactional
  public KnowledgeBaseResponse createKnowledgeBase(UUID orgId, KnowledgeBaseRequest request) {
    KnowledgeBaseEntity kb =
        KnowledgeBaseEntity.builder()
            .organizationId(orgId)
            .name(request.name().trim())
            .description(trimToNull(request.description()))
            .build();
    return KnowledgeBaseResponse.of(knowledgeBaseRepository.save(kb));
  }

  @Override
  @Transactional
  public KnowledgeBaseResponse updateKnowledgeBase(UUID id, KnowledgeBaseRequest request) {
    KnowledgeBaseEntity kb = lookup.requireKnowledgeBase(id);
    kb.setName(request.name().trim());
    kb.setDescription(trimToNull(request.description()));
    return KnowledgeBaseResponse.of(knowledgeBaseRepository.save(kb));
  }

  @Override
  @Transactional
  public void deleteKnowledgeBase(UUID id) {
    KnowledgeBaseEntity kb = lookup.requireKnowledgeBase(id);
    List<String> storageKeys =
        documentRepository.findByKnowledgeBaseId(id).stream()
            .map(DocumentEntity::getStorageKey)
            .toList();

    knowledgeBaseRepository.delete(kb);
    afterCommit(() -> storageKeys.forEach(storage::delete));
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
