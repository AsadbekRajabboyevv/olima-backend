package com.olima.complaint;

import com.olima.complaint.dto.ComplaintResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/complaints")
public interface ComplaintApi {

  @GetMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  ResponseEntity<List<ComplaintResponse>> findByOrganization(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @GetMapping("/{id}")
  @PreAuthorize("@tenant.canAccess('COMPLAINT', #id)")
  ResponseEntity<ComplaintResponse> findById(@PathVariable UUID id);

  @PostMapping("/{id}/confirm")
  @PreAuthorize("@tenant.canAccess('COMPLAINT', #id)")
  ResponseEntity<ComplaintResponse> confirm(@PathVariable UUID id);
}
