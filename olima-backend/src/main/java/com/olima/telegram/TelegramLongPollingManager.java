package com.olima.telegram;

import com.olima.telegram.client.TelegramClient;
import com.olima.telegram.config.TelegramProperties;
import com.olima.telegram.exception.TelegramPollingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;

/**
 * LONG_POLLING rejimidagi har bir yoqilgan bot uchun bitta virtual thread getUpdates'ni aylantiradi.
 * Kutish paytida thread hech narsa qilmaydi, shuning uchun o'nlab bot deyarli yuk bermaydi.
 *
 * <p>Ro'yxat bazadan olinadi: bot o'zgarganda (event) va har {@code polling-reconcile-interval}
 * da solishtiriladi. Telegram bitta botni faqat bitta getUpdates'ga beradi — ilova bir nechta
 * nusxada ishga tushirilsa 409 bo'ladi.
 */
@Slf4j
@Component
public class TelegramLongPollingManager implements SmartLifecycle {

  private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);

  private final TelegramBotConfigRepository repository;
  private final TelegramClient telegramClient;
  private final TelegramService telegramService;
  private final TelegramProperties properties;

  private final Map<UUID, Poller> pollers = new ConcurrentHashMap<>();
  private volatile boolean running;

  public TelegramLongPollingManager(
      TelegramBotConfigRepository repository,
      TelegramClient telegramClient,
      TelegramService telegramService,
      TelegramProperties properties) {
    this.repository = repository;
    this.telegramClient = telegramClient;
    this.telegramService = telegramService;
    this.properties = properties;
  }

  @Override
  public void start() {
    running = true;
    reconcile();
  }

  @Override
  public synchronized void stop() {
    running = false;
    pollers.values().forEach(Poller::stop);
    pollers.clear();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onConfigChanged(TelegramBotConfigChangedEvent event) {
    reconcile();
  }

  @Scheduled(
      fixedDelayString = "${app.telegram.polling-reconcile-interval:60s}",
      initialDelayString = "${app.telegram.polling-reconcile-interval:60s}")
  public void scheduledReconcile() {
    reconcile();
  }

  /** Bazadagi LONG_POLLING botlar bilan ishlab turgan poller'larni moslaydi. */
  synchronized void reconcile() {
    if (!running) {
      return;
    }
    List<TelegramBotConfigEntity> configs;
    try {
      configs = repository.findByEnabledTrueAndUpdateMode(TelegramUpdateMode.LONG_POLLING);
    } catch (Exception e) {
      log.warn("Telegram long polling reconcile skipped: {}", e.getMessage());
      return;
    }
    Map<UUID, TelegramBotConfigEntity> desired = new HashMap<>();
    configs.forEach(c -> desired.put(c.getId(), c));

    pollers
        .entrySet()
        .removeIf(
            entry -> {
              TelegramBotConfigEntity config = desired.get(entry.getKey());
              // Token almashgan bo'lsa ham eski poller to'xtatiladi va yangisi ochiladi
              if (config == null || !config.getBotToken().equals(entry.getValue().botToken)) {
                entry.getValue().stop();
                return true;
              }
              return false;
            });

    desired.forEach(
        (id, config) ->
            pollers.computeIfAbsent(
                id, key -> new Poller(id, config.getBotToken(), config.getBotUsername()).start()));
  }

  int activePollerCount() {
    return pollers.size();
  }

  private final class Poller implements Runnable {

    private final UUID configId;
    private final String botToken;
    private final String botUsername;
    private volatile boolean active = true;
    private Thread thread;
    private long offset;

    private Poller(UUID configId, String botToken, String botUsername) {
      this.configId = configId;
      this.botToken = botToken;
      this.botUsername = botUsername;
    }

    private Poller start() {
      thread = Thread.ofVirtual().name("tg-poll-" + botUsername).start(this);
      return this;
    }

    private void stop() {
      active = false;
      if (thread != null) {
        thread.interrupt();
      }
    }

    @Override
    public void run() {
      log.info("Telegram long polling started for @{}", botUsername);
      Duration backoff = MIN_BACKOFF;
      while (active && running) {
        try {
          JsonNode updates = telegramClient.getUpdates(botToken, offset);
          for (JsonNode update : updates) {
            // Keyingi getUpdates shu offset bilan chaqirilganda Telegram bularni tasdiqlangan
            // deb hisoblaydi. Qayta ishga tushishda qaytib kelganlarini deduplicator ushlaydi.
            offset = Math.max(offset, update.path("update_id").asLong() + 1);
            telegramService.processPolledUpdateAsync(configId, update);
          }
          backoff = MIN_BACKOFF;
        } catch (Exception e) {
          if (!active || !running) {
            break;
          }
          handleFailure(e);
          if (!sleep(backoff)) {
            break;
          }
          backoff = min(backoff.multipliedBy(2), properties.pollingMaxBackoff());
        }
      }
      log.info("Telegram long polling stopped for @{}", botUsername);
    }

    private void handleFailure(Exception e) {
      if (e instanceof TelegramPollingException pe
          && pe.errorCode() == 409
          && pe.getMessage() != null
          && pe.getMessage().contains("webhook")) {
        // Rejim LONG_POLLING, lekin Telegram'da webhook qolib ketgan (masalan deleteWebhook
        // o'tmay qolgan) — getUpdates ishlashi uchun uni o'chiramiz.
        log.warn("Webhook still set for @{} in LONG_POLLING mode, deleting it", botUsername);
        try {
          telegramClient.deleteWebhook(botToken);
        } catch (Exception ex) {
          log.warn("deleteWebhook failed for @{}: {}", botUsername, ex.getMessage());
        }
        return;
      }
      log.warn("Telegram getUpdates failed for @{}: {}", botUsername, e.getMessage());
    }

    private boolean sleep(Duration duration) {
      try {
        Thread.sleep(duration);
        return true;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }
}
