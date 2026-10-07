package com.olima.telegram;

import com.olima.telegram.dto.TelegramBotConfigRequest;
import com.olima.telegram.dto.TelegramBotConfigResponse;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public interface TelegramService {

  void processWebhookAsync(String webhookSecret, String headerSecret, String rawBody);

  /** Long polling orqali olingan update — {@link TelegramLongPollingManager} chaqiradi. */
  void processPolledUpdateAsync(UUID botConfigId, JsonNode update);

  TelegramBotConfigResponse configureBot(UUID organizationId, TelegramBotConfigRequest request);

  TelegramBotConfigResponse getConfig(UUID organizationId);

  Optional<TelegramBotConfigEntity> findConfig(UUID organizationId);

  void disconnectBot(UUID organizationId);

  void handleIncomingUpdate(String webhookSecret, String headerSecret, JsonNode update);

  String render(String template, UUID organizationId);
}
