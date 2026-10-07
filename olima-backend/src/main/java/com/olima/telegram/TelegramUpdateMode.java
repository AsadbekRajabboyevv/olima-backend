package com.olima.telegram;

/** Bot Telegram'dan yangilanishlarni qanday oladi. */
public enum TelegramUpdateMode {
  /** Telegram o'zi {public-base-url}/api/v1/telegram/webhook/{secret} ga POST qiladi. */
  WEBHOOK,
  /** Server o'zi getUpdates orqali so'raydi — kiruvchi ulanish kerak emas. */
  LONG_POLLING
}
