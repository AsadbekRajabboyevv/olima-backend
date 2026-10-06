package com.olima.agent;

import com.olima.agent.builder.ChatHistoryBuilder;
import com.olima.agent.builder.SystemPromptBuilder;
import com.olima.agent.config.AgentProperties;
import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatRequest;
import com.olima.agent.dto.ChatTurn;
import com.olima.agent.dto.PreparedTurn;
import com.olima.agent.dto.ToolCallInfo;
import com.olima.agent.factory.AgentToolFactory;
import com.olima.conversation.ConversationAccessService;
import com.olima.conversation.ConversationService;
import com.olima.conversation.enums.MessageRole;
import com.olima.organization.dto.OrganizationResponse;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatTurnPreparer {

  private final SystemPromptBuilder systemPromptBuilder;
  private final ChatHistoryBuilder historyBuilder;
  private final ConversationService conversationService;
  private final ConversationAccessService conversationAccess;
  private final AgentToolFactory toolFactory;
  private final AgentProperties properties;

  public PreparedTurn prepare(
      OrganizationResponse org,
      ChatRequest request,
      ChatChannel channel,
      Consumer<ToolCallInfo> toolCallListener) {
    UUID conversationId = request.conversationId();
    String issuedToken = null;
    if (conversationId != null) {
      conversationAccess.verify(
          conversationId,
          org.id(),
          request.conversationToken(),
          channel.requiresConversationToken());
    } else {
      String tokenHash = null;
      if (channel.requiresConversationToken()) {
        issuedToken = conversationAccess.newAccessToken();
        tokenHash = conversationAccess.hash(issuedToken);
      }
      conversationId =
          conversationService
              .createConversation(
                  org.id(),
                  AgentProperties.render(properties.messages().conversationTitle(), org.name()),
                  tokenHash)
              .id();
    }

    List<Message> history = historyBuilder.build(conversationId);
    conversationService.addMessage(conversationId, MessageRole.USER, request.message(), null, null);

    boolean webSearch = toolFactory.webSearchAllowed(org.webSearchEnabled());
    ChatTurn turn =
        new ChatTurn(org.id(), org.name(), conversationId, channel, webSearch, toolCallListener);
    return new PreparedTurn(
        turn, issuedToken, systemPromptBuilder.build(org, webSearch), history, request.message());
  }
}
