package com.olima.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olima.agent.builder.ChatHistoryBuilder;
import com.olima.agent.builder.SystemPromptBuilder;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ConfirmActionRequest;
import com.olima.agent.factory.AgentToolFactory;
import com.olima.agent.limiter.LlmConcurrencyLimiter;
import com.olima.common.error.NotFoundException;
import com.olima.complaint.ComplaintService;
import com.olima.complaint.dto.ComplaintResponse;
import com.olima.complaint.enums.ComplaintStatus;
import com.olima.conversation.ConversationAccessService;
import com.olima.conversation.ConversationService;
import com.olima.conversation.enums.MessageRole;
import com.olima.organization.OrganizationService;
import com.olima.usage.UsageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

class AgentServiceConfirmActionTest {

  private final UUID orgA = UUID.randomUUID();
  private final UUID orgB = UUID.randomUUID();
  private final UUID conversationId = UUID.randomUUID();
  private final UUID complaintId = UUID.randomUUID();

  private ComplaintService complaintService;
  private ConversationService conversationService;
  private ConversationAccessService conversationAccess;
  private AgentServiceImpl agentService;

  @BeforeEach
  void setUp() {
    complaintService = mock(ComplaintService.class);
    conversationService = mock(ConversationService.class);
    conversationAccess = mock(ConversationAccessService.class);
    agentService =
        new AgentServiceImpl(
            mock(ChatAccessGuard.class),
            mock(ChatTurnPreparer.class),
            mock(LlmGateway.class),
            mock(SseChatStreamer.class),
            mock(LlmConcurrencyLimiter.class),
            conversationService,
            conversationAccess,
            complaintService,
            mock(UsageService.class),
            AgentTestProperties.defaults(),
            new SimpleMeterRegistry());
  }

  private void storedComplaint(UUID orgId, UUID convId) {
    ComplaintResponse complaint =
        new ComplaintResponse(
            complaintId, orgId, convId, null, null, null, ComplaintStatus.DRAFT, null, null);
    when(complaintService.getComplaint(complaintId)).thenReturn(complaint);
  }

  @Test
  void widgetOfAnotherOrganizationCannotConfirm() {
    storedComplaint(orgB, conversationId);

    assertThatThrownBy(
            () ->
                agentService.confirmAction(
                    new ConfirmActionRequest(conversationId, complaintId, "token"),
                    orgA,
                    ChatChannel.WIDGET))
        .isInstanceOf(NotFoundException.class);
    verify(complaintService, never()).confirmComplaint(any());
    verify(conversationService, never()).addMessage(any(), any(), any(), any(), any());
  }

  @Test
  void complaintFromAnotherConversationCannotBeConfirmed() {
    storedComplaint(orgA, UUID.randomUUID());

    assertThatThrownBy(
            () ->
                agentService.confirmAction(
                    new ConfirmActionRequest(conversationId, complaintId, "token"),
                    orgA,
                    ChatChannel.WIDGET))
        .isInstanceOf(NotFoundException.class);
    verify(complaintService, never()).confirmComplaint(any());
  }

  @Test
  void widgetWithWrongConversationTokenIsRejected() {
    storedComplaint(orgA, conversationId);
    doThrow(new NotFoundException("verify.conversation_not_found"))
        .when(conversationAccess)
        .verify(conversationId, orgA, "wrong", true);

    assertThatThrownBy(
            () ->
                agentService.confirmAction(
                    new ConfirmActionRequest(conversationId, complaintId, "wrong"),
                    orgA,
                    ChatChannel.WIDGET))
        .isInstanceOf(NotFoundException.class);
    verify(complaintService, never()).confirmComplaint(any());
  }

  @Test
  void ownerConfirmsOwnDraft() {
    storedComplaint(orgA, conversationId);

    var response =
        agentService.confirmAction(
            new ConfirmActionRequest(conversationId, complaintId, "token"),
            orgA,
            ChatChannel.WIDGET);

    verify(conversationAccess).verify(conversationId, orgA, "token", true);
    verify(complaintService).confirmComplaint(complaintId);
    verify(conversationService).addMessage(any(), any(MessageRole.class), any(), any(), any());
    assertThat(response.conversationId()).isEqualTo(conversationId);
  }

  @Test
  void superAdminPanelIsScopedByComplaintsOwnOrganization() {
    storedComplaint(orgB, conversationId);

    agentService.confirmAction(
        new ConfirmActionRequest(conversationId, complaintId, null), null, ChatChannel.PANEL);

    verify(conversationAccess).verify(conversationId, orgB, null, false);
    verify(complaintService).confirmComplaint(complaintId);
  }

  @Test
  void missingIdsAreBadRequest() {
    assertThatThrownBy(
            () ->
                agentService.confirmAction(
                    new ConfirmActionRequest(null, complaintId, null), orgA, ChatChannel.PANEL))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
