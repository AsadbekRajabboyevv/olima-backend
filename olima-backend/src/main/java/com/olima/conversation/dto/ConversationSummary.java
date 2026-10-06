package com.olima.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(
    UUID id,
    UUID organizationId,
    String title,
    long messageCount,
    Instant createdAt,
    Instant lastMessageAt) {}
