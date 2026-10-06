package com.olima.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrganizationUsage {

  UUID getOrganizationId();

  long getRequests();

  long getTokens();

  BigDecimal getCostUzs();
}
