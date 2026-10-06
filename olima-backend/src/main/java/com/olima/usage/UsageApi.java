package com.olima.usage;

import com.olima.usage.dto.UsageResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface UsageApi {

  @GetMapping("/api/v1/organizations/{id}/usage")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  ResponseEntity<UsageResponse> usage(@PathVariable UUID id);
}
