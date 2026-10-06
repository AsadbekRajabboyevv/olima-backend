package com.olima.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.olima.user.enums.UserRole;
import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
    String token,
    Instant tokenExpiresAt,
    String username,
    UserRole role,
    UUID organizationId,
    String organizationName,
    @JsonIgnore String refreshToken,
    @JsonIgnore Instant refreshExpiresAt) {

  public LoginResponse withoutRefresh() {
    return new LoginResponse(
        token, tokenExpiresAt, username, role, organizationId, organizationName, null, null);
  }
}
