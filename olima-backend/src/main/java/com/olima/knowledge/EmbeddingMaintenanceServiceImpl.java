package com.olima.knowledge;

import com.olima.knowledge.config.KnowledgeProperties;
import com.olima.knowledge.dto.EmbeddingBackfillResponse;
import com.olima.knowledge.dto.EmbeddingStatusResponse;
import com.olima.knowledge.ingestion.ChunkEmbeddingIndexer;
import com.olima.knowledge.ingestion.ChunkEmbeddingResult;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingMaintenanceServiceImpl implements EmbeddingMaintenanceService {

  private final DocumentChunkRepository documentChunkRepository;
  private final EmbeddingService embeddingService;
  private final ChunkEmbeddingIndexer indexer;
  private final KnowledgeProperties properties;

  @Override
  public EmbeddingBackfillResponse backfillEmbeddings(UUID organizationId, int limit) {
    if (!embeddingService.isEnabled()) {
      log.warn(
          "Cannot backfill embeddings: model configured={}, vector store available={}",
          embeddingService.isModelConfigured(),
          embeddingService.isVectorStoreAvailable());
      return new EmbeddingBackfillResponse(0, 0, pendingChunks(organizationId), false);
    }
    int safeLimit = Math.clamp(limit, 1, properties.embedding().backfillMaxLimit());
    List<DocumentChunkEntity> unindexed =
        organizationId != null
            ? documentChunkRepository.findChunksWithoutEmbedding(organizationId, safeLimit)
            : documentChunkRepository.findChunksWithoutEmbedding(safeLimit);

    ChunkEmbeddingResult result = indexer.index(unindexed);
    log.info(
        "Backfilled embeddings for {} chunk(s), {} failed (organization: {})",
        result.processed(),
        result.failed(),
        organizationId != null ? organizationId : "ALL");
    return new EmbeddingBackfillResponse(
        result.processed(), result.failed(), pendingChunks(organizationId), true);
  }

  @Override
  public EmbeddingStatusResponse embeddingStatus(UUID organizationId) {
    long[] progress = progress(organizationId);
    return new EmbeddingStatusResponse(
        organizationId,
        embeddingService.isEnabled(),
        embeddingService.isModelConfigured(),
        embeddingService.isVectorStoreAvailable(),
        embeddingService.modelName(),
        progress[0],
        progress[1],
        progress[0] - progress[1]);
  }

  private long pendingChunks(UUID organizationId) {
    long[] progress = progress(organizationId);
    return progress[0] - progress[1];
  }

  private long[] progress(UUID organizationId) {
    if (!embeddingService.isVectorStoreAvailable()) {
      long total =
          organizationId != null
              ? documentChunkRepository.countActive(organizationId)
              : documentChunkRepository.countActiveAll();
      return new long[] {total, 0};
    }
    List<Object[]> rows =
        organizationId != null
            ? documentChunkRepository.countEmbeddingProgress(organizationId)
            : documentChunkRepository.countEmbeddingProgressAll();
    if (rows.isEmpty()) {
      return new long[] {0, 0};
    }
    Object[] row = rows.getFirst();
    return new long[] {((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
  }
}
