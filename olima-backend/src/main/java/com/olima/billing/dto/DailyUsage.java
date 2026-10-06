package com.olima.billing.dto;

import java.math.BigDecimal;

public interface DailyUsage {

  String getDay();

  long getRequests();

  long getTokens();

  BigDecimal getCostUzs();
}
