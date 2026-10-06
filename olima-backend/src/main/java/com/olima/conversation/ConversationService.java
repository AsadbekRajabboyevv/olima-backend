package com.olima.conversation;

import com.olima.conversation.dto.ConversationResponse;
import com.olima.conversation.dto.ConversationSummary;
import com.olima.conversation.dto.MessageResponse;
import com.olima.conversation.enums.MessageRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConversationService {

  ConversationResponse createConversation(UUID orgId, String title);

  ConversationResponse createConversation(UUID orgId, String title, String accessTokenHash);

  void addMessage(
      UUID convId, MessageRole role, String content, String toolCallId, String toolName);

  ConversationResponse findById(UUID id);

  /** Suhbatning eng so'nggi xabarlari, yangisidan eskisiga qarab, ko'pi bilan {@code limit} ta. */
  List<MessageResponse> findRecentMessages(UUID conversationId, int limit);

  Page<ConversationSummary> findByOrganization(UUID orgId, Pageable pageable);
}
