package com.olima.knowledge.ingestion;

import com.olima.knowledge.DocumentChunkEntity;
import com.olima.knowledge.DocumentChunkRepository;
import com.olima.knowledge.EmbeddingService;
import com.olima.knowledge.config.KnowledgeProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChunkEmbeddingIndexer {

  private final EmbeddingService embeddingService;
  private final DocumentChunkRepository chunkRepository;
  private final int batchSize;

  public ChunkEmbeddingIndexer(
      EmbeddingService embeddingService,
      DocumentChunkRepository chunkRepository,
      KnowledgeProperties properties) {
    this.embeddingService = embeddingService;
    this.chunkRepository = chunkRepository;
    this.batchSize = properties.embedding().batchSize();
  }

  public ChunkEmbeddingResult index(List<DocumentChunkEntity> chunks) {
    if (!embeddingService.isEnabled() || chunks.isEmpty()) {
      return new ChunkEmbeddingResult(0, 0);
    }
    int processed = 0;
    int failed = 0;
    for (int from = 0; from < chunks.size(); from += batchSize) {
      List<DocumentChunkEntity> batch =
          chunks.subList(from, Math.min(from + batchSize, chunks.size()));
      try {
        List<float[]> vectors =
            embeddingService.embedAll(batch.stream().map(DocumentChunkEntity::getContent).toList());
        for (int i = 0; i < batch.size(); i++) {
          chunkRepository.updateEmbedding(
              batch.get(i).getId(), EmbeddingService.toPgVector(vectors.get(i)));
          processed++;
        }
      } catch (Exception e) {
        failed += batch.size();
        log.warn("Embedding batch of {} chunk(s) failed: {}", batch.size(), e.getMessage());
      }
    }
    return new ChunkEmbeddingResult(processed, failed);
  }
}
