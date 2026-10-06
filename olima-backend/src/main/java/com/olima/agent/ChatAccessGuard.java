package com.olima.agent;

import com.olima.agent.dto.ChatChannel;
import com.olima.agent.dto.ChatRequest;
import com.olima.organization.OrganizationService;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.security.model.AuthenticatedUser;
import com.olima.usage.UsageService;
import com.olima.user.enums.UserRole;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatAccessGuard {

  private final OrganizationService organizationService;
  private final UsageService usageService;

  public OrganizationResponse authorize(
      ChatRequest request, ChatChannel channel, AuthenticatedUser principal) {
    if (request.organizationId() == null) {
      throw new IllegalArgumentException("organizationId is required");
    }
    assertOrganizationAccess(channel, principal, request.organizationId());
    OrganizationResponse org = organizationService.findById(request.organizationId());
    if (!org.enabled()) {
      throw new AccessDeniedException("Organization is disabled");
    }
    usageService.checkQuota(org.id(), org.monthlyTokenQuota());
    return org;
  }

  private void assertOrganizationAccess(
      ChatChannel channel, AuthenticatedUser principal, UUID organizationId) {
    if (channel != ChatChannel.PANEL) {
      return;
    }
    if (principal == null) {
      throw new AccessDeniedException("Authentication is required");
    }
    if (principal.role() == UserRole.ORG_ADMIN
        && !organizationId.equals(principal.organizationId())) {
      throw new AccessDeniedException("You do not have access to this organization's data");
    }
  }
}
