package com.olima.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

  @Query(
      value =
          """
          WITH q AS (
            SELECT to_tsquery('simple', string_agg(quote_literal(t.lexeme) || ':*', ' | ')) AS tsq
            FROM unnest(to_tsvector('simple', translate(:text, 'ʻʼ’‘`', '     '))) AS t
            WHERE length(t.lexeme) >= :minLength
          )
          SELECT c.* FROM document_chunks c, q
          WHERE q.tsq IS NOT NULL
            AND c.organization_id = :orgId
            AND c.status = 'ACTIVE'
            AND c.search_vector @@ q.tsq
          ORDER BY ts_rank_cd(c.search_vector, q.tsq) DESC
          LIMIT :maxResults
          """,
      nativeQuery = true)
  List<DocumentChunkEntity> searchFullText(
      @Param("orgId") UUID orgId,
      @Param("text") String text,
      @Param("minLength") int minLength,
      @Param("maxResults") int maxResults);

  @Query(
      value =
          """
          SELECT * FROM document_chunks c
          WHERE c.organization_id = :orgId
            AND c.status = 'ACTIVE'
            AND c.embedding IS NOT NULL
            AND (c.embedding <=> cast(:embedding as vector)) <= :maxDistance
          ORDER BY c.embedding <=> cast(:embedding as vector)
          LIMIT :maxResults
          """,
      nativeQuery = true)
  List<DocumentChunkEntity> searchByVector(
      @Param("orgId") UUID orgId,
      @Param("embedding") String embedding,
      @Param("maxDistance") double maxDistance,
      @Param("maxResults") int maxResults);

  @Modifying
  @Transactional
  @Query(
      value = "UPDATE document_chunks SET embedding = cast(:embedding as vector) WHERE id = :id",
      nativeQuery = true)
  void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

  @Modifying
  @Transactional
  @Query("DELETE FROM DocumentChunkEntity c WHERE c.documentId = :documentId")
  int deleteByDocumentId(@Param("documentId") UUID documentId);

  @Query(
      value =
          "SELECT * FROM document_chunks c WHERE c.status = 'ACTIVE' AND c.embedding IS NULL ORDER"
              + " BY c.created_at LIMIT :limit",
      nativeQuery = true)
  List<DocumentChunkEntity> findChunksWithoutEmbedding(@Param("limit") int limit);

  @Query(
      value =
          """
          SELECT * FROM document_chunks c
          WHERE c.organization_id = :orgId AND c.status = 'ACTIVE' AND c.embedding IS NULL
          ORDER BY c.created_at LIMIT :limit
          """,
      nativeQuery = true)
  List<DocumentChunkEntity> findChunksWithoutEmbedding(
      @Param("orgId") UUID orgId, @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT COUNT(*), COUNT(c.embedding) FROM document_chunks c
          WHERE c.status = 'ACTIVE' AND c.organization_id = :orgId
          """,
      nativeQuery = true)
  List<Object[]> countEmbeddingProgress(@Param("orgId") UUID orgId);

  @Query(
      value =
          "SELECT COUNT(*), COUNT(c.embedding) FROM document_chunks c WHERE c.status = 'ACTIVE'",
      nativeQuery = true)
  List<Object[]> countEmbeddingProgressAll();

  @Query(
      value =
          """
          SELECT c.document_id, COUNT(*), COUNT(c.embedding) FROM document_chunks c
          WHERE c.document_id IN (:documentIds) AND c.status = 'ACTIVE'
          GROUP BY c.document_id
          """,
      nativeQuery = true)
  List<Object[]> countByDocument(@Param("documentIds") Collection<UUID> documentIds);

  @Query(
      value =
          "SELECT COUNT(*) FROM document_chunks c WHERE c.status = 'ACTIVE' AND c.organization_id ="
              + " :orgId",
      nativeQuery = true)
  long countActive(@Param("orgId") UUID orgId);

  @Query(
      value = "SELECT COUNT(*) FROM document_chunks c WHERE c.status = 'ACTIVE'",
      nativeQuery = true)
  long countActiveAll();

  @Query(
      value =
          """
          SELECT c.document_id, COUNT(*) FROM document_chunks c
          WHERE c.document_id IN (:documentIds) AND c.status = 'ACTIVE'
          GROUP BY c.document_id
          """,
      nativeQuery = true)
  List<Object[]> countChunksByDocument(@Param("documentIds") Collection<UUID> documentIds);
}
