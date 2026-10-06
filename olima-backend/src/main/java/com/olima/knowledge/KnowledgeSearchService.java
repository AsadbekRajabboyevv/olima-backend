package com.olima.knowledge;

import com.olima.knowledge.dto.KnowledgeHit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface KnowledgeSearchService {

  List<KnowledgeHit> search(UUID orgId, String query, int maxResults);

  Map<UUID, String> documentTitles(Collection<UUID> documentIds);
}
