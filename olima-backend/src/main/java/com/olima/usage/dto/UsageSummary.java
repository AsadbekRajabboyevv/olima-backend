package com.olima.usage.dto;

import java.util.UUID;

public record UsageSummary(
    UUID organizationId, long requests, long promptTokens, long completionTokens) {

  public long totalTokens() {
    return promptTokens + completionTokens;
  }
}
