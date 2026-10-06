package com.olima.security.tenant;

import com.olima.security.model.AuthenticatedUser;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

  private CurrentUser() {}

  public static Optional<AuthenticatedUser> get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user
        ? Optional.of(user)
        : Optional.empty();
  }

  public static AuthenticatedUser require() {
    return get().orElseThrow(() -> new AccessDeniedException("Authentication is required"));
  }
}
