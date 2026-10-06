package com.olima.agent.builder;

import com.olima.agent.config.AgentProperties;
import com.olima.conversation.ConversationService;
import com.olima.conversation.dto.MessageResponse;
import com.olima.conversation.enums.MessageRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatHistoryBuilder {

  private final ConversationService conversationService;
  private final AgentProperties.History config;
  private final String truncatedMarker;

  public ChatHistoryBuilder(ConversationService conversationService, AgentProperties properties) {
    this.conversationService = conversationService;
    this.config = properties.history();
    this.truncatedMarker = "\n... " + properties.messages().historyTruncated();
  }

  public List<Message> build(UUID conversationId) {
    if (conversationId == null || config.maxMessages() == 0) {
      return Collections.emptyList();
    }

    List<MessageResponse> recentMessages =
        conversationService.findRecentMessages(conversationId, config.maxMessages() * 2);
    List<Message> selected = new ArrayList<>();
    int totalChars = 0;

    for (MessageResponse m : recentMessages) {
      String content = m.content();
      if (content == null
          || content.isBlank()
          || (m.role() != MessageRole.USER && m.role() != MessageRole.ASSISTANT)) {
        continue;
      }

      if (content.length() > config.maxSingleMessageChars()) {
        content = content.substring(0, config.maxSingleMessageChars()) + truncatedMarker;
      }
      if (totalChars + content.length() > config.maxChars() && !selected.isEmpty()) {
        break;
      }
      selected.add(
          m.role() == MessageRole.USER
              ? new UserMessage(content)
              : new AssistantMessage(content));
      totalChars += content.length();
      if (selected.size() >= config.maxMessages()) {
        break;
      }
    }

    Collections.reverse(selected);
    return selected;
  }
}
