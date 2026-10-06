package com.olima.security.tenant;

import com.olima.security.model.AuthenticatedUser;
import com.olima.user.enums.UserRole;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component("tenant")
public class TenantAccess {

  private final Map<TenantResource, TenantResourceResolver> resolvers =
      new EnumMap<>(TenantResource.class);

  public TenantAccess(List<TenantResourceResolver> resolvers) {
    for (TenantResourceResolver resolver : resolvers) {
      if (this.resolvers.put(resolver.resource(), resolver) != null) {
        throw new IllegalStateException("Duplicate tenant resolver for " + resolver.resource());
      }
    }
    for (TenantResource resource : TenantResource.values()) {
      if (!this.resolvers.containsKey(resource)) {
        throw new IllegalStateException("No tenant resolver registered for " + resource);
      }
    }
  }

  public boolean isSuperAdmin() {
    return CurrentUser.get().map(AuthenticatedUser::isSuperAdmin).orElse(false);
  }

  public boolean canAccessOrganization(UUID organizationId) {
    AuthenticatedUser user = CurrentUser.get().orElse(null);
    if (user == null) {
      return false;
    }
    if (user.isSuperAdmin()) {
      return true;
    }
    return user.role() == UserRole.ORG_ADMIN
        && organizationId != null
        && organizationId.equals(user.organizationId());
  }

  public boolean canAccess(String resource, UUID resourceId) {
    AuthenticatedUser user = CurrentUser.get().orElse(null);
    if (user == null || resourceId == null) {
      return false;
    }
    if (user.isSuperAdmin()) {
      return true;
    }
    TenantResourceResolver resolver = resolvers.get(TenantResource.valueOf(resource));
    return resolver
        .organizationOf(resourceId)
        .map(orgId -> canAccessOrganization(orgId))
        .orElse(false);
  }

  public UUID effectiveOrganization(UUID requested, boolean required) {
    AuthenticatedUser user = CurrentUser.require();
    if (user.isSuperAdmin()) {
      if (required && requested == null) {
        throw new IllegalArgumentException("organizationId is required");
      }
      return requested;
    }
    if (user.role() != UserRole.ORG_ADMIN || user.organizationId() == null) {
      throw new AccessDeniedException("You do not have access to this organization's data");
    }
    if (requested != null && !requested.equals(user.organizationId())) {
      throw new AccessDeniedException("You do not have access to this organization's data");
    }
    return user.organizationId();
  }
}
