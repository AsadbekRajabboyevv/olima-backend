package com.olima.integration.dto;

import com.olima.integration.config.AuthSettings;
import com.olima.integration.enums.AuthType;
import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(
    UUID id,
    UUID organizationId,
    String name,
    String description,
    String baseUrl,
    AuthType authType,
    AuthSettings authConfig,
    boolean hasUsername,
    boolean hasPassword,
    boolean hasSecret,
    boolean enabled,
    long toolCount,
    Instant createdAt,
    Instant updatedAt) {}
