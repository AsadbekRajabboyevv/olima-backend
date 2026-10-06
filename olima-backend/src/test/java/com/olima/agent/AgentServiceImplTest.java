package com.olima.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ChatResponse;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.PreparedTurn;
import com.olima.agent.limiter.LlmConcurrencyLimiter;
import com.olima.complaint.ComplaintService;
import com.olima.conversation.ConversationAccessService;
import com.olima.conversation.ConversationService;
import com.olima.conversation.enums.MessageRole;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.usage.UsageService;
import com.olima.usage.dto.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AgentServiceImplTest {

  private ChatAccessGuard accessGuard;
  private ChatTurnPreparer turnPreparer;
  private LlmGateway llmGateway;
  private SseChatStreamer streamer;
  private LlmConcurrencyLimiter limiter;
  private ConversationService conversationService;
  private ConversationAccessService conversationAccess;
  private ComplaintService complaintService;
  private UsageService usageService;
  private AgentServiceImpl agentService;

  private UUID orgId;
  private UUID conversationId;

  @BeforeEach
  void setUp() {
    accessGuard = mock(ChatAccessGuard.class);
    turnPreparer = mock(ChatTurnPreparer.class);
    llmGateway = mock(LlmGateway.class);
    streamer = mock(SseChatStreamer.class);
    limiter = mock(LlmConcurrencyLimiter.class);
    conversationService = mock(ConversationService.class);
    conversationAccess = mock(ConversationAccessService.class);
    complaintService = mock(ComplaintService.class);
    usageService = mock(UsageService.class);

    agentService =
        new AgentServiceImpl(
            accessGuard,
            turnPreparer,
            llmGateway,
            streamer,
            limiter,
            conversationService,
            conversationAccess,
            complaintService,
            usageService,
            AgentTestProperties.defaults(),
            new SimpleMeterRegistry());

    orgId = UUID.randomUUID();
    conversationId = UUID.randomUUID();
  }

  private OrganizationResponse mockOrg() {
    return new OrganizationResponse(
        orgId, "Olima Test", "olima", "Desc", true, "key", null, true, false, null, null, null);
  }

  private PreparedTurn mockPreparedTurn() {
    ChatTurn turn = new ChatTurn(orgId, "Olima Test", conversationId, ChatChannel.PANEL, false, null);
    return new PreparedTurn(turn, "token-123", "system prompt", List.of(), "Hello!");
  }

  @Test
  void chat_whenAccessGuardThrows_abortsWithoutAcquiringPermit() {
    ChatRequest request = new ChatRequest(orgId, null, "Hello", null, null);
    when(accessGuard.authorize(any(), any(), any()))
        .thenThrow(new AccessDeniedException("Organization is disabled"));

    assertThatThrownBy(() -> agentService.chat(request, ChatChannel.PANEL))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(limiter);
    verifyNoInteractions(llmGateway);
  }

  @Test
  void chat_successfulExecution_acquiresAndReleasesPermitAndSavesMessage() {
    ChatRequest request = new ChatRequest(orgId, null, "Hello", null, null);
    OrganizationResponse org = mockOrg();
    when(accessGuard.authorize(any(), any(), any())).thenReturn(org);

    LlmConcurrencyLimiter.Permit permit = mock(LlmConcurrencyLimiter.Permit.class);
    when(limiter.acquire()).thenReturn(permit);

    PreparedTurn prepared = mockPreparedTurn();
    when(turnPreparer.prepare(eq(org), eq(request), eq(ChatChannel.PANEL), any()))
        .thenReturn(prepared);

    org.springframework.ai.chat.model.ChatResponse aiResponse =
        mock(org.springframework.ai.chat.model.ChatResponse.class);
    when(llmGateway.callWithFallback(prepared)).thenReturn(aiResponse);
    when(llmGateway.text(aiResponse)).thenReturn("AI javobi");
    when(llmGateway.usage(aiResponse)).thenReturn(new TokenUsage(10, 20, "gpt-4o"));

    ChatResponse response = agentService.chat(request, ChatChannel.PANEL);

    assertThat(response.message()).isEqualTo("AI javobi");
    assertThat(response.conversationId()).isEqualTo(conversationId);
    assertThat(response.conversationToken()).isEqualTo("token-123");

    verify(conversationService)
        .addMessage(conversationId, MessageRole.ASSISTANT, "AI javobi", null, null);
    verify(usageService).record(eq(orgId), eq(conversationId), eq("PANEL"), any());
    verify(permit).close();
  }

  @Test
  void chatStream_successfulStream_callsStreamerAndReturnsEmitter() {
    ChatRequest request = new ChatRequest(orgId, null, "Stream query", null, null);
    OrganizationResponse org = mockOrg();
    when(accessGuard.authorize(any(), any(), any())).thenReturn(org);

    LlmConcurrencyLimiter.Permit permit = mock(LlmConcurrencyLimiter.Permit.class);
    when(limiter.acquire()).thenReturn(permit);

    SseEmitter emitter = new SseEmitter();
    when(streamer.createEmitter()).thenReturn(emitter);

    PreparedTurn prepared = mockPreparedTurn();
    when(turnPreparer.prepare(eq(org), eq(request), eq(ChatChannel.WIDGET), any()))
        .thenReturn(prepared);

    SseEmitter result = agentService.chatStream(request, ChatChannel.WIDGET);

    assertThat(result).isSameAs(emitter);
    verify(streamer).startStream(emitter, prepared, ChatChannel.WIDGET, permit);
  }

  @Test
  void chatStream_whenPrepareThrows_closesPermitImmediately() {
    ChatRequest request = new ChatRequest(orgId, null, "Stream query", null, null);
    OrganizationResponse org = mockOrg();
    when(accessGuard.authorize(any(), any(), any())).thenReturn(org);

    LlmConcurrencyLimiter.Permit permit = mock(LlmConcurrencyLimiter.Permit.class);
    when(limiter.acquire()).thenReturn(permit);

    when(streamer.createEmitter()).thenReturn(new SseEmitter());
    when(turnPreparer.prepare(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Token verification failed"));

    assertThatThrownBy(() -> agentService.chatStream(request, ChatChannel.WIDGET))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Token verification failed");

    verify(permit).close();
  }
}
