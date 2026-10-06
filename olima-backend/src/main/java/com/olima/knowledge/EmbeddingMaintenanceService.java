package com.olima.knowledge;

import com.olima.knowledge.dto.EmbeddingBackfillResponse;
import com.olima.knowledge.dto.EmbeddingStatusResponse;
import java.util.UUID;

public interface EmbeddingMaintenanceService {

  EmbeddingBackfillResponse backfillEmbeddings(UUID organizationId, int limit);

  EmbeddingStatusResponse embeddingStatus(UUID organizationId);
}
