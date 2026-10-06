package com.olima.complaint;

import com.olima.common.web.Paging;
import com.olima.complaint.dto.ComplaintResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintController implements ComplaintApi {

  private final ComplaintService complaintService;
  private final Paging paging;

  @Override
  @GetMapping
  @PreAuthorize("@tenant.canAccessOrganization(#organizationId)")
  public ResponseEntity<List<ComplaintResponse>> findByOrganization(
      @RequestParam UUID organizationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return Paging.ok(
        complaintService.findByOrganization(
            organizationId, paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
  }

  @Override
  @GetMapping("/{id}")
  @PreAuthorize("@tenant.canAccess('COMPLAINT', #id)")
  public ResponseEntity<ComplaintResponse> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(complaintService.getComplaint(id));
  }

  @Override
  @PostMapping("/{id}/confirm")
  @PreAuthorize("@tenant.canAccess('COMPLAINT', #id)")
  public ResponseEntity<ComplaintResponse> confirm(@PathVariable UUID id) {
    return ResponseEntity.ok(complaintService.confirmComplaint(id));
  }
}
