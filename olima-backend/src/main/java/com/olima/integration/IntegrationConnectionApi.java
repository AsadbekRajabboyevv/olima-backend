package com.olima.integration;

import com.olima.integration.dto.ConnectionRequest;
import com.olima.integration.dto.ConnectionResponse;
import com.olima.integration.dto.ConnectionTestResponse;
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

public interface IntegrationConnectionApi {

  @GetMapping("/api/v1/organizations/{organizationId}/connections")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<List<ConnectionResponse>> findByOrganization(@PathVariable UUID organizationId);

  @PostMapping("/api/v1/organizations/{organizationId}/connections")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<ConnectionResponse> create(
      @PathVariable UUID organizationId, @RequestBody @Valid ConnectionRequest request);

  @GetMapping("/api/v1/connections/{id}")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  ResponseEntity<ConnectionResponse> findById(@PathVariable UUID id);

  @PutMapping("/api/v1/connections/{id}")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  ResponseEntity<ConnectionResponse> update(
      @PathVariable UUID id, @RequestBody @Valid ConnectionRequest request);

  @DeleteMapping("/api/v1/connections/{id}")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  ResponseEntity<Void> delete(@PathVariable UUID id);

  @PostMapping("/api/v1/connections/{id}/test")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  ResponseEntity<ConnectionTestResponse> test(@PathVariable UUID id);
}
