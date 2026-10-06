package com.olima.conversation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    UUID organizationId,
    String title,
    List<MessageResponse> messages,
    Instant createdAt) {}
