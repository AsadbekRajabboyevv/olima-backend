package com.olima.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

  List<DocumentEntity> findByKnowledgeBaseIdOrderByCreatedAtDesc(UUID kbId);

  List<DocumentEntity> findByKnowledgeBaseId(UUID kbId);

  boolean existsByKnowledgeBaseIdAndContentHash(UUID kbId, String contentHash);

  @Query(
      "select k.organizationId from DocumentEntity d, KnowledgeBaseEntity k "
          + "where d.id = :id and k.id = d.knowledgeBaseId")
  Optional<UUID> findOrganizationIdById(@Param("id") UUID id);
}
