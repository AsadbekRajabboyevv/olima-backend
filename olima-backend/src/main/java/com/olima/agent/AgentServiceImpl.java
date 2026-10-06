package com.olima.agent;

import com.olima.agent.config.AgentProperties;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ChatResponse;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.ConfirmActionRequest;
import com.olima.agent.dto.PreparedTurn;
import com.olima.agent.limiter.LlmConcurrencyLimiter;
import com.olima.common.error.NotFoundException;
import com.olima.complaint.ComplaintService;
import com.olima.complaint.dto.ComplaintResponse;
import com.olima.conversation.ConversationAccessService;
import com.olima.conversation.ConversationService;
import com.olima.conversation.enums.MessageRole;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.security.tenant.CurrentUser;
import com.olima.usage.UsageService;
import com.olima.usage.dto.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

  private final ChatAccessGuard accessGuard;
  private final ChatTurnPreparer turnPreparer;
  private final LlmGateway llmGateway;
  private final SseChatStreamer streamer;
  private final LlmConcurrencyLimiter limiter;
  private final ConversationService conversationService;
  private final ConversationAccessService conversationAccess;
  private final ComplaintService complaintService;
  private final UsageService usageService;
  private final AgentProperties properties;
  private final MeterRegistry meterRegistry;

  @Override
  public ChatResponse chat(ChatRequest request, ChatChannel channel) {
    OrganizationResponse org = accessGuard.authorize(request, channel, CurrentUser.get().orElse(null));
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "error";
    try (LlmConcurrencyLimiter.Permit ignored = limiter.acquire()) {
      PreparedTurn prepared = turnPreparer.prepare(org, request, channel, null);
      ChatTurn turn = prepared.turn();

      org.springframework.ai.chat.model.ChatResponse response = llmGateway.callWithFallback(prepared);
      String answer = llmGateway.text(response);
      saveAssistantMessage(turn.conversationId(), answer);
      recordUsage(turn, llmGateway.usage(response));
      outcome = "success";

      return new ChatResponse(
          turn.conversationId(),
          answer,
          turn.toolCalls(),
          turn.sources(),
          turn.confirmationRequired(),
          turn.pendingComplaintId(),
          prepared.issuedToken());
    } finally {
      sample.stop(
          meterRegistry.timer(
              "olima.llm.request", "channel", channel.name(), "mode", "sync", "outcome", outcome));
    }
  }

  @Override
  public SseEmitter chatStream(ChatRequest request, ChatChannel channel) {
    OrganizationResponse org = accessGuard.authorize(request, channel, CurrentUser.get().orElse(null));
    LlmConcurrencyLimiter.Permit permit = limiter.acquire();

    SseEmitter emitter = streamer.createEmitter();

    PreparedTurn prepared;
    try {
      prepared =
          turnPreparer.prepare(org, request, channel, info -> streamer.sendToolCall(emitter, info));
    } catch (RuntimeException e) {
      permit.close();
      throw e;
    }

    streamer.startStream(emitter, prepared, channel, permit);
    return emitter;
  }

  @Override
  public ChatResponse confirmAction(
      ConfirmActionRequest request, UUID scopeOrganizationId, ChatChannel channel) {
    if (request == null || request.conversationId() == null || request.complaintId() == null) {
      throw new IllegalArgumentException("conversationId va complaintId majburiy");
    }

    ComplaintResponse complaint = complaintService.getComplaint(request.complaintId());

    boolean sameConversation = request.conversationId().equals(complaint.conversationId());
    boolean sameOrganization =
        scopeOrganizationId == null || scopeOrganizationId.equals(complaint.organizationId());
    if (!sameConversation || !sameOrganization) {
      NotFoundException e = new NotFoundException("confirm.complaint_not_found");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }

    conversationAccess.verify(
        request.conversationId(),
        complaint.organizationId(),
        request.conversationToken(),
        channel.requiresConversationToken());

    complaintService.confirmComplaint(complaint.id());
    String message = properties.messages().complaintConfirmed();
    conversationService.addMessage(
        request.conversationId(), MessageRole.ASSISTANT, message, null, null);
    return new ChatResponse(
        request.conversationId(), message, List.of(), List.of(), false, null, null);
  }

  private void saveAssistantMessage(UUID conversationId, String answer) {
    if (answer != null && !answer.isBlank()) {
      conversationService.addMessage(conversationId, MessageRole.ASSISTANT, answer, null, null);
    }
  }

  private void recordUsage(ChatTurn turn, TokenUsage usage) {
    try {
      usageService.record(
          turn.organizationId(), turn.conversationId(), turn.channel().name(), usage);
    } catch (Exception e) {
      log.warn(
          "Failed to record LLM usage for conversation {}: {}",
          turn.conversationId(),
          e.getMessage());
    }
  }
}
