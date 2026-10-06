package com.olima.security.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olima.security.model.AuthenticatedUser;
import com.olima.user.enums.UserRole;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantAccessTest {

  private final UUID orgA = UUID.randomUUID();
  private final UUID orgB = UUID.randomUUID();
  private final UUID toolOfB = UUID.randomUUID();

  private final TenantAccess tenant =
      new TenantAccess(
          Arrays.stream(TenantResource.values())
              .map(
                  r ->
                      TenantResourceResolver.of(
                          r, id -> Optional.ofNullable(Map.of(toolOfB, orgB).get(id))))
              .toList());

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private void login(UserRole role, UUID orgId) {
    var user = new AuthenticatedUser(UUID.randomUUID(), "u", role, orgId);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
  }

  @Test
  void orgAdminSeesOnlyOwnOrganization() {
    login(UserRole.ORG_ADMIN, orgA);
    assertThat(tenant.canAccessOrganization(orgA)).isTrue();
    assertThat(tenant.canAccessOrganization(orgB)).isFalse();
    assertThat(tenant.canAccessOrganization(null)).isFalse();
  }

  @Test
  void orgAdminCannotReachResourceOfAnotherOrganization() {
    login(UserRole.ORG_ADMIN, orgA);
    assertThat(tenant.canAccess("TOOL", toolOfB)).isFalse();

    assertThat(tenant.canAccess("TOOL", UUID.randomUUID())).isFalse();
  }

  @Test
  void ownerCanReachOwnResource() {
    login(UserRole.ORG_ADMIN, orgB);
    assertThat(tenant.canAccess("TOOL", toolOfB)).isTrue();
  }

  @Test
  void superAdminSeesEverything() {
    login(UserRole.SUPER_ADMIN, null);
    assertThat(tenant.canAccessOrganization(orgB)).isTrue();
    assertThat(tenant.canAccess("DOCUMENT", UUID.randomUUID())).isTrue();
  }

  @Test
  void widgetPrincipalHasNoPanelAccess() {
    login(UserRole.WIDGET, orgA);
    assertThat(tenant.canAccessOrganization(orgA)).isFalse();
    assertThat(tenant.canAccess("TOOL", toolOfB)).isFalse();
  }

  @Test
  void anonymousHasNoAccess() {
    assertThat(tenant.canAccessOrganization(orgA)).isFalse();
  }

  @Test
  void effectiveOrganizationForcesOwnOrgForOrgAdmin() {
    login(UserRole.ORG_ADMIN, orgA);
    assertThat(tenant.effectiveOrganization(null, true)).isEqualTo(orgA);
    assertThat(tenant.effectiveOrganization(orgA, true)).isEqualTo(orgA);
    assertThatThrownBy(() -> tenant.effectiveOrganization(orgB, true))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void superAdminMustNameOrganizationWhenRequired() {
    login(UserRole.SUPER_ADMIN, null);
    assertThat(tenant.effectiveOrganization(null, false)).isNull();
    assertThatThrownBy(() -> tenant.effectiveOrganization(null, true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyResourceTypeNeedsAResolver() {
    assertThatThrownBy(
            () ->
                new TenantAccess(
                    List.of(
                        TenantResourceResolver.of(TenantResource.TOOL, id -> Optional.empty()))))
        .isInstanceOf(IllegalStateException.class);
  }
}
