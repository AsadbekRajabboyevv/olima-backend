package com.olima.tool;

import com.olima.tool.dto.ToolRequest;
import com.olima.tool.dto.ToolResponse;
import com.olima.tool.enums.ToolType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ToolController implements ToolApi {

  private final ToolService toolService;

  @Override
  @GetMapping("/api/v1/tools/types")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
  public ResponseEntity<List<ToolType>> supportedTypes() {
    return ResponseEntity.ok(toolService.getSupportedTypes());
  }

  @Override
  @GetMapping("/api/v1/organizations/{organizationId}/tools")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<List<ToolResponse>> findByOrganization(@PathVariable UUID organizationId) {
    return ResponseEntity.ok(toolService.findByOrganization(organizationId));
  }

  @Override
  @GetMapping(value = "/api/v1/tools", params = "organizationId")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<List<ToolResponse>> findByOrganizationParam(
      @RequestParam UUID organizationId) {
    return ResponseEntity.ok(toolService.findByOrganization(organizationId));
  }

  @Override
  @PostMapping("/api/v1/organizations/{organizationId}/tools")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<ToolResponse> create(
      @PathVariable UUID organizationId, @RequestBody @Valid ToolRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toolService.create(organizationId, request));
  }

  @Override
  @PostMapping(value = "/api/v1/tools", params = "organizationId")
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<ToolResponse> createWithParam(
      @RequestParam UUID organizationId, @RequestBody @Valid ToolRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toolService.create(organizationId, request));
  }

  @Override
  @GetMapping("/api/v1/tools/{id}")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  public ResponseEntity<ToolResponse> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(toolService.findById(id));
  }

  @Override
  @PutMapping("/api/v1/tools/{id}")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  public ResponseEntity<ToolResponse> update(
      @PathVariable UUID id, @RequestBody @Valid ToolRequest request) {
    return ResponseEntity.ok(toolService.update(id, request));
  }

  @Override
  @DeleteMapping("/api/v1/tools/{id}")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    toolService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PatchMapping("/api/v1/tools/{id}/toggle")
  @PreAuthorize("@tenant.canAccess('TOOL', #id)")
  public ResponseEntity<ToolResponse> toggleEnabled(
      @PathVariable UUID id,
      @RequestParam(required = false) Boolean enabled,
      @RequestBody(required = false) Map<String, Object> body) {
    return ResponseEntity.ok(toolService.toggleEnabled(id, enabled, body));
  }
}
