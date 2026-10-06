package com.olima.complaint.event;

import java.util.UUID;

public record ComplaintConfirmedEvent(
    UUID complaintId, UUID organizationId, String subject, String description, String category) {}
