package com.olima.telegram.dto;

import com.olima.telegram.TelegramUpdateMode;
import java.util.UUID;

/**
 * @param webhookUrl faqat WEBHOOK rejimida; LONG_POLLING da null
 */
public record TelegramBotConfigResponse(
    UUID organizationId,
    String botUsername,
    String maskedToken,
    String webhookUrl,
    boolean enabled,
    Long notificationChatId,
    TelegramUpdateMode updateMode) {}
