package com.olima.conversation;

import com.olima.common.error.NotFoundException;
import com.olima.conversation.dto.ConversationResponse;
import com.olima.conversation.dto.ConversationSummary;
import com.olima.conversation.dto.MessageResponse;
import com.olima.conversation.enums.MessageRole;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationServiceImpl implements ConversationService {

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final ConversationMapper conversationMapper;

  @Override
  @Transactional
  public ConversationResponse createConversation(UUID orgId, String title) {
    return createConversation(orgId, title, null);
  }

  @Override
  @Transactional
  public ConversationResponse createConversation(UUID orgId, String title, String accessTokenHash) {
    ConversationEntity entity =
        ConversationEntity.builder()
            .organizationId(orgId)
            .title(title)
            .accessTokenHash(accessTokenHash)
            .build();
    ConversationResponse response =
        conversationMapper.toResponse(conversationRepository.save(entity));
    return response;
  }

  @Override
  @Transactional
  public void addMessage(
      UUID convId, MessageRole role, String content, String toolCallId, String toolName) {
    if (!conversationRepository.existsById(convId)) {
      NotFoundException e = new NotFoundException("append.conversation_not_found");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    MessageEntity message =
        MessageEntity.builder()
            .conversation(conversationRepository.getReferenceById(convId))
            .role(role)
            .content(content)
            .toolCallId(toolCallId)
            .toolName(toolName)
            .build();
    messageRepository.save(message);
  }

  @Override
  public ConversationResponse findById(UUID id) {
    ConversationResponse response =
        conversationRepository
            .findById(id)
            .map(conversationMapper::toResponse)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("get.conversation_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    return response;
  }

  @Override
  public List<MessageResponse> findRecentMessages(UUID conversationId, int limit) {
    if (conversationId == null || limit <= 0) {
      return List.of();
    }
    return messageRepository
        .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit))
        .stream()
        .map(conversationMapper::toMessageResponse)
        .toList();
  }

  @Override
  public Page<ConversationSummary> findByOrganization(UUID orgId, Pageable pageable) {
    Page<ConversationSummary> page = conversationRepository.findSummaries(orgId, pageable);
    return page;
  }
}
