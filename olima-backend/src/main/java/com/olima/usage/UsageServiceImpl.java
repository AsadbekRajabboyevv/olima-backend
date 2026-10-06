package com.olima.usage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.olima.billing.BillingService;
import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.organization.OrganizationService;
import com.olima.usage.config.QuotaProperties;
import com.olima.usage.dto.TokenUsage;
import com.olima.usage.dto.UsageResponse;
import com.olima.usage.dto.UsageSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UsageServiceImpl implements UsageService {

  private final LlmUsageRepository repository;
  private final QuotaProperties properties;
  private final MeterRegistry meterRegistry;
  private final OrganizationService organizationService;
  private final BillingService billingService;
  private final Cache<UUID, Long> monthlyUsage;

  public UsageServiceImpl(
      LlmUsageRepository repository,
      QuotaProperties properties,
      MeterRegistry meterRegistry,
      OrganizationService organizationService,
      BillingService billingService) {
    this.repository = repository;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.organizationService = organizationService;
    this.billingService = billingService;
    this.monthlyUsage =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.usageCacheTtl())
            .maximumSize(10_000)
            .build();
  }

  @Override
  public void checkQuota(UUID organizationId, Long organizationQuota) {
    long quota = organizationQuota != null ? organizationQuota : properties.defaultMonthlyTokens();
    if (quota < 0) {
      return;
    }
    long used =
        monthlyUsage.get(organizationId, id -> repository.sumTokensSince(id, startOfMonth()));
    if (used >= quota) {
      meterRegistry.counter("olima.quota.rejected").increment();
      BusinessException e =
          new BusinessException(
              ErrorCode.QUOTA_EXCEEDED,
              "Tashkilotning oylik so'rovlar limiti tugadi. Administrator bilan bog'laning");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
  }

  @Override
  @Transactional
  public void record(UUID organizationId, UUID conversationId, String channel, TokenUsage usage) {
    if (usage == null || usage.isEmpty()) {
      return;
    }
    repository.save(
        LlmUsageEntity.builder()
            .organizationId(organizationId)
            .conversationId(conversationId)
            .channel(channel)
            .model(usage.model())
            .promptTokens(usage.promptTokens())
            .completionTokens(usage.completionTokens())
            .costUzs(
                billingService.cost(organizationId, usage.promptTokens(), usage.completionTokens()))
            .build());
    monthlyUsage
        .asMap()
        .computeIfPresent(
            organizationId, (id, used) -> used + usage.promptTokens() + usage.completionTokens());
    meterRegistry
        .counter("olima.llm.tokens", "type", "prompt", "channel", channel)
        .increment(usage.promptTokens());
    meterRegistry
        .counter("olima.llm.tokens", "type", "completion", "channel", channel)
        .increment(usage.completionTokens());
  }

  @Override
  @Transactional(readOnly = true)
  public UsageSummary currentMonth(UUID organizationId) {
    UsageSummary summary =
        repository.summarize(organizationId, startOfMonth()).stream()
            .findFirst()
            .orElse(new UsageSummary(organizationId, 0, 0, 0));
    return summary;
  }

  @Override
  @Transactional(readOnly = true)
  public UsageResponse getOrganizationUsage(UUID organizationId) {
    UsageSummary summary = currentMonth(organizationId);
    long quota = effectiveQuota(organizationService.findById(organizationId).monthlyTokenQuota());
    UsageResponse response =
        new UsageResponse(
            organizationId,
            summary.requests(),
            summary.promptTokens(),
            summary.completionTokens(),
            summary.totalTokens(),
            quota);
    return response;
  }

  @Override
  public long effectiveQuota(Long organizationQuota) {
    return organizationQuota != null ? organizationQuota : properties.defaultMonthlyTokens();
  }

  private Instant startOfMonth() {
    return LocalDate.now(properties.zone())
        .withDayOfMonth(1)
        .atStartOfDay(properties.zone())
        .toInstant();
  }
}
