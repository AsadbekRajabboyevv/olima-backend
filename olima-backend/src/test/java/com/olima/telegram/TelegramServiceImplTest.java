package com.olima.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olima.agent.AgentService;
import com.olima.organization.OrganizationService;
import com.olima.security.model.AuthenticatedUser;
import com.olima.telegram.client.TelegramClient;
import com.olima.telegram.config.TelegramProperties;
import com.olima.telegram.dto.TelegramBotConfigRequest;
import com.olima.telegram.dto.TelegramBotConfigResponse;
import com.olima.telegram.util.TelegramFormatter;
import com.olima.telegram.util.TelegramUpdateDeduplicator;
import com.olima.user.enums.UserRole;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TelegramServiceImplTest {

  private static final String TOKEN = "123456:ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final String SECRET = "tg_secret";

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final UUID orgId = UUID.randomUUID();

  private TelegramBotConfigRepository repository;
  private OrganizationService organizationService;
  private TelegramClient telegramClient;
  private TelegramUpdateDeduplicator deduplicator;
  private ApplicationEventPublisher eventPublisher;
  private ExecutorService executor;
  private TelegramServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = mock(TelegramBotConfigRepository.class);
    organizationService = mock(OrganizationService.class);
    telegramClient = mock(TelegramClient.class);
    deduplicator = mock(TelegramUpdateDeduplicator.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    executor = Executors.newSingleThreadExecutor();

    when(organizationService.exists(orgId)).thenReturn(true);
    when(organizationService.findNames(any())).thenReturn(Map.of(orgId, "Klinika"));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(telegramClient.getBotUsername(TOKEN)).thenReturn("test_bot");
    when(deduplicator.markFirstSeen(any(), anyLong())).thenReturn(true);

    service =
        new TelegramServiceImpl(
            repository,
            mock(TelegramConversationRepository.class),
            organizationService,
            telegramClient,
            mock(TelegramFormatter.class),
            deduplicator,
            mock(AgentService.class),
            properties(TelegramUpdateMode.WEBHOOK),
            "https://olima.example.uz/",
            executor,
            objectMapper,
            eventPublisher);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    executor.shutdownNow();
  }

  @Test
  void newBotUsesDefaultWebhookMode() {
    login(UserRole.ORG_ADMIN);

    TelegramBotConfigResponse response =
        service.configureBot(orgId, new TelegramBotConfigRequest(TOKEN, null, null));

    assertThat(response.updateMode()).isEqualTo(TelegramUpdateMode.WEBHOOK);
    assertThat(response.webhookUrl())
        .startsWith("https://olima.example.uz/api/v1/telegram/webhook/tg_");
    verify(telegramClient).setWebhook(eq(TOKEN), anyString(), anyString());
    verify(telegramClient, never()).deleteWebhook(any());
    verify(eventPublisher).publishEvent(new TelegramBotConfigChangedEvent(orgId));
  }

  @Test
  void superAdminCanChooseLongPolling() {
    login(UserRole.SUPER_ADMIN);

    TelegramBotConfigResponse response =
        service.configureBot(
            orgId, new TelegramBotConfigRequest(TOKEN, null, TelegramUpdateMode.LONG_POLLING));

    assertThat(response.updateMode()).isEqualTo(TelegramUpdateMode.LONG_POLLING);
    assertThat(response.webhookUrl()).isNull();
    verify(telegramClient).deleteWebhook(TOKEN);
    verify(telegramClient, never()).setWebhook(any(), any(), any());
    verify(eventPublisher).publishEvent(new TelegramBotConfigChangedEvent(orgId));
  }

  @Test
  void orgAdminCannotChangeUpdateMode() {
    login(UserRole.ORG_ADMIN);

    assertThatThrownBy(
            () ->
                service.configureBot(
                    orgId,
                    new TelegramBotConfigRequest(TOKEN, null, TelegramUpdateMode.LONG_POLLING)))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(telegramClient);
    verify(repository, never()).save(any());
  }

  @Test
  void orgAdminKeepsModeChosenBySuperAdmin() {
    TelegramBotConfigEntity existing = config(TelegramUpdateMode.LONG_POLLING);
    when(repository.findByOrganizationId(orgId)).thenReturn(Optional.of(existing));
    login(UserRole.ORG_ADMIN);

    // Panel ORG_ADMIN uchun rejimni yubormaydi — faqat chat ID o'zgaradi
    TelegramBotConfigResponse response =
        service.configureBot(orgId, new TelegramBotConfigRequest(null, -100L, null));

    assertThat(response.updateMode()).isEqualTo(TelegramUpdateMode.LONG_POLLING);
    assertThat(response.notificationChatId()).isEqualTo(-100L);
    verify(telegramClient).deleteWebhook(TOKEN);
  }

  @Test
  void webhookUpdateIgnoredForLongPollingBot() {
    when(repository.findByWebhookSecret(SECRET))
        .thenReturn(Optional.of(config(TelegramUpdateMode.LONG_POLLING)));

    service.handleIncomingUpdate(SECRET, SECRET, startUpdate(1));

    verifyNoInteractions(deduplicator);
    verify(telegramClient, never()).sendMessage(any(), anyLong(), any());
  }

  @Test
  void polledUpdateIsProcessed() throws Exception {
    TelegramBotConfigEntity config = config(TelegramUpdateMode.LONG_POLLING);
    when(repository.findById(config.getId())).thenReturn(Optional.of(config));

    service.processPolledUpdateAsync(config.getId(), startUpdate(7));
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    verify(deduplicator).markFirstSeen(config.getId(), 7);
    verify(telegramClient).sendMessage(eq(TOKEN), eq(42L), any());
  }

  @Test
  void polledUpdateIgnoredAfterSwitchToWebhook() throws Exception {
    TelegramBotConfigEntity config = config(TelegramUpdateMode.WEBHOOK);
    when(repository.findById(config.getId())).thenReturn(Optional.of(config));

    service.processPolledUpdateAsync(config.getId(), startUpdate(8));
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    verifyNoInteractions(deduplicator);
    verify(telegramClient, never()).sendMessage(any(), anyLong(), any());
  }

  private TelegramBotConfigEntity config(TelegramUpdateMode mode) {
    TelegramBotConfigEntity config =
        TelegramBotConfigEntity.builder()
            .organizationId(orgId)
            .botToken(TOKEN)
            .botUsername("test_bot")
            .webhookSecret(SECRET)
            .updateMode(mode)
            .build();
    config.setId(UUID.randomUUID());
    return config;
  }

  private JsonNode startUpdate(long updateId) {
    return objectMapper.readTree(
        """
        {"update_id": %d, "message": {"text": "/start", "chat": {"id": 42}}}
        """
            .formatted(updateId));
  }

  private void login(UserRole role) {
    var user = new AuthenticatedUser(UUID.randomUUID(), "u", role, orgId);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
  }

  static TelegramProperties properties(TelegramUpdateMode defaultMode) {
    return new TelegramProperties(
        "https://api.telegram.org",
        4000,
        "Markdown",
        List.of("message"),
        Duration.ofDays(3),
        defaultMode,
        Duration.ofSeconds(30),
        Duration.ofSeconds(30),
        "Salom {org}",
        "Xatolik",
        "Murojaat {org}");
  }
}
