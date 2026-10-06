package com.olima.usage;

import com.olima.usage.dto.UsageSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LlmUsageRepository extends JpaRepository<LlmUsageEntity, UUID> {

  @Query(
      """
      select coalesce(sum(u.promptTokens + u.completionTokens), 0)
            from LlmUsageEntity u
            where u.organizationId = :orgId
            and u.createdAt >= :since
      """)
  long sumTokensSince(UUID orgId, Instant since);

  @Query(
      """
      select new com.olima.usage.dto.UsageSummary(
            u.organizationId,
            count(u),
            coalesce(sum(u.promptTokens), 0),
            coalesce(sum(u.completionTokens), 0))
      from LlmUsageEntity u
      where u.organizationId = :orgId and u.createdAt >= :since
      group by u.organizationId
      """)
  List<UsageSummary> summarize(UUID orgId, Instant since);
}
