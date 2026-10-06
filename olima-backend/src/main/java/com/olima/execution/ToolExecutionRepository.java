package com.olima.execution;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolExecutionRepository extends JpaRepository<ToolExecutionEntity, UUID> {

  Page<ToolExecutionEntity> findByOrganizationId(UUID orgId, Pageable pageable);

  List<ToolExecutionEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
