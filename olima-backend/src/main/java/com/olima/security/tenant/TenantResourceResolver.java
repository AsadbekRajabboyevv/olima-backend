package com.olima.security.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface TenantResourceResolver {

  TenantResource resource();

  Optional<UUID> organizationOf(UUID resourceId);

  static TenantResourceResolver of(TenantResource resource, Function<UUID, Optional<UUID>> lookup) {
    return new TenantResourceResolver() {
      @Override
      public TenantResource resource() {
        return resource;
      }

      @Override
      public Optional<UUID> organizationOf(UUID resourceId) {
        return lookup.apply(resourceId);
      }
    };
  }
}
