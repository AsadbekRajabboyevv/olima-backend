package com.olima.knowledge.ingestion;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentQueue {

  private static final String CLAIM =
      """
      UPDATE documents SET status = 'PROCESSING', attempts = attempts + 1,
             processing_started_at = now(), updated_at = now()
      WHERE id IN (
        SELECT id FROM documents
        WHERE status = 'PENDING'
        ORDER BY created_at
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      )
      RETURNING id
      """;

  private static final String REQUEUE_STALE =
      """
      UPDATE documents SET status = 'PENDING', updated_at = now()
      WHERE status = 'PROCESSING'
        AND processing_started_at < now() - make_interval(secs => ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  public List<UUID> claim(int limit) {
    return jdbcTemplate.queryForList(CLAIM, UUID.class, limit);
  }

  public int requeueStale(Duration staleAfter) {
    return jdbcTemplate.update(REQUEUE_STALE, (double) staleAfter.toSeconds());
  }
}
