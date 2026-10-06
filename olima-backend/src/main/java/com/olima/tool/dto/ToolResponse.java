package com.olima.tool.dto;

import com.olima.tool.enums.AccessLevel;
import com.olima.tool.enums.ToolType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ToolResponse(
    UUID id,
    UUID organizationId,
    String name,
    String description,
    ToolType type,
    String configuration,
    boolean enabled,
    boolean requiresConfirmation,
    AccessLevel accessLevel,
    List<ToolParameterResponse> parameters,
    Instant createdAt,
    Instant updatedAt) {}
