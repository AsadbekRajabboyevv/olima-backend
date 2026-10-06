package com.olima.agent.dto;

import com.olima.execution.enums.ExecutionStatus;

public record ToolCallInfo(
    String toolName, String input, String output, ExecutionStatus status, long durationMs) {

  public ToolCallInfo withoutPayload() {
    return new ToolCallInfo(toolName, null, null, status, durationMs);
  }
}
