package com.olima.complaint;

import com.olima.complaint.dto.ComplaintResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ComplaintService {

  ComplaintResponse createDraft(
      UUID orgId, UUID convId, String subject, String description, String category);

  ComplaintResponse confirmComplaint(UUID complaintId);

  void markSubmitted(UUID complaintId);

  Page<ComplaintResponse> findByOrganization(UUID orgId, Pageable pageable);

  ComplaintResponse getComplaint(UUID id);
}
