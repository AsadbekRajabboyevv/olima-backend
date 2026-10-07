package com.olima.telegram;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.olima.agent.AgentService;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ChatResponse;
import com.olima.common.error.NotFoundException;
import com.olima.config.AgentExecutorConfig;
import com.olima.organization.OrganizationService;
import com.olima.security.model.AuthenticatedUser;
import com.olima.security.tenant.CurrentUser;
import com.olima.telegram.client.TelegramClient;
import com.olima.telegram.config.TelegramProperties;
import com.olima.telegram.dto.TelegramBotConfigRequest;
import com.olima.telegram.dto.TelegramBotConfigResponse;
import com.olima.telegram.util.TelegramFormatter;
import com.olima.telegram.util.TelegramUpdateDeduplicator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class TelegramServiceImpl implements TelegramService {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("^\\d+:[A-Za-z0-9_-]{30,}$");
  private static final String ORG = "{org}";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final TelegramBotConfigRepository botConfigRepository;
  private final TelegramConversationRepository telegramConversationRepository;
  private final OrganizationService organizationService;
  private final TelegramClient telegramClient;
  private final TelegramFormatter telegramFormatter;
  private final TelegramUpdateDeduplicator deduplicator;
  private final AgentService agentService;
  private final TelegramProperties properties;
  private final String publicBaseUrl;
  private final ExecutorService agentExecutor;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  private final LoadingCache<String, ReentrantLock> chatLocks =
      Caffeine.newBuilder().weakValues().build(key -> new ReentrantLock());

  public TelegramServiceImpl(
      TelegramBotConfigRepository botConfigRepository,
      TelegramConversationRepository telegramConversationRepository,
      OrganizationService organizationService,
      TelegramClient telegramClient,
      TelegramFormatter telegramFormatter,
      TelegramUpdateDeduplicator deduplicator,
      AgentService agentService,
      TelegramProperties properties,
      @Value("${app.public-base-url}") String publicBaseUrl,
      @Qualifier(AgentExecutorConfig.AGENT_EXECUTOR) ExecutorService agentExecutor,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.botConfigRepository = botConfigRepository;
    this.telegramConversationRepository = telegramConversationRepository;
    this.organizationService = organizationService;
    this.telegramClient = telegramClient;
    this.telegramFormatter = telegramFormatter;
    this.deduplicator = deduplicator;
    this.agentService = agentService;
    this.properties = properties;
    this.publicBaseUrl = publicBaseUrl;
    this.agentExecutor = agentExecutor;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void processWebhookAsync(String webhookSecret, String headerSecret, String rawBody) {
    agentExecutor.execute(
        () -> {
          try {
            JsonNode update = objectMapper.readTree(rawBody);
            handleIncomingUpdate(webhookSecret, headerSecret, update);
          } catch (Exception e) {
            log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
          }
        });
  }

  @Override
  public void processPolledUpdateAsync(UUID botConfigId, JsonNode update) {
    agentExecutor.execute(
        () -> {
          try {
            botConfigRepository
                .findById(botConfigId)
                .filter(TelegramBotConfigEntity::isEnabled)
                .filter(c -> c.getUpdateMode() == TelegramUpdateMode.LONG_POLLING)
                .ifPresent(config -> processUpdate(config, update));
          } catch (Exception e) {
            log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
          }
        });
  }

  @Override
  @Transactional
  public TelegramBotConfigResponse configureBot(
      UUID organizationId, TelegramBotConfigRequest request) {
    if (!organizationService.exists(organizationId)) {
      NotFoundException e = new NotFoundException("get.organization_not_found");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }

    TelegramBotConfigEntity config =
        botConfigRepository
            .findByOrganizationId(organizationId)
            .orElseGet(
                () ->
                    TelegramBotConfigEntity.builder()
                        .organizationId(organizationId)
                        .webhookSecret(generateSecret())
                        .updateMode(properties.defaultUpdateMode())
                        .build());
    TelegramUpdateMode updateMode = resolveUpdateMode(config.getUpdateMode(), request.updateMode());

    String botToken =
        request.botToken() != null && !request.botToken().isBlank()
            ? request.botToken().trim()
            : config.getBotToken();
    if (botToken == null) {
      IllegalArgumentException e = new IllegalArgumentException("Bot token is required");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    if (!TOKEN_PATTERN.matcher(botToken).matches()) {
      IllegalArgumentException e =
          new IllegalArgumentException("That doesn't look like a valid Telegram bot token");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }

    String botUsername = telegramClient.getBotUsername(botToken);

    config.setBotToken(botToken);
    config.setBotUsername(botUsername);
    config.setNotificationChatId(request.notificationChatId());
    config.setUpdateMode(updateMode);
    config.setEnabled(true);
    config = botConfigRepository.save(config);

    if (updateMode == TelegramUpdateMode.WEBHOOK) {
      telegramClient.setWebhook(
          botToken, buildWebhookUrl(config.getWebhookSecret()), config.getWebhookSecret());
    } else {
      // Webhook o'rnatilgan bo'lsa Telegram getUpdates'ga 409 qaytaradi
      telegramClient.deleteWebhook(botToken);
    }
    // Poller'lar commit'dan keyin moslanadi (TelegramLongPollingManager)
    eventPublisher.publishEvent(new TelegramBotConfigChangedEvent(organizationId));

    log.info(
        "Registered Telegram bot @{} for organization {} ({})",
        botUsername,
        organizationId,
        updateMode);
    TelegramBotConfigResponse response = toResponse(config);
    return response;
  }

  /**
   * Rejimni faqat SUPER_ADMIN o'zgartira oladi. ORG_ADMIN null yoki joriy rejimni yuborsa —
   * o'zgarishsiz qoladi.
   */
  private static TelegramUpdateMode resolveUpdateMode(
      TelegramUpdateMode current, TelegramUpdateMode requested) {
    if (requested == null || requested == current) {
      return current;
    }
    boolean superAdmin = CurrentUser.get().map(AuthenticatedUser::isSuperAdmin).orElse(false);
    if (!superAdmin) {
      AccessDeniedException e =
          new AccessDeniedException("Only SUPER_ADMIN can change the Telegram update mode");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    return requested;
  }

  @Override
  @Transactional(readOnly = true)
  public TelegramBotConfigResponse getConfig(UUID organizationId) {
    TelegramBotConfigResponse response =
        botConfigRepository
            .findByOrganizationId(organizationId)
            .map(this::toResponse)
            .orElse(null);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TelegramBotConfigEntity> findConfig(UUID organizationId) {
    Optional<TelegramBotConfigEntity> opt =
        botConfigRepository.findByOrganizationId(organizationId);
    return opt;
  }

  @Override
  @Transactional
  public void disconnectBot(UUID organizationId) {
    TelegramBotConfigEntity config =
        botConfigRepository.findByOrganizationId(organizationId).orElse(null);
    if (config == null) {
      return;
    }
    try {
      telegramClient.deleteWebhook(config.getBotToken());
    } catch (Exception e) {
      log.warn(
          "Failed to delete Telegram webhook for organization {}: {}",
          organizationId,
          e.getMessage());
    }
    botConfigRepository.delete(config);
    eventPublisher.publishEvent(new TelegramBotConfigChangedEvent(organizationId));
  }

  @Override
  public void handleIncomingUpdate(String webhookSecret, String headerSecret, JsonNode update) {
    Optional<TelegramBotConfigEntity> maybeConfig =
        botConfigRepository.findByWebhookSecret(webhookSecret);
    if (maybeConfig.isEmpty() || !maybeConfig.get().isEnabled()) {
      log.warn("Telegram update received for unknown or disabled webhook secret");
      return;
    }
    TelegramBotConfigEntity config = maybeConfig.get();
    if (!constantTimeEquals(config.getWebhookSecret(), headerSecret)) {
      log.warn(
          "Telegram update rejected: secret token header mismatch (organization {})",
          config.getOrganizationId());
      return;
    }
    if (config.getUpdateMode() != TelegramUpdateMode.WEBHOOK) {
      log.warn(
          "Telegram webhook update ignored: bot is in {} mode (organization {})",
          config.getUpdateMode(),
          config.getOrganizationId());
      return;
    }
    processUpdate(config, update);
  }

  private void processUpdate(TelegramBotConfigEntity config, JsonNode update) {
    String prevOrgId = MDC.get("orgId");
    MDC.put("orgId", config.getOrganizationId().toString());
    try {
      JsonNode updateId = update.path("update_id");
      if (updateId.isNumber() && !deduplicator.markFirstSeen(config.getId(), updateId.asLong())) {
        log.debug("Duplicate Telegram update {} ignored", updateId.asLong());
        return;
      }

      JsonNode message = update.path("message");
      JsonNode textNode = message.path("text");
      JsonNode chatIdNode = message.path("chat").path("id");
      if (message.isMissingNode() || textNode.isMissingNode() || chatIdNode.isMissingNode()) {
        return;
      }

      long chatId = chatIdNode.asLong();
      String text = textNode.asString();

      ReentrantLock lock = chatLocks.get(config.getOrganizationId() + ":" + chatId);
      lock.lock();
      try {
        if (text.trim().startsWith("/start")) {
          telegramClient.sendMessage(
              config.getBotToken(),
              chatId,
              render(properties.startGreeting(), config.getOrganizationId()));
          return;
        }
        answer(config, chatId, text);
      } finally {
        lock.unlock();
      }
    } finally {
      if (prevOrgId != null) {
        MDC.put("orgId", prevOrgId);
      } else {
        MDC.remove("orgId");
      }
    }
  }

  private void answer(TelegramBotConfigEntity config, long chatId, String text) {
    try {
      UUID conversationId = findExistingConversationId(config.getOrganizationId(), chatId);
      ChatRequest request =
          new ChatRequest(config.getOrganizationId(), conversationId, text, null, null);
      ChatResponse response = agentService.chat(request, ChatChannel.TELEGRAM);

      if (conversationId == null) {
        saveConversationMapping(config.getOrganizationId(), chatId, response.conversationId());
      }
      telegramClient.sendMessage(
          config.getBotToken(), chatId, telegramFormatter.format(response.message()));
    } catch (Exception e) {
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      try {
        telegramClient.sendMessage(config.getBotToken(), chatId, properties.errorMessage());
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public String render(String template, UUID organizationId) {
    String orgName =
        organizationService.findNames(List.of(organizationId)).getOrDefault(organizationId, "");
    return template.replace(ORG, orgName);
  }

  private UUID findExistingConversationId(UUID organizationId, long chatId) {
    return telegramConversationRepository
        .findByOrganizationIdAndTelegramChatId(organizationId, chatId)
        .map(TelegramConversationEntity::getConversationId)
        .orElse(null);
  }

  private void saveConversationMapping(UUID organizationId, long chatId, UUID conversationId) {
    try {
      telegramConversationRepository.save(
          TelegramConversationEntity.builder()
              .organizationId(organizationId)
              .telegramChatId(chatId)
              .conversationId(conversationId)
              .build());
    } catch (DataIntegrityViolationException e) {
      log.debug("Telegram conversation mapping already exists for chat {}", chatId);
    }
  }

  private String buildWebhookUrl(String webhookSecret) {
    return publicBaseUrl.replaceAll("/+$", "") + "/api/v1/telegram/webhook/" + webhookSecret;
  }

  private static String generateSecret() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return "tg_" + HexFormat.of().formatHex(bytes);
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  private TelegramBotConfigResponse toResponse(TelegramBotConfigEntity config) {
    String webhookUrl =
        config.getUpdateMode() == TelegramUpdateMode.WEBHOOK
            ? buildWebhookUrl(config.getWebhookSecret())
            : null;
    String token = config.getBotToken();
    String masked =
        token != null && token.length() > 10
            ? token.substring(0, 6) + "..." + token.substring(token.length() - 4)
            : "***";
    return new TelegramBotConfigResponse(
        config.getOrganizationId(),
        config.getBotUsername(),
        masked,
        webhookUrl,
        config.isEnabled(),
        config.getNotificationChatId(),
        config.getUpdateMode());
  }
}
