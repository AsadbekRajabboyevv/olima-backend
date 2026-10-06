package com.olima.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olima.knowledge.config.KnowledgeProperties;
import com.olima.knowledge.dto.KnowledgeHit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeSearchServiceTest {

  private DocumentRepository documentRepository;
  private DocumentChunkRepository documentChunkRepository;
  private EmbeddingService embeddingService;
  private KnowledgeProperties properties;
  private KnowledgeSearchServiceImpl service;

  @BeforeEach
  void setUp() {
    documentRepository = mock(DocumentRepository.class);
    documentChunkRepository = mock(DocumentChunkRepository.class);
    embeddingService = mock(EmbeddingService.class);
    properties =
        new KnowledgeProperties(
            null,
            null,
            null,
            null,
            new KnowledgeProperties.Search(0.25, 60, 3, 3, 500));
    service =
        new KnowledgeSearchServiceImpl(
            documentRepository, documentChunkRepository, embeddingService, properties);
  }

  @Test
  void search_whenQueryBlank_returnsEmptyList() {
    UUID orgId = UUID.randomUUID();

    assertThat(service.search(orgId, null, 5)).isEmpty();
    assertThat(service.search(orgId, "", 5)).isEmpty();
    assertThat(service.search(orgId, "   ", 5)).isEmpty();

    verifyNoInteractions(documentChunkRepository);
  }

  @Test
  void search_hybridFusion_ordersByRrfScoreAndLimitsMaxResults() {
    UUID orgId = UUID.randomUUID();
    String query = "admission rules";

    DocumentChunkEntity chunk1 =
        DocumentChunkEntity.builder().id(UUID.randomUUID()).content("chunk1").build();
    DocumentChunkEntity chunk2 =
        DocumentChunkEntity.builder().id(UUID.randomUUID()).content("chunk2").build();
    DocumentChunkEntity chunk3 =
        DocumentChunkEntity.builder().id(UUID.randomUUID()).content("chunk3").build();

    when(embeddingService.isEnabled()).thenReturn(true);
    when(embeddingService.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f});
    // Vector search returns chunk1 (rank 0), chunk2 (rank 1)
    when(documentChunkRepository.searchByVector(eq(orgId), anyString(), anyDouble(), anyInt()))
        .thenReturn(List.of(chunk1, chunk2));
    // FullText returns chunk2 (rank 0), chunk3 (rank 1)
    when(documentChunkRepository.searchFullText(eq(orgId), eq(query), anyInt(), anyInt()))
        .thenReturn(List.of(chunk2, chunk3));

    // maxResults = 2 -> chunk2 appears in both so highest RRF score; then chunk1 (vector rank 0) vs chunk3 (fulltext rank 1)
    List<KnowledgeHit> results = service.search(orgId, query, 2);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).id()).isEqualTo(chunk2.getId());
    assertThat(results.get(1).id()).isEqualTo(chunk1.getId());
  }

  @Test
  void search_whenVectorSearchFails_fallsBackToFullText() {
    UUID orgId = UUID.randomUUID();
    String query = "tuition fee";

    DocumentChunkEntity chunk =
        DocumentChunkEntity.builder().id(UUID.randomUUID()).content("fee chunk").build();

    when(embeddingService.isEnabled()).thenReturn(true);
    when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("Embedding model down"));
    when(documentChunkRepository.searchFullText(eq(orgId), eq(query), anyInt(), anyInt()))
        .thenReturn(List.of(chunk));

    List<KnowledgeHit> results = service.search(orgId, query, 5);

    assertThat(results).extracting(KnowledgeHit::id).containsExactly(chunk.getId());
  }

  @Test
  void search_whenFullTextFails_fallsBackToVector() {
    UUID orgId = UUID.randomUUID();
    String query = "hostel info";

    DocumentChunkEntity chunk =
        DocumentChunkEntity.builder().id(UUID.randomUUID()).content("hostel chunk").build();

    when(embeddingService.isEnabled()).thenReturn(true);
    when(embeddingService.embed(anyString())).thenReturn(new float[] {0.5f});
    when(documentChunkRepository.searchByVector(eq(orgId), anyString(), anyDouble(), anyInt()))
        .thenReturn(List.of(chunk));
    when(documentChunkRepository.searchFullText(eq(orgId), anyString(), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("Full-text index syntax error"));

    List<KnowledgeHit> results = service.search(orgId, query, 5);

    assertThat(results).extracting(KnowledgeHit::id).containsExactly(chunk.getId());
  }

  @Test
  void documentTitles_whenEmpty_returnsEmptyMap() {
    assertThat(service.documentTitles(List.of())).isEmpty();
    verifyNoInteractions(documentRepository);
  }

  @Test
  void documentTitles_mapsTitlesById() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    DocumentEntity doc1 = DocumentEntity.builder().title("Title 1").build();
    doc1.setId(id1);
    DocumentEntity doc2 = DocumentEntity.builder().title("Title 2").build();
    doc2.setId(id2);

    when(documentRepository.findAllById(List.of(id1, id2))).thenReturn(List.of(doc1, doc2));

    Map<UUID, String> titles = service.documentTitles(List.of(id1, id2));

    assertThat(titles).containsEntry(id1, "Title 1").containsEntry(id2, "Title 2");
  }
}
