package com.olima.telegram.util;

import com.olima.telegram.config.TelegramProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TelegramUpdateDeduplicator {

  private final JdbcTemplate jdbcTemplate;
  private final TelegramProperties properties;

  public TelegramUpdateDeduplicator(JdbcTemplate jdbcTemplate, TelegramProperties properties) {
    this.jdbcTemplate = jdbcTemplate;
    this.properties = properties;
  }

  public boolean markFirstSeen(UUID botConfigId, long updateId) {
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO telegram_processed_updates (bot_config_id, update_id, processed_at)
            VALUES (?, ?, now())
            ON CONFLICT DO NOTHING
            """,
            botConfigId,
            updateId);
    return inserted > 0;
  }

  @Scheduled(cron = "${app.telegram.cleanup-cron:0 15 4 * * *}")
  public void cleanup() {
    Instant before = Instant.now().minus(properties.processedUpdateRetention());
    int deleted =
        jdbcTemplate.update(
            "DELETE FROM telegram_processed_updates WHERE processed_at < ?",
            Timestamp.from(before));
    if (deleted > 0) {
      log.info("Deleted {} processed Telegram update id(s)", deleted);
    }
  }
}
