package com.olima.knowledge;

import com.olima.knowledge.dto.KnowledgeBaseRequest;
import com.olima.knowledge.dto.KnowledgeBaseResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface KnowledgeBaseService {

  Page<KnowledgeBaseResponse> findKnowledgeBases(UUID orgId, Pageable pageable);

  KnowledgeBaseResponse findKnowledgeBase(UUID id);

  KnowledgeBaseResponse createKnowledgeBase(UUID orgId, KnowledgeBaseRequest request);

  KnowledgeBaseResponse updateKnowledgeBase(UUID id, KnowledgeBaseRequest request);

  void deleteKnowledgeBase(UUID id);
}
