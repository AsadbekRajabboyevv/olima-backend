package com.olima.organization;

import com.olima.organization.dto.AssistantSettingsRequest;
import com.olima.organization.dto.AssistantSettingsResponse;
import com.olima.organization.dto.OrganizationRequest;
import com.olima.organization.dto.OrganizationResponse;
import com.olima.organization.dto.WidgetSettingsRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/organizations")
public interface OrganizationApi {

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  ResponseEntity<List<OrganizationResponse>> findAll();

  @GetMapping("/{id}")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  ResponseEntity<OrganizationResponse> findById(@PathVariable UUID id);

  @PostMapping
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  ResponseEntity<OrganizationResponse> create(@RequestBody @Valid OrganizationRequest request);

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  ResponseEntity<OrganizationResponse> update(
      @PathVariable UUID id, @RequestBody @Valid OrganizationRequest request);

  @PutMapping("/{id}/widget")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  ResponseEntity<OrganizationResponse> updateWidget(
      @PathVariable UUID id, @RequestBody @Valid WidgetSettingsRequest request);

  @PostMapping("/{id}/widget-key/rotate")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  ResponseEntity<OrganizationResponse> rotateWidgetKey(@PathVariable UUID id);

  @GetMapping("/{id}/assistant")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  ResponseEntity<AssistantSettingsResponse> assistantSettings(@PathVariable UUID id);

  @PutMapping("/{id}/assistant")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  ResponseEntity<AssistantSettingsResponse> updateAssistantSettings(
      @PathVariable UUID id, @RequestBody @Valid AssistantSettingsRequest request);

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  ResponseEntity<Void> delete(@PathVariable UUID id);
}
