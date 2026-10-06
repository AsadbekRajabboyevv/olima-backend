package com.olima.organization.resolver;

import com.olima.security.filter.WidgetKeyFilter;
import com.olima.security.model.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class WidgetContextResolver {

  public Optional<UUID> resolveOrganizationId(HttpServletRequest request) {
    UUID orgId = (UUID) request.getAttribute(WidgetKeyFilter.WIDGET_ORG_ATTR);
    if (orgId != null) {
      return Optional.of(orgId);
    }
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
      return Optional.ofNullable(user.organizationId());
    }
    return Optional.empty();
  }
}
