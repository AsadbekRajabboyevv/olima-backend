package com.olima.agent.dto;

import java.util.UUID;

public record ConfirmActionRequest(
    UUID conversationId, UUID complaintId, String conversationToken) {}
