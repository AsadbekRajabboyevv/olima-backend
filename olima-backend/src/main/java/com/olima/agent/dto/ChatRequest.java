package com.olima.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatRequest(
    UUID organizationId,
    UUID conversationId,
    @NotBlank @Size(max = 4000) String message,
    String studentId,
    @Size(max = 200) String conversationToken) {

  public ChatRequest withOrganizationId(UUID orgId) {
    return new ChatRequest(orgId, conversationId, message, studentId, conversationToken);
  }
}
