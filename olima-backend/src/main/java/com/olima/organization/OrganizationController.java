package com.olima.organization;

import com.olima.organization.dto.AssistantSettingsRequest;
import com.olima.organization.dto.AssistantSettingsResponse;
import com.olima.organization.dto.OrganizationRequest;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.organization.dto.WidgetSettingsRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController implements OrganizationApi {

  private final OrganizationService organizationService;

  @Override
  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  public ResponseEntity<List<OrganizationResponse>> findAll() {
    return ResponseEntity.ok(organizationService.findAccessibleOrganizations());
  }

  @Override
  @GetMapping("/{id}")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  public ResponseEntity<OrganizationResponse> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(organizationService.findById(id));
  }

  @Override
  @PostMapping
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<OrganizationResponse> create(
      @RequestBody @Valid OrganizationRequest request) {
    return ResponseEntity.ok(organizationService.create(request));
  }

  @Override
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<OrganizationResponse> update(
      @PathVariable UUID id, @RequestBody @Valid OrganizationRequest request) {
    return ResponseEntity.ok(organizationService.update(id, request));
  }

  @Override
  @PutMapping("/{id}/widget")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  public ResponseEntity<OrganizationResponse> updateWidget(
      @PathVariable UUID id, @RequestBody @Valid WidgetSettingsRequest request) {
    return ResponseEntity.ok(organizationService.updateWidgetSettings(id, request));
  }

  @Override
  @PostMapping("/{id}/widget-key/rotate")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  public ResponseEntity<OrganizationResponse> rotateWidgetKey(@PathVariable UUID id) {
    return ResponseEntity.ok(organizationService.rotateWidgetKey(id));
  }

  @Override
  @GetMapping("/{id}/assistant")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  public ResponseEntity<AssistantSettingsResponse> assistantSettings(@PathVariable UUID id) {
    return ResponseEntity.ok(organizationService.assistantSettings(id));
  }

  @Override
  @PutMapping("/{id}/assistant")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  public ResponseEntity<AssistantSettingsResponse> updateAssistantSettings(
      @PathVariable UUID id, @RequestBody @Valid AssistantSettingsRequest request) {
    return ResponseEntity.ok(organizationService.updateAssistantSettings(id, request));
  }

  @Override
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    organizationService.delete(id);
    return ResponseEntity.ok().build();
  }
}
