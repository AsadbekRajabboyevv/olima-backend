package com.olima.telegram.dto;

import com.olima.telegram.TelegramUpdateMode;
import jakarta.validation.constraints.Size;

/**
 * @param updateMode faqat SUPER_ADMIN o'zgartira oladi; null — mavjud rejim (yangi bot uchun
 *     app.telegram.default-update-mode) saqlanadi
 */
public record TelegramBotConfigRequest(
    @Size(max = 200) String botToken, Long notificationChatId, TelegramUpdateMode updateMode) {}
