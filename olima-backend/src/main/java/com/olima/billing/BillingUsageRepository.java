package com.olima.billing;

import com.olima.billing.dto.DailyUsage;
import com.olima.billing.dto.OrganizationUsage;
import com.olima.billing.dto.UsageTotals;
import com.olima.usage.LlmUsageEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface BillingUsageRepository extends Repository<LlmUsageEntity, UUID> {

  @Query(
      value =
          """
          select count(*) as requests,
                 coalesce(sum(prompt_tokens), 0) as promptTokens,
                 coalesce(sum(completion_tokens), 0) as completionTokens,
                 coalesce(sum(cost_uzs), 0) as costUzs
          from llm_usage
          where organization_id = :orgId and created_at >= :from and created_at < :to
          """,
      nativeQuery = true)
  UsageTotals totals(UUID orgId, Instant from, Instant to);

  @Query(
      value =
          """
          select to_char(created_at at time zone :zone, 'YYYY-MM-DD') as day,
                 count(*) as requests,
                 sum(prompt_tokens + completion_tokens) as tokens,
                 sum(cost_uzs) as costUzs
          from llm_usage
          where organization_id = :orgId and created_at >= :from and created_at < :to
          group by 1
          order by 1
          """,
      nativeQuery = true)
  List<DailyUsage> daily(UUID orgId, Instant from, Instant to, String zone);

  @Query(
      value =
          """
          select organization_id as organizationId,
                 count(*) as requests,
                 sum(prompt_tokens + completion_tokens) as tokens,
                 sum(cost_uzs) as costUzs
          from llm_usage
          where created_at >= :from and created_at < :to
          group by organization_id
          """,
      nativeQuery = true)
  List<OrganizationUsage> byOrganization(Instant from, Instant to);
}
