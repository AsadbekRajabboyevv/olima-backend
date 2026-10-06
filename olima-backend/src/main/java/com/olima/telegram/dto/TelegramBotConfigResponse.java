package com.olima.telegram.dto;

import java.util.UUID;

public record TelegramBotConfigResponse(
    UUID organizationId,
    String botUsername,
    String maskedToken,
    String webhookUrl,
    boolean enabled,
    Long notificationChatId) {}
