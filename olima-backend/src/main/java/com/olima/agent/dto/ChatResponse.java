package com.olima.agent.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
    UUID conversationId,
    String message,
    List<ToolCallInfo> toolCalls,
    List<String> sources,
    boolean confirmationRequired,
    UUID pendingComplaintId,
    String conversationToken) {}
