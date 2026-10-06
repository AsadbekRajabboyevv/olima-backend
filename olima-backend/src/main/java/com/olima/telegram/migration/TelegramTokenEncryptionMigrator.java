package com.olima.telegram.migration;

import com.olima.security.crypto.SecretCipher;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramTokenEncryptionMigrator implements ApplicationRunner {

  private final JdbcTemplate jdbcTemplate;
  private final SecretCipher cipher;

  @Override
  public void run(ApplicationArguments args) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT id, bot_token FROM telegram_bot_configs WHERE bot_token NOT LIKE 'enc:v1:%'");
    for (Map<String, Object> row : rows) {
      jdbcTemplate.update(
          "UPDATE telegram_bot_configs SET bot_token = ? WHERE id = ?",
          cipher.encrypt((String) row.get("bot_token")),
          row.get("id"));
    }
    if (!rows.isEmpty()) {
      log.info("Encrypted {} legacy Telegram bot token(s)", rows.size());
    }
  }
}
