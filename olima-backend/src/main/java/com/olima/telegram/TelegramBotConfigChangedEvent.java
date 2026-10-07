package com.olima.telegram;

import java.util.UUID;

/** Bot ulandi, o'zgardi yoki uzildi — long polling ro'yxatini qayta solishtirish kerak. */
public record TelegramBotConfigChangedEvent(UUID organizationId) {}
