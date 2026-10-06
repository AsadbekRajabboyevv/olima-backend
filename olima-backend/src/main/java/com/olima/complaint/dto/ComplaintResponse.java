package com.olima.complaint.dto;

import com.olima.complaint.ComplaintEntity;
import com.olima.complaint.enums.ComplaintStatus;
import java.time.Instant;
import java.util.UUID;

public record ComplaintResponse(
    UUID id,
    UUID organizationId,
    UUID conversationId,
    String subject,
    String description,
    String category,
    ComplaintStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public static ComplaintResponse of(ComplaintEntity c) {
    return new ComplaintResponse(
        c.getId(),
        c.getOrganizationId(),
        c.getConversationId(),
        c.getSubject(),
        c.getDescription(),
        c.getCategory(),
        c.getStatus(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
