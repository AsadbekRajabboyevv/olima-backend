package com.olima.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, UUID> {
  List<KnowledgeBaseEntity> findByOrganizationId(UUID orgId);

  Page<KnowledgeBaseEntity> findByOrganizationId(UUID orgId, Pageable pageable);

  @Query("select k.organizationId from KnowledgeBaseEntity k where k.id = :id")
  Optional<UUID> findOrganizationIdById(@Param("id") UUID id);
}
