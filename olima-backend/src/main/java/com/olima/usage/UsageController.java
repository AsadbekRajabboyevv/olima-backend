package com.olima.usage;

import com.olima.usage.dto.UsageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UsageController implements UsageApi {

  private final UsageService usageService;

  @Override
  @GetMapping("/api/v1/organizations/{id}/usage")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  public ResponseEntity<UsageResponse> usage(@PathVariable UUID id) {
    return ResponseEntity.ok(usageService.getOrganizationUsage(id));
  }
}
