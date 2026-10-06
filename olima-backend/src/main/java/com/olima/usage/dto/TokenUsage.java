package com.olima.usage.dto;

public record TokenUsage(int promptTokens, int completionTokens, String model) {

  public static final TokenUsage NONE = new TokenUsage(0, 0, null);

  public boolean isEmpty() {
    return promptTokens == 0 && completionTokens == 0;
  }
}
