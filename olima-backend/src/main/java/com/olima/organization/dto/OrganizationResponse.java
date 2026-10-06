package com.olima.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
    UUID id,
    String name,
    String slug,
    String description,
    boolean enabled,
    String widgetKey,
    String widgetGreeting,
    boolean widgetGreetingEnabled,
    boolean webSearchEnabled,
    Long monthlyTokenQuota,
    Instant createdAt,
    Instant updatedAt) {}
