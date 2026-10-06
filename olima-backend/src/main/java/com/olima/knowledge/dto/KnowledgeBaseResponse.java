package com.olima.knowledge.dto;

import com.olima.knowledge.KnowledgeBaseEntity;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeBaseResponse(
    UUID id,
    UUID organizationId,
    String name,
    String description,
    Instant createdAt,
    Instant updatedAt) {

  public static KnowledgeBaseResponse of(KnowledgeBaseEntity kb) {
    return new KnowledgeBaseResponse(
        kb.getId(),
        kb.getOrganizationId(),
        kb.getName(),
        kb.getDescription(),
        kb.getCreatedAt(),
        kb.getUpdatedAt());
  }
}
