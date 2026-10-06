package com.olima.execution.dto;

import com.olima.execution.ToolExecutionEntity;
import com.olima.execution.enums.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ToolExecutionResponse(
    UUID id,
    UUID organizationId,
    UUID conversationId,
    UUID toolId,
    String toolName,
    String input,
    String output,
    ExecutionStatus status,
    Long durationMs,
    String errorMessage,
    Instant createdAt) {

  public static ToolExecutionResponse of(ToolExecutionEntity e) {
    return new ToolExecutionResponse(
        e.getId(),
        e.getOrganizationId(),
        e.getConversationId(),
        e.getToolId(),
        e.getToolName(),
        e.getInput(),
        e.getOutput(),
        e.getStatus(),
        e.getDurationMs(),
        e.getErrorMessage(),
        e.getCreatedAt());
  }
}
