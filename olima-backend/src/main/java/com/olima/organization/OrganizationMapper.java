package com.olima.organization;

import com.olima.organization.dto.OrganizationRequest;
import com.olima.organization.dto.OrganizationResponse;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapper {

  public OrganizationResponse toResponse(OrganizationEntity entity) {
    return new OrganizationResponse(
        entity.getId(),
        entity.getName(),
        entity.getSlug(),
        entity.getDescription(),
        entity.isEnabled(),
        entity.getWidgetKey(),
        entity.getWidgetGreeting(),
        entity.isWidgetGreetingEnabled(),
        entity.isWebSearchEnabled(),
        entity.getMonthlyTokenQuota(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public OrganizationEntity toEntity(OrganizationRequest request) {
    return OrganizationEntity.builder()
        .name(request.name())
        .description(request.description())
        .enabled(true)
        .monthlyTokenQuota(request.monthlyTokenQuota())
        .widgetKey(newWidgetKey())
        .build();
  }

  private static final SecureRandom RANDOM = new SecureRandom();

  public static String newWidgetKey() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return "wk_" + HexFormat.of().formatHex(bytes);
  }
}
