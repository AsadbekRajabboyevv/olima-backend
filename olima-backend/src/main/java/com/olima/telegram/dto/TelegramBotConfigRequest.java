package com.olima.telegram.dto;

import jakarta.validation.constraints.Size;

public record TelegramBotConfigRequest(@Size(max = 200) String botToken, Long notificationChatId) {}
