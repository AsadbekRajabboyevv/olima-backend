package com.olima.knowledge;

import com.olima.knowledge.config.KnowledgeProperties;
import com.olima.knowledge.dto.KnowledgeHit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

  private final DocumentRepository documentRepository;
  private final DocumentChunkRepository documentChunkRepository;
  private final EmbeddingService embeddingService;
  private final KnowledgeProperties properties;

  @Override
  @Transactional(readOnly = true)
  public List<KnowledgeHit> search(UUID orgId, String query, int maxResults) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    KnowledgeProperties.Search cfg = properties.search();
    String text =
        query.length() > cfg.maxQueryChars() ? query.substring(0, cfg.maxQueryChars()) : query;
    int candidates = maxResults * cfg.candidateMultiplier();

    List<DocumentChunkEntity> vector = List.of();
    if (embeddingService.isEnabled()) {
      try {
        String embedding = EmbeddingService.toPgVector(embeddingService.embed(text));
        vector =
            documentChunkRepository.searchByVector(
                orgId, embedding, 1.0 - cfg.minSimilarity(), candidates);
      } catch (Exception e) {
        log.warn("Vector search failed, using full-text search only: {}", e.getMessage());
      }
    }
    List<DocumentChunkEntity> fullText = List.of();
    try {
      fullText =
          documentChunkRepository.searchFullText(orgId, text, cfg.minTermLength(), candidates);
    } catch (Exception e) {
      log.warn("Full-text search failed: {}", e.getMessage());
    }

    Map<UUID, DocumentChunkEntity> byId = new LinkedHashMap<>();
    Map<UUID, Double> score = new HashMap<>();
    accumulate(vector, cfg.rrfK(), byId, score);
    accumulate(fullText, cfg.rrfK(), byId, score);
    log.debug(
        "Knowledge search: {} vector + {} full-text candidate(s)", vector.size(), fullText.size());

    List<KnowledgeHit> results =
        byId.values().stream()
            .sorted(
                Comparator.comparingDouble((DocumentChunkEntity c) -> score.get(c.getId()))
                    .reversed())
            .limit(maxResults)
            .map(KnowledgeSearchServiceImpl::toHit)
            .toList();
    return results;
  }

  private static KnowledgeHit toHit(DocumentChunkEntity chunk) {
    return new KnowledgeHit(
        chunk.getId(), chunk.getDocumentId(), chunk.getContent(), chunk.getSourceUrl());
  }

  static void accumulate(
      List<DocumentChunkEntity> ranked,
      int k,
      Map<UUID, DocumentChunkEntity> byId,
      Map<UUID, Double> score) {
    for (int i = 0; i < ranked.size(); i++) {
      DocumentChunkEntity chunk = ranked.get(i);
      byId.putIfAbsent(chunk.getId(), chunk);
      score.merge(chunk.getId(), 1.0 / (k + i + 1), Double::sum);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, String> documentTitles(Collection<UUID> documentIds) {
    if (documentIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> titles =
        documentRepository.findAllById(documentIds).stream()
            .filter(d -> d.getTitle() != null)
            .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getTitle));
    return titles;
  }
}
