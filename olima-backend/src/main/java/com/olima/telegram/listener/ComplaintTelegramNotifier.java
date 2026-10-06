package com.olima.telegram.listener;

import com.olima.complaint.ComplaintService;
import com.olima.complaint.event.ComplaintConfirmedEvent;
import com.olima.telegram.TelegramBotConfigEntity;
import com.olima.telegram.TelegramService;
import com.olima.telegram.client.TelegramClient;
import com.olima.telegram.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintTelegramNotifier {

  private final TelegramService telegramService;
  private final TelegramClient telegramClient;
  private final TelegramProperties properties;
  private final ComplaintService complaintService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onConfirmed(ComplaintConfirmedEvent event) {
    telegramService
        .findConfig(event.organizationId())
        .filter(TelegramBotConfigEntity::isEnabled)
        .filter(config -> config.getNotificationChatId() != null)
        .ifPresent(
            config -> {
              String text =
                  telegramService
                      .render(properties.complaintNotification(), event.organizationId())
                      .replace("{subject}", nullToEmpty(event.subject()))
                      .replace("{category}", nullToEmpty(event.category()))
                      .replace("{description}", nullToEmpty(event.description()));
              try {
                telegramClient.sendMessage(
                    config.getBotToken(), config.getNotificationChatId(), text);
                complaintService.markSubmitted(event.complaintId());
                log.info(
                    "Complaint {} delivered to Telegram chat of organization {}",
                    event.complaintId(),
                    event.organizationId());
              } catch (Exception e) {
                log.warn(
                    "Failed to deliver complaint {} to Telegram: {}",
                    event.complaintId(),
                    e.getMessage());
              }
            });
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
