package com.olima.usage;

import com.olima.usage.dto.TokenUsage;
import com.olima.usage.dto.UsageResponse;
import com.olima.usage.dto.UsageSummary;
import java.util.UUID;

public interface UsageService {

  void checkQuota(UUID organizationId, Long organizationQuota);

  void record(UUID organizationId, UUID conversationId, String channel, TokenUsage usage);

  UsageSummary currentMonth(UUID organizationId);

  UsageResponse getOrganizationUsage(UUID organizationId);

  long effectiveQuota(Long organizationQuota);
}
