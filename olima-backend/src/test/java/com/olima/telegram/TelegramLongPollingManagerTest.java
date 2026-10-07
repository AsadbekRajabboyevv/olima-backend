package com.olima.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olima.telegram.client.TelegramClient;
import com.olima.telegram.exception.TelegramPollingException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TelegramLongPollingManagerTest {

  private static final String TOKEN = "123456:ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  private TelegramBotConfigRepository repository;
  private TelegramClient telegramClient;
  private TelegramService telegramService;
  private TelegramLongPollingManager manager;
  private TelegramBotConfigEntity config;

  @BeforeEach
  void setUp() {
    repository = mock(TelegramBotConfigRepository.class);
    telegramClient = mock(TelegramClient.class);
    telegramService = mock(TelegramService.class);
    manager =
        new TelegramLongPollingManager(
            repository,
            telegramClient,
            telegramService,
            TelegramServiceImplTest.properties(TelegramUpdateMode.WEBHOOK));

    config =
        TelegramBotConfigEntity.builder()
            .organizationId(UUID.randomUUID())
            .botToken(TOKEN)
            .botUsername("test_bot")
            .webhookSecret("tg_secret")
            .updateMode(TelegramUpdateMode.LONG_POLLING)
            .build();
    config.setId(UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    manager.stop();
  }

  @Test
  void pollsUpdatesAndAdvancesOffset() {
    when(repository.findByEnabledTrueAndUpdateMode(TelegramUpdateMode.LONG_POLLING))
        .thenReturn(List.of(config));
    JsonNode batch = json("[{\"update_id\": 10}, {\"update_id\": 11}]");
    when(telegramClient.getUpdates(TOKEN, 0)).thenReturn(batch);
    when(telegramClient.getUpdates(TOKEN, 12)).thenAnswer(inv -> idle());

    manager.start();

    verify(telegramService, timeout(2000)).processPolledUpdateAsync(config.getId(), batch.get(0));
    verify(telegramService, timeout(2000)).processPolledUpdateAsync(config.getId(), batch.get(1));
    // Keyingi so'rov offset=12 bilan — 10 va 11 Telegram'da tasdiqlanadi
    verify(telegramClient, timeout(2000).atLeastOnce()).getUpdates(TOKEN, 12);
    assertThat(manager.activePollerCount()).isEqualTo(1);
  }

  @Test
  void stopsPollerWhenBotLeavesLongPollingMode() {
    when(repository.findByEnabledTrueAndUpdateMode(TelegramUpdateMode.LONG_POLLING))
        .thenReturn(List.of(config))
        .thenReturn(List.of());
    when(telegramClient.getUpdates(eq(TOKEN), anyLong())).thenAnswer(inv -> idle());

    manager.start();
    assertThat(manager.activePollerCount()).isEqualTo(1);

    manager.onConfigChanged(new TelegramBotConfigChangedEvent(config.getOrganizationId()));
    assertThat(manager.activePollerCount()).isZero();
  }

  @Test
  void deletesLeftoverWebhookOnConflict() {
    when(repository.findByEnabledTrueAndUpdateMode(TelegramUpdateMode.LONG_POLLING))
        .thenReturn(List.of(config));
    when(telegramClient.getUpdates(TOKEN, 0))
        .thenThrow(
            new TelegramPollingException(
                409, "Conflict: can't use getUpdates method while webhook is active"))
        .thenAnswer(inv -> idle());

    manager.start();

    verify(telegramClient, timeout(2000)).deleteWebhook(TOKEN);
  }

  /** Telegram'ning bo'sh long poll javobiga taqlid: biroz kutib bo'sh massiv. */
  private JsonNode idle() throws InterruptedException {
    Thread.sleep(50);
    return json("[]");
  }

  private JsonNode json(String raw) {
    return objectMapper.readTree(raw);
  }
}
