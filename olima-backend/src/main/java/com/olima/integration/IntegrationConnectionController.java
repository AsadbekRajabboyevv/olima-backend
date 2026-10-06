package com.olima.integration;

import com.olima.integration.dto.ConnectionRequest;
import com.olima.integration.dto.ConnectionResponse;
import com.olima.integration.dto.ConnectionTestResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IntegrationConnectionController implements IntegrationConnectionApi {

  private final IntegrationConnectionService service;

  @Override
  @GetMapping("/api/v1/organizations/{organizationId}/connections")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<List<ConnectionResponse>> findByOrganization(
      @PathVariable UUID organizationId) {
    return ResponseEntity.ok(service.findByOrganization(organizationId));
  }

  @Override
  @PostMapping("/api/v1/organizations/{organizationId}/connections")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<ConnectionResponse> create(
      @PathVariable UUID organizationId, @RequestBody @Valid ConnectionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(organizationId, request));
  }

  @Override
  @GetMapping("/api/v1/connections/{id}")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  public ResponseEntity<ConnectionResponse> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(service.findById(id));
  }

  @Override
  @PutMapping("/api/v1/connections/{id}")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  public ResponseEntity<ConnectionResponse> update(
      @PathVariable UUID id, @RequestBody @Valid ConnectionRequest request) {
    return ResponseEntity.ok(service.update(id, request));
  }

  @Override
  @DeleteMapping("/api/v1/connections/{id}")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PostMapping("/api/v1/connections/{id}/test")
  @PreAuthorize("@tenant.canAccess('CONNECTION', #id)")
  public ResponseEntity<ConnectionTestResponse> test(@PathVariable UUID id) {
    return ResponseEntity.ok(service.test(id));
  }
}
