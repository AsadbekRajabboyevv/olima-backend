package com.olima.tool;

import com.olima.tool.dto.ToolRequest;
import com.olima.tool.dto.ToolResponse;
import com.olima.tool.enums.ToolType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

public interface ToolApi {

  @GetMapping("/api/v1/tools/types")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  ResponseEntity<List<ToolType>> supportedTypes();

  @GetMapping("/api/v1/organizations/{organizationId}/tools")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<List<ToolResponse>> findByOrganization(@PathVariable UUID organizationId);

  @GetMapping(value = "/api/v1/tools", params = "organizationId")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<List<ToolResponse>> findByOrganizationParam(@RequestParam UUID organizationId);

  @PostMapping("/api/v1/organizations/{organizationId}/tools")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<ToolResponse> create(
      @PathVariable UUID organizationId, @RequestBody @Valid ToolRequest request);

  @PostMapping(value = "/api/v1/tools", params = "organizationId")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<ToolResponse> createWithParam(
      @RequestParam UUID organizationId, @RequestBody @Valid ToolRequest request);

  @GetMapping("/api/v1/tools/{id}")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  ResponseEntity<ToolResponse> findById(@PathVariable UUID id);

  @PutMapping("/api/v1/tools/{id}")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  ResponseEntity<ToolResponse> update(
      @PathVariable UUID id, @RequestBody @Valid ToolRequest request);

  @DeleteMapping("/api/v1/tools/{id}")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  ResponseEntity<Void> delete(@PathVariable UUID id);

  @PatchMapping("/api/v1/tools/{id}/toggle")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  ResponseEntity<ToolResponse> toggleEnabled(
      @PathVariable UUID id,
      @RequestParam(required = false) Boolean enabled,
      @RequestBody(required = false) Map<String, Object> body);
}
