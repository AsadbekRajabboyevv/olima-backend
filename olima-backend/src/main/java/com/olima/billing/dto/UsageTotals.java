package com.olima.billing.dto;

import java.math.BigDecimal;

public interface UsageTotals {

  long getRequests();

  long getPromptTokens();

  long getCompletionTokens();

  BigDecimal getCostUzs();
}
