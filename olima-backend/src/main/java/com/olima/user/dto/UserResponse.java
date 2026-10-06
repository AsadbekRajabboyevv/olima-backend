package com.olima.user.dto;

import com.olima.user.enums.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    UserRole role,
    UUID organizationId,
    String organizationName,
    boolean enabled,
    Instant createdAt) {}
