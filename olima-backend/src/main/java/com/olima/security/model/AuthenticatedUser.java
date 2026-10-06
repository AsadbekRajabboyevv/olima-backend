package com.olima.security.model;

import com.olima.user.enums.UserRole;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, String username, UserRole role, UUID organizationId) {

  public boolean isSuperAdmin() {
    return role == UserRole.SUPER_ADMIN;
  }
}
